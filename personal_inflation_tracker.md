# Personal Inflation Tracker (Android)

## What this project is

An Android app that computes a **personal inflation rate** from the owner's actual spending. Core loop: photograph a receipt → extract line items on-device → normalise to canonical products and unit prices → accumulate price observations over time → compute a weighted personal price index and show timelines.

Everything is **local-first and on-device**. No backend, no accounts, no analytics, no network calls in v1. Original receipt images are always retained so extraction can be re-run later with better models.

## Tech stack (decided — do not substitute without asking)

- **Language/UI:** Kotlin, Jetpack Compose, Material 3
- **Persistence:** Room (SQLite), one database, exportable to CSV
- **Capture:** ML Kit Document Scanner API (`com.google.android.gms:play-services-mlkit-document-scanner`) — provides crop/deskew/enhance UI, no camera permission needed. Min SDK 21+, requires ≥1.7GB device RAM (handle `MlKitException` UNSUPPORTED gracefully).
- **OCR:** ML Kit Text Recognition v2 (on-device, Latin)
- **LLM structuring (tier 1):** ML Kit GenAI **Prompt API** (Gemini Nano via AICore, on-device, multimodal, alpha). Always call `checkStatus()`/`checkFeatureStatus()` before use; only available on supported devices (Pixel 8+, Galaxy S24+, etc.). Context is ~4K tokens — never pass the full product catalogue into a prompt.
- **Parsing fallback (tier 2):** deterministic rule-based parser (regex price columns + fuzzy match) for devices without Gemini Nano, or when Nano output fails validation.
- **Background work:** WorkManager for extraction jobs
- **Charts:** Vico (Compose-native)
- **DI:** Hilt. **Async:** coroutines + Flow. **Tests:** JUnit + Robolectric for the index math (index math must be thoroughly unit-tested — it is the point of the app).

## Architecture

MVVM with a clean separation:

```
ui/            Compose screens + ViewModels
domain/        use cases, index calculation (pure Kotlin, no Android deps)
data/          Room entities/DAOs, repositories
extraction/    ReceiptExtractor interface + implementations
```

`ReceiptExtractor` is an interface with swappable implementations:

```kotlin
interface ReceiptExtractor {
    suspend fun extract(image: Uri, ocrText: OcrResult, candidates: List<Product>): ExtractionResult
}
// Implementations: GeminiNanoExtractor, RuleBasedExtractor
// Future (do not build yet): DesktopBatchImportExtractor (Claude Code processed JSON import)
```

Extraction pipeline (runs in WorkManager):
1. Document Scanner returns cleaned JPEG → save to app-private storage, insert `receipts` row with status `PENDING`.
2. ML Kit Text Recognition → raw text lines with bounding boxes.
3. Candidate pre-filter: fuzzy-match (normalised Levenshtein / trigram) raw item strings against `products` table; take top ~10 candidates per receipt.
4. Extractor structures raw text → line items JSON `{raw_text, qty, unit_price, line_total, matched_product_id?, suggested_new_product?}` .
5. **Validation:** Σ(line totals) must equal subtotal (±0.02) and subtotal + tax = total. Failures → status `NEEDS_REVIEW`, never silently stored.
6. Review screen: user confirms/corrects matches and prices. Confirmation writes `price_observations`.

The review/correction screen is a **core feature, not polish** — data quality for the index depends on it. Every auto-match below a confidence threshold must be surfaced for one-tap confirm.

## Room schema

