# Next session plan

## 1. Diagnose the +27.7% July→August index jump (data, not display)

The overview maths and display are verified correct (re-audited 2026-08-30:
`PersonalInflationCalculator`, `PriceHistory`, the adapter's unit-price and
quantity path, and the receipt-confirm path all do what they claim). The
fixed-basket index genuinely rises ~112 → ~143. The data lives only on the
phone (no export, no device connected on 2026-08-30), so the diagnosis has to
happen there.

**Lead found 2026-08-30:** the cut-off screenshot (`Screenshot_20260809-193252.png`,
repo root, gitignored) shows the top row of "Why did it change?" starting
"Household bill…" — the biggest contributor is a **recurring bill**, not a
grocery product. Bills have large weights (rent/energy-sized amounts, one
observation qualifies them for the basket), so a single bill edit can move the
headline by tens of percent on its own. How bills feed the index:

- Adding a bill (`recordPrice`) and editing one to a *different amount*
  (`updateRecurringBill`) both insert a BILL observation on the date entered;
  the index treats it as a per-period price with pack size 1.
- Until 2026-08-30 the cadence was **not** normalised: a bill entered as £300
  quarterly after a £100 monthly one read as +200%, and changing cadence
  alone added no observation. Both fixed now, but observations already on
  the phone keep their old pack size of 1 until the bill is edited once.
- Removing a bill only sets `active = false`; its observations keep feeding
  the index (`observeAllForInflation` has no active filter). Worth fixing
  separately.

Steps on the phone:

1. Overview → "Why did it change?" → scroll to see the Household bills row and
   the "Biggest product effects" list; the top product is very likely a bill.
2. Settings → Recurring bills → open that bill: compare its amount/cadence with
   what it was in July. Also check whether a bill was re-added under a new
   name or with the amount for a different period.
3. If it is a grocery product after all: open it in the Basket and inspect
   pack sizes per observation (review defaults the pack size to whatever
   `parseReceiptPackSize` reads from the raw line, else the product's stored
   pack size — a "4X125G" line confirmed onto a 500 g product quadruples the
   unit price) and the shop of each observation (a pricier shop joining the
   per-merchant mean).
4. Fix the bill amount/cadence (or the observation/pack size) — the index
   recalculates immediately.

## 2. Random crashes on the phone (unresolved — needs device logs)

Not reproducible on the emulator: all screens exercised plus 2,100 random
monkey events with zero crashes/ANRs on the current build. Requires the real
device:

1. Phone: Settings → About phone → tap "Build number" 7× → Developer options →
   enable USB debugging; plug into the PC.
2. Pull stored traces of past crashes (no repro needed):
   `adb logcat -d -b crash` and `adb shell dumpsys dropbox --print data_app_crash`.
3. Fix whatever the stack trace names.

## 3. Per-shop inflation — built 2026-08-30, needs a look on the phone

Implemented as agreed: `InflationDashboardCalculator` now returns
`merchants: List<MerchantIndex>` — a separate fixed basket per merchant over
the same base window and monthly dates as the headline, built from that shop's
observations only (so no cross-shop averaging). A shop needs at least
`MINIMUM_MERCHANT_PRODUCTS` (3) qualifying products in the base window,
otherwise its series is null and the card says "Not enough data" with the
count so far. The Overview shows a "By shop" section under the chart; tapping
a shop switches the chart to that shop's index (tap again to return), and the
rate shown follows the selected chart range. Unit tests cover the split, the
threshold, and the name fallback. Not yet seen on the real phone.

## 4. Better scanning: Ollama vision-model extraction — built 2026-08-30

Shipped as a "vision-first OCR" layer, one transport for both the PC and
Ollama's hosted service:

- `VisionFirstReceiptOcrService` wraps ML Kit. When Settings → Receipt
  scanning → "AI receipt reading" has a server address, it downsizes the photo
  (≤1600 px JPEG), POSTs it to `<server>/api/chat` (Bearer key if given) with
  a JSON-schema `format`, parses the receipt (`OllamaReceiptCodec.kt`), and
  **only trusts it if the signed lines reconcile with the printed totals**
  (same £0.02 arithmetic as the review screen). Otherwise, or on any error or
  timeout, ML Kit runs exactly as before.
- A trusted reading is turned back into canonical receipt text and fed to the
  unchanged `RuleBasedReceiptExtractor`, so product matching, pack sizes,
  promotions, the worker and the review screen are untouched (tests round-trip
  the Costco receipt through the rules parser).
- Settings: server address (default `https://ollama.com`, i.e. AI reading is
  on by default once a key is entered — without one nothing is sent), API
  key (cloud only, entered in the app), model (default `qwen3.5:cloud`).
  Cleared address = fully on-device. Reset restores the defaults.
- Manifest allows cleartext HTTP (the PC on the LAN is plain http).

**To use it on the phone** (not yet done):

- Ollama cloud: sign in at ollama.com → API key; enter `https://ollama.com`,
  the key, model `qwen3.5:cloud` (Free tier should cover a few receipts a
  week; Pro is $20/mo).
- Own PC: run Ollama with `OLLAMA_HOST=0.0.0.0`, allow TCP 11434 in the
  Windows firewall, enter `http://192.168.0.9:11434`, no key, model
  `huihui_ai/qwen3.5-abliterated:9b` (or a pulled official vision tag).
- Then scan a receipt and check the review screen: descriptions should be
  clean model text with prices at the end; logcat tag `VisionReceiptOcr`
  says when it fell back and why.

Original plan, kept for the reasoning:

Goal: improve receipt scanning beyond ML Kit + rules. Ollama runs vision models
(e.g. Qwen2.5-VL 7B, MiniCPM-V) on the PC, not on the phone, so the phone POSTs
the receipt photo to `http://<pc>:11434/api/chat` and gets structured JSON
(merchant, date, line items with prices) in one shot.

