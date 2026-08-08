# Pocket Index — Next Session

## Status after 8 August 2026

On-device testing with a real Lidl receipt is underway. Work completed this day:

- **Store-format parser hardening.** Characterisation tests for representative
  Tesco, Sainsbury's, Morrisons, and Costco UK receipts, plus four generic
  fixes: `BALANCE DUE` recognised as a total label (Sainsbury's receipts
  previously lost their total); Clubcard/Nectar/More Card price cuts treated as
  promotions; promotion lines now win over the payment-word ignore list (a
  Morrisons "More Card Price" cut was being dropped, breaking reconciliation);
  scheme savings summaries ("CLUBCARD SAVINGS", "SAVINGS WITH YOUR MORE CARD")
  ignored so discounts are not double counted. The fixtures are reconstructions
  from documented formats, not real scans — replace each with genuine OCR text
  on first real use.
- **Receipt deletion.** A receipt can be deleted from its detail screen behind a
  confirmation dialog stating the impact. Deletion removes the receipt, its
  line items, its private image, and the price observations those lines created
  (the schema otherwise orphans them via `SET NULL` and they keep feeding the
  index). It cancels the receipt's unique extraction work and runs under
  `LocalDataLock`, so it cannot interleave with a reset or the worker's locked
  write.

Known gaps from this work:

- No automated coverage of the deletion path (same pure-JVM limitation as the
  reset path).
- The real Lidl scan still fails on-device even though the same receipt's
  idealised text passes in tests — see the scanner item below.

## Ideas and next steps (8 August 2026)

1. **Scanner accuracy — highest priority, currently blocked on evidence.** The
   parser handles the Lidl receipt's clean text, so the failure is in real ML
   Kit OCR output, most likely right-column price fragments failing the
   `mergeSameRowFragments` row-overlap test on crumpled paper. Get the stored
   `raw_text` from the device (`adb` with the phone plugged in, app id
   `com.siyandimitrov.pocketindex`, database `pocket-index.db`) or a screenshot
   of the receipt detail's "OCR evidence" section, turn it into a fixture, then
   fix the geometry or ignore rules generically. Do not reach for an on-device
   LLM first: Gemini Nano is unavailable on the Pixel 7 (AICore needs Pixel
   8+), and a bundled small model (MediaPipe/Gemma-class, ~1 GB APK) would sit
   on the same garbled OCR text. Reconsider only if real OCR text proves
   fundamentally unusable.
2. **Backup: user-triggered export/import.** Currently there is no backup of
   any kind — backup rules exclude everything and the app has no network
   permission, so a lost phone loses the history, and a future switch to
   release signing forces an uninstall. Add "Back up to file" / restore in
   settings via the system file picker (works without network permission),
   zipping the database and receipt images. Companion tweak: include the
   database in the `device-transfer` rules for phone-to-phone migration. A
   cloud backend stays out of scope. Note: the database has no migration setup
   (version 1, no `fallbackToDestructiveMigration`) — add real migrations
   before the first schema change ships to testers.
