# Pocket Index — Next Session

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