Why this is the right lever:

- The weak link is not OCR itself — ML Kit reads characters well. It is the
  structuring step: `RuleBasedReceiptExtractor` parses lines with rules, which
  break on varied receipt layouts, multi-line items, and discount rows. A
  vision model does OCR *and* structuring together, so it handles exactly the
  cases the rules miss.
- It preserves the app's privacy stance: the image goes only to the user's own
  PC, never to a cloud provider. Today the image never leaves the phone at all
  (only unmatched text lines go to Gemini), so this is a small, self-hosted
  relaxation rather than a change of principle.
- It costs nothing per scan and has no quota, unlike cloud vision APIs.
- The architecture already anticipates it: extraction sits behind interfaces
  and the worker already treats a network step (Gemini suggestions) as
  best-effort, so a fallback layer slots in without restructuring.

Agreed design — better-when-available layer, never a replacement:

1. New `OllamaReceiptExtractor` behind the existing `ReceiptExtractor` /
   `ReceiptOcrService` interfaces (already injected into
   `ReceiptExtractionWorker`, which already treats the Gemini step as
   best-effort).
2. Ping the server with a short timeout; if reachable, send the image and ask
   for structured JSON; validate against the receipt total (the worker already
   computes `calculatedTotal`) before trusting it — a silently wrong price
   corrupts the index, worse than a missed line.
3. If unreachable or validation fails, fall back to the current
   ML Kit + rules pipeline unchanged.
4. Settings field for the server address, alongside the existing Gemini key
   setup.

Prerequisites to confirm before building: Ollama installed and running on the
PC, which model (needs ~6–8GB+ VRAM for Qwen2.5-VL 7B; smaller models misread
prices more often), and PC address on the home network.

### Findings 2026-08-30: local test, and the "just use an API" alternative

Tested on this PC (Ollama 0.32.15, RTX 3070 8 GB, model
`huihui_ai/qwen3.5-abliterated:9b`, Q4_K_M 6.6 GB, vision-capable) with the
Costco photo `PXL_20260809_102859860.jpg` and a structured-JSON prompt:

- Quality: merchant, date, total and all 17 line prices exactly right. One
  miss: the three "IRC" instant-rebate lines (−£1, −£3, −£2) came back
  positive, so the lines sum to £155.15 against a £146.64 total — exactly the
  case the validate-against-total step catches.
- Speed: 93 s cold, 72 s warm, of which 71 s is generating ~970 output tokens
  (~14 tok/s — slow for a 3070, so the 9.7B model is probably partly on CPU).
  Asking for compact JSON and a smaller/official vision model would roughly
  halve it; still expect 30–60 s per receipt.
- Setup gaps: Ollama listens on 127.0.0.1 only — set `OLLAMA_HOST=0.0.0.0`
  (and allow port 11434 in the Windows firewall) before the phone can reach
  it at `http://192.168.0.9:11434`. The installed model is a community
  "abliterated" build; fine for OCR but prefer an official Qwen vision tag.

Cloud API alternative (asked 2026-08-30):

- Gemini API: already wired for text suggestions and its free tier accepts
  images, but sending the photo means the receipt image (merchant, card
  details, name) goes to Google — the privacy line the plan drew.
- Claude API (vision): Haiku 4.5 at $1/$5 per MTok, Sonnet 5 at $2/$10. A
  phone photo is ~1.5K input tokens plus ~0.6K output, so about $0.005
  (Haiku) to $0.01 (Sonnet) per receipt — a few pence a month — and ~5 s
  instead of ~70 s, from anywhere. Same privacy trade as Gemini (image leaves
  the phone to a vendor); Anthropic's API has a 30-day retention default and
  no training on API data by policy.

Decision to make: keep Ollama (private, free, slow, home network only) or take
a paid API (fast, anywhere, image leaves the device). Either way the app-side
design is the same `ReceiptExtractor` layer with total validation and
ML Kit fallback; only the transport differs, so the choice can be a setting
later. If an API is chosen, the natural fit is the Anthropic Java SDK
(Kotlin uses it) with a pinned model id and an app-entered key stored the way
the Gemini key is.

Trade-offs accepted: only works when the PC is on and the phone is on the same
network (Tailscale could lift that later); the image leaves the phone but only
to the user's own PC. Rejected alternative: sending the image to Gemini Flash
(already wired, works anywhere) — deliberately avoided because images should
not go to Google (see privacy note in `GeminiProductSuggestionService.kt`).

## Backlog / smaller items

- Removed recurring bills still feed the index (only `active` is flipped);
  decide whether removal should also stop carrying the price forward.
- Bill cadence is normalised since 2026-08-30: a bill observation's unit
  price is per month and its pack size is the months one payment covers
  (weekly 12/52, quarterly 3, annual 12); a cadence change now records a new
  observation. Observations saved before then still have pack size 1 — an
  old quarterly/annual bill reads at its full period amount until it is
  edited once. The Basket's pack-change note will show a cadence change as
  a "pack size" change; harmless but could be worded for bills.
- Custom emoji per product in the product editor (override the automatic one).
- Consider showing the receipt-total on basket product rows.
- Rotate the Gemini API key periodically (Google AI Studio → key lives only in
  `local.properties`, never committed).

## State notes

- All work through 2026-08-09 is committed and pushed; APK with the Gemini key
  baked in: `app/build/outputs/apk/debug/pocket-index-0.1.0-debug.apk`.
- Emulator QA loop that works: boot `CanvasCurio_Pixel_7_Pro`, `adb install -r`,
  drive via `adb shell input tap` + `screencap`; `pm clear` re-seeds demo data.