```sql
-- Physical evidence layer
receipts(
  id INTEGER PK,
  merchant_id FK -> merchants, purchased_at DATE, total_minor INT, subtotal_minor INT,
  tax_minor INT, currency TEXT DEFAULT 'GBP', image_path TEXT, ocr_text TEXT,
  status TEXT CHECK(status IN ('PENDING','NEEDS_REVIEW','CONFIRMED')),
  created_at TIMESTAMP
)

line_items(
  id INTEGER PK, receipt_id FK,
  raw_text TEXT,             -- exactly as printed, never mutated
  quantity REAL, unit_price_minor INT, line_total_minor INT,
  product_id FK NULL,        -- null until matched/confirmed
  match_confidence REAL, user_confirmed BOOLEAN
)

merchants(id INTEGER PK, name TEXT, aliases TEXT)  -- receipts print merchant names inconsistently too

-- Canonical layer
products(
  id INTEGER PK,
  canonical_name TEXT,       -- "Whole Milk"
  category_id FK -> categories,
  unit_type TEXT CHECK(unit_type IN ('MASS_G','VOLUME_ML','COUNT','SERVICE')),
  pack_size REAL NULL,       -- current typical pack, e.g. 1136 (ml) for 2 pints
  aliases TEXT               -- JSON array of raw strings previously matched, feeds fuzzy matcher
)

categories(id INTEGER PK, name TEXT, expenditure_weight REAL NULL)  -- weight auto-derived, user-overridable

-- Analytical layer (the index reads ONLY from this table)
price_observations(
  id INTEGER PK, product_id FK, observed_at DATE,
  unit_price_micros INT,     -- price per BASE unit: per 1g, per 1ml, per 1 count — see normalisation
  shelf_price_minor INT, pack_size REAL,
  merchant_id FK NULL,
  source TEXT CHECK(source IN ('RECEIPT','BILL','MANUAL')),
  receipt_line_item_id FK NULL
)

-- Bills & recurring costs (no receipt to photograph)
recurring_items(
  id INTEGER PK, product_id FK,   -- bills are products with unit_type='SERVICE'
  cadence TEXT CHECK(cadence IN ('WEEKLY','MONTHLY','QUARTERLY','ANNUAL')),
  current_price_minor INT, last_updated DATE, active BOOLEAN
)
```

Money is always integer minor units (pence); normalised unit prices are integer **micros** to avoid float drift at per-gram scale. Dates are ISO strings.

### Unit normalisation (shrinkflation defence)

Every observation stores `unit_price = shelf_price / pack_size` in the product's base unit (per g / per ml / per count). A 10% smaller pack at the same shelf price therefore correctly registers as ~11% unit-price inflation. Parse pack sizes from raw text where present ("2PT", "500G", "6PK"); otherwise inherit product's `pack_size` and flag for review. Services (bills) use `COUNT`-like semantics: unit price = period price.

## Index methodology

Personal inflation = **weighted average of category sub-indices**, each a Laspeyres index. This is deliberate: it mirrors ONS/CPI practice, keeps a milk price change from fighting a broadband change directly, and makes the category breakdown a first-class output.

For category c with product set P_c:

```
I_c(t) = Σ_{p∈P_c} [ q_p,base × u_p(t) ] / Σ_{p∈P_c} [ q_p,base × u_p(base) ] × 100
```

where `q_p,base` = total base-unit quantity of p purchased during the base window, and `u_p(t)` = unit price of p at time t.

Headline index: `I(t) = Σ_c w_c × I_c(t)` where `w_c` = category's share of total expenditure in the base window (user-overridable in settings).

Rules (implement exactly, unit-test each):
- **Base window:** first 8 weeks of data by default (configurable). A product enters the basket only once observed ≥2 times, or once for `BILL` source.
- **Price at time t:** most recent observation ≤ t (carry-forward). If the last observation is >90 days old for a grocery-type product, mark the product **stale** — still carried forward, but the UI shows coverage % so the user knows how much of the basket is fresh.
- **Multiple merchants:** u_p(t) = mean of each merchant's carried-forward price at t (a switch to a cheaper shop is substitution, not deflation; note in UI).
- **New products after base window:** excluded from the fixed index; included in the **chained variant**.
- **Chained variant (Phase 4b):** re-base weights every 12 months and chain-link the indices multiplicatively. Show both "fixed basket" and "chained" — the gap between them is the user's substitution behaviour, which is genuinely interesting.
- **Headline rate:** YoY change of I(t); until 12 months of data exist, show annualised change since base with an "early estimate" label.
- **Monthly resolution** for the index series; product timelines plot raw observations.

## Build phases

1. **Capture & store:** scanner → OCR → rule-based parse → review screen → Room. Plus manual/recurring bill entry (needed from day one so bill history accumulates). CSV export.
2. **Canonical matching:** products table, fuzzy pre-filter, Gemini Nano extractor behind feature check, alias learning from confirmations, unit normalisation.
3. **Timelines:** per-product price charts, category views, coverage indicators.
4. **The index:** Laspeyres per category, weighted headline, YoY rate. 4b: chained variant.

## Constraints & conventions

- **Never** add network permissions, telemetry, or cloud calls. On-device only.
- Never delete or mutate `raw_text` or original images; all corrections are additive.
- Distribution is undecided (sideload vs Play Store): keep everything Play-compliant — standard permissions only, no hardcoded personal data.
- All index math lives in `domain/` as pure Kotlin with exhaustive unit tests, including: carry-forward, stale handling, pack-size change, merchant averaging, chaining, and a golden-path fixture with hand-computed expected values.
- UK context: GBP, prices with VAT included as printed, pints/litres both appear on receipts.
