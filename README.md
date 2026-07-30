# Pocket Index

Pocket Index is a private, local-first Android app that calculates personal
inflation from receipt and recurring-bill history.

The app is designed around a simple loop:

1. Scan a receipt.
2. Review the extracted line items.
3. Confirm canonical products, quantities, and pack sizes.
4. Track unit prices and personal inflation over time.

All data, receipt images, OCR, and calculations stay on the device. The app
does not request network access, create accounts, or include analytics.

## Target

- Pixel 7 Pro as the primary reference device
- Kotlin and Jetpack Compose with Material 3
- Room, Hilt, coroutines, and Flow
- ML Kit Document Scanner and on-device text recognition
- Vico charts

See [personal_inflation_tracker.md](personal_inflation_tracker.md) for the
complete product and calculation specification.

## Status

Active implementation. The product design reference is under `design/`.
