# Pocket Index

Pocket Index is a private, local-first Android app that calculates personal
inflation from receipt and recurring-bill history.

The app is designed around a simple loop:

1. Scan or import a receipt.
2. Review the locally extracted line items.
3. Confirm canonical products, quantities, and pack sizes.
4. Track comparable unit prices and personal inflation over time.

All data, receipt images, OCR, and calculations stay on the device. The app
creates no accounts and includes no analytics. Network access exists for one
optional, build-time opt-in feature — AI product-match suggestions — described
below; a build without an API key never makes a network request.

![Pocket Index Pixel 7 Pro design](design/pocket-index-pixel7pro-mockup.png)

## Implemented

- Material 3 Compose shell based on the first Pixel 7 Pro design direction
- Room-backed overview with fixed/chained series, headline-rate states,
  contribution explanations, fresh coverage, and stale-product detail
- Index chart present in every state: an empty axis at zero until a second
  monthly point exists, and one point per calendar month thereafter
- Receipt inbox and selected receipt detail with image/OCR evidence, retry,
  history, and unique durable WorkManager extraction
- Full receipt correction: catalogue bootstrap, merchant/date/totals/line
  editing, exclusions, manual lines/receipts/observations, 2p reconciliation,
  and alias learning
- Searchable real product catalogue with freshness/fixed-basket filters,
  merchant prices, unit-price and pack-size charts, pack changes, editing, and
  transactional duplicate merging
- Add and edit recurring bills, including cadence; soft removal keeps price history
- Google ML Kit document scanning and bundled on-device OCR
- Deterministic UK receipt parsing, pack-size extraction, fuzzy matching, and
  receipt-total validation
- Room schema and transactional repositories for receipts, products,
  observations, aliases, and recurring bills
- Fixed-basket Laspeyres calculations, per-merchant carry-forward, staleness,
  coverage, category weights, chain linking, and headline-rate estimates
- Configurable base window, category-weight overrides, and in-app methodology
- Vico charts, Hilt dependency injection, coroutines, and Flow
- Debug-only sample data for manual QA, never restored once the user has reset
- Reset that returns the app to a first-launch state — database, private
  receipt images, cached scans, and settings — serialised against in-flight
  receipt capture and extraction so nothing survives the wipe

Optional privacy locking/data-retention controls and local reminders are
deliberately deferred.

See [personal_inflation_tracker.md](personal_inflation_tracker.md) for the
complete product and calculation specification.

## Build and test

The project targets API 36, supports API 23+, and uses JDK 17 bytecode.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to
`app/build/outputs/apk/debug/pocket-index-0.1.0-debug.apk`.

## Optional: AI product-match suggestions (bring your own key)

Receipt lines the deterministic matcher cannot resolve (for example
`AB ROOSTER POTS 2KG` → Potatoes) can be matched by the Gemini API free tier.
The feature is off unless you provide your own key at build time:

1. Get a free API key from [Google AI Studio](https://aistudio.google.com/apikey).
2. Add it to `local.properties` (which is gitignored — never commit a key):

   ```properties
   gemini.apiKey=YOUR_KEY_HERE
   ```

3. Rebuild. Suggestions appear in the receipt review screen for confirmation;
   nothing enters the index without your approval.

What leaves the device: only the unmatched line descriptions and your product
catalogue names — never prices, totals, dates, or images. On Google's free
tier, submitted content may be used to improve their services. The key is
compiled into the APK, so do not share APKs built with your key. Without a
key, the app performs no network requests at all.

## Project structure

- `app/src/main/.../data` — Room entities, DAOs, and repositories
- `app/src/main/.../extraction` — OCR adapter and deterministic receipt parser
- `app/src/main/.../domain` — pure personal-inflation calculations
- `app/src/main/.../ui` — Compose screens, navigation, and view models
- `app/src/test` — parser and inflation-domain tests
- `design` — Pixel 7 Pro design references