3. **Read the printed purchase date.** `purchasedAt` defaults to the scan day;
   the printed receipt date (e.g. Lidl's `Date: 03/08/26`) is only used to
   ignore lines. Extract it and prefill review, so late-scanned receipts land
   in the right month without manual correction.
4. **Validate store fixtures against real receipts.** On the first real
   Tesco/Sainsbury's/Morrisons/Costco scan, replace the reconstructed fixture
   with genuine OCR text. Known deliberate gaps, unfixed until then: weighted
   produce ("0.912 kg @ £0.90/kg") parses with a junk description and review
   flag; Costco leading item codes stay in descriptions and weaken matching;
   Costco TPD/IRC markdown codes are unrecognised (their negative amounts are
   still excluded by default). If Costco becomes a real shop, prices are
   ex-VAT — that needs a per-merchant "prices exclude VAT" flag so its
   observations stay comparable, not a receipt-type system.

## Status after 31 July 2026

Features 1–5 in this handoff are implemented and validated:

- Complete receipt correction and catalogue bootstrap.
- Live Room-backed personal-inflation dashboard.
- Receipt inbox, history, selected-receipt detail, retry, and durable WorkManager extraction.
- Real searchable basket and product browser.
- Inflation contribution, coverage, substitution, weight, base-window, and methodology explanations.

Features 6 and 7 were explicitly deferred by the user. Do not begin the
privacy-lock/data-control or reminder tracks unless they are requested again.

Validation completed with a clean `testDebugUnitTest assembleDebug`, plus an
install and visual launch at Pixel 7 Pro dimensions (1080 × 2340 override,
420 dpi). The app still has no network permission, account, backend,
analytics, or telemetry.

### Follow-up work completed the same day

- **Reset to zero.** "Reset all data" wipes the database, private receipt
  images, cached scanner pages, and settings on every build type, and records a
  durable flag first so debug builds never restore the demo history afterwards,
  including when a later step fails or the process dies mid-reset.
- **Reset and capture races.** `LocalDataLock` serialises receipt capture and
  extraction against the wipe. The extraction worker re-checks that its receipt
  still exists before creating a merchant, because OCR deliberately runs outside
  the lock so a reset never waits on it.
- **Chart presence.** The fixed-basket card stays on screen in every state with
  an empty gridded axis at zero, rather than disappearing when there is no
  history and reading as a broken graph.
- **Monthly points.** A base window closing a few days before a month end no
  longer produces two points, and two identical month labels, inside one
  calendar month.

Known gaps in that work, for whoever picks it up next:

- There is no automated coverage of the reset path or the preference reset. The
  suite is pure JVM with no Robolectric, so those view models are not testable
  as they stand.
- The capture/reset race is verified by construction and code review only.
  Exercising it needs the document scanner plus a reset fired mid-scan, which
  the available emulator tooling cannot drive.
- A failed reset surfaces `error.message` verbatim, so a raw SQLite message can
  reach the user.

## Objective

Continue Pocket Index from the current tested Android foundation and turn it
into a real-data beta. Preserve the local-first design: no accounts, backend,
analytics, telemetry, or network permission.

## Selected features

### 1. Complete receipt correction and catalogue bootstrap — COMPLETE

This is the highest-priority production blocker. A release installation starts
with an empty product catalogue, so every receipt must be reviewable without
debug sample data.

Implement:

- Create a canonical product directly from an unmatched receipt line.
- Edit merchant and purchase date.
- Edit quantity, unit price, line total, subtotal, tax, and receipt total.
- Add a missing line manually.
- Exclude discounts, deposits, loyalty messages, and other non-product lines.
- Select or correct category, unit type, and pack size.
- Add a completely manual receipt or individual price observation when
  scanning fails.
- Keep `raw_text` and the original receipt image immutable.
- Require reconciliation within £0.02 before confirmation.
- Add useful empty, validation, and error states instead of sample receipt
  content.

Definition of done:

- A fresh release-style database can scan and confirm its first receipt.
- A user can recover from incomplete or inaccurate OCR without editing the
  database externally.
- Confirmed corrections continue to teach product aliases.

### 2. Live personal-inflation dashboard — COMPLETE

Replace the hard-coded headline, chart, coverage, and category drivers with
Room-backed calculations using the existing pure Kotlin domain engine.

Implement:

- A repository/domain adapter that maps persisted products and observations to
  the inflation-domain models.
- Monthly fixed-basket index series.
- Chained index series when sufficient history exists.
- Headline YoY rate or annualised early estimate.
- Category sub-indices and contribution breakdown.
- Fresh weighted coverage and stale-product counts.
- Honest onboarding states such as “building your base basket” and “not enough
  history yet.”
- Loading, empty, and calculation-error states.
- Removal of hard-coded overview metrics.

Definition of done:

- The overview changes when confirmed prices or recurring bills change.
- Displayed values can be traced to persisted observations.
- The UI clearly distinguishes early estimates from a full YoY rate.

### 3. Receipt inbox, history, and recovery — COMPLETE

Replace the single review destination and sample fallback with a complete
receipt workflow.

Implement:

- Receipt inbox grouped by processing, needs review, confirmed, and failed.
- Queue count and clear next-action labels.
- Receipt detail with original image, OCR evidence, totals, and line items.
- Continue reviewing a selected receipt rather than only the first queue item.
- Retry failed OCR or deterministic extraction.
- Inspect confirmed receipts and their resulting observations.
- Move extraction into unique WorkManager jobs so it survives navigation,
  process death, and device restarts.
- Avoid duplicate jobs and duplicate observations.

Definition of done:

- Closing the app during extraction does not lose the captured receipt.
- Failed work can be retried without rescanning.
- Every stored receipt is discoverable from the UI.

### 4. Real basket and product browser — COMPLETE

Replace the fixed Whole Milk example with persisted catalogue and observation
data.

Implement:

- Searchable product list grouped or filtered by category.
- Fresh/stale and fixed-basket eligibility indicators.
- Product detail with real unit-price observations.
- Merchant-specific carried-forward prices and current mean price.
- Unit-price and pack-size chart modes.
- Shrinkflation or pack-change events.
- Manual observation entry.
- Product editing for canonical name, category, unit type, typical pack size,
  and aliases.
- Safe product merge for duplicate canonical products.

Definition of done:

- Every confirmed product can be found and inspected.
- Charts update from real observations.
- Pack-size changes are visible separately from shelf-price changes.

### 5. Inflation explainability — COMPLETE

Make the headline understandable rather than presenting it as a black box.

Implement:

- “Why did my inflation change?” breakdown.
- Category contribution in percentage points.
- Largest positive and negative product contributors.
- Coverage explanation with the stale products causing reduced confidence.
- Fixed versus chained basket comparison.
- Category weight display and user override controls.
- Base-window configuration.
- A concise in-app methodology page.

Definition of done:

- A user can trace the headline change to categories and products.
- Weight overrides and base-window changes recalculate the displayed index.
- The UI explains substitution and why changing shops is not treated as
  deflation.

### 6. Optional privacy lock and data controls — DEFERRED BY USER

Keep this opt-in so the app remains convenient for users who already rely on
device security.

Implement:

- Biometric or device-credential app lock.
- Configurable re-lock interval.
- Receipt-image retention controls.
- Clear distinction between removing an image and removing analytical history.
- Safe deletion flows with confirmation and impact summaries.
- Android Keystore-backed protection for any locally stored encryption key.
- Privacy status page covering permissions, storage, and backup exclusions.

Definition of done:

- Enabling the lock protects app contents after the chosen timeout.
- Users can manage receipt images without accidentally corrupting the index.
- The app continues to request no network permission.

### 7. Local reminders — DEFERRED BY USER

Add only user-requested, low-noise notifications.

Implement:

- Remind the user when recurring bill prices are due for review.
- Warn when important basket coverage drops because products are stale.
- Optional reminder to scan recent receipts.
- Per-reminder controls and a global off switch.
- Request notification permission only after the user enables a reminder.
- Use unique WorkManager jobs to prevent duplicate reminders.

Definition of done:

- No notification is scheduled without explicit opt-in.
- Reminders survive app and device restarts.
- Disabling a reminder cancels its scheduled work.

## Recommended implementation order

### Milestone A — Real-data receipt loop — COMPLETE

1. Remove sample fallbacks and add release-style empty states.
2. Add inline product creation and full receipt correction.
3. Add manual receipt and observation entry.
4. Build the receipt inbox and selected-receipt navigation.
5. Move OCR/extraction into WorkManager.

### Milestone B — Real inflation experience — COMPLETE

1. Build the Room-to-domain calculation adapter.
2. Replace the overview with real metrics and series.
3. Replace the basket sample with the real catalogue browser.
4. Add contribution and coverage explanations.
5. Add settings for weights and base window.

### Milestone C — Trust and retention — DEFERRED

1. Add the optional privacy lock. **Deferred by user.**
2. Add receipt-image retention and safe deletion controls. **Deferred by user.**
3. Add opt-in local reminders. **Deferred by user.**
4. Run unit, persistence, UI, and Pixel 7 Pro emulator regression tests.
   **Completed for the implemented scope.**

## Explicitly out of scope

- CSV export, backup, and restore.
- Accounts or cloud synchronisation.
- Bank or card-provider integration.
- Telemetry, analytics, or advertising.
- Public inflation comparisons.
- Network-based AI services.
- Gemini Nano work until the deterministic real-data workflow is complete.

## Working rules for the next session

- Read this file and `personal_inflation_tracker.md` before changing code.
- Use parallel agents for bounded, non-overlapping implementation tracks.
- Preserve existing user changes and commit in meaningful milestones.
- Run focused tests after each track and the complete suite before pushing.
- Validate the final build at Pixel 7 Pro dimensions.
- Keep original receipt images and OCR `raw_text` immutable.
- Keep all index mathematics in pure Kotlin with focused unit tests.
