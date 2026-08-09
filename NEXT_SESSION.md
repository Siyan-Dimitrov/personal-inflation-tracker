# Next session plan

## 1. Diagnose the +27.7% July→August index jump (data, not display)

The overview maths and display are verified correct; the fixed-basket index
genuinely rises ~112 → ~143. That size of move means one or two products are
distorted. Steps:

1. Overview → "Why did it change?" — note the top category and product
   contributions (screenshot was cut off last time).
2. Open the top product in the Basket and inspect its observation history and
   pack sizes. Likely causes, in order:
   - pack-size mismatch (e.g. a 2L pack confirmed onto a product whose base
     price was per 1L, doubling the unit price),
   - a wrong product match confirmed during review,
   - cross-shop averaging (a much pricier shop joined the product's mean).
3. Fix by correcting the product's pack size or deleting the bad observation;
   the index recalculates immediately.

## 2. Random crashes on the phone (unresolved — needs device logs)

Not reproducible on the emulator: all screens exercised plus 2,100 random
monkey events with zero crashes/ANRs on the current build. Requires the real
device:

1. Phone: Settings → About phone → tap "Build number" 7× → Developer options →
   enable USB debugging; plug into the PC.
2. Pull stored traces of past crashes (no repro needed):
   `adb logcat -d -b crash` and `adb shell dumpsys dropbox --print data_app_crash`.
3. Fix whatever the stack trace names.

## 3. Next feature, once stable: per-shop inflation

Agreed design: an Overview section with one card per merchant (its own index
and rate, reusing the chained-index calculator filtered to that merchant's
observations), tappable to filter the chart; show "not enough data" below a
small threshold instead of a noisy number.

## Backlog / smaller items

- Custom emoji per product in the product editor (override the automatic one).
- Consider showing the receipt-total on basket product rows.
- Rotate the Gemini API key periodically (Google AI Studio → key lives only in
  `local.properties`, never committed).

## State notes

- All work through 2026-08-09 is committed and pushed; APK with the Gemini key
  baked in: `app/build/outputs/apk/debug/pocket-index-0.1.0-debug.apk`.
- Emulator QA loop that works: boot `CanvasCurio_Pixel_7_Pro`, `adb install -r`,
  drive via `adb shell input tap` + `screencap`; `pm clear` re-seeds demo data.
