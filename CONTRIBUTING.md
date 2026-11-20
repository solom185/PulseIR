# Contributing to PulseIR

Thank you for your interest in improving **PulseIR**!

## Ways to Contribute
1. **Add or fix IR protocols** in `app/src/main/java/com/pulseir/remote/ir/IrProtocolEncoder.kt` or `AcProtocolEngine.kt` (please include unit tests in `app/src/test/`).
2. **Expand the offline catalog:** Contribute `.ir` files upstream to [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB) (CC0-1.0) or re-run `tools/build_catalog_db.py`.
3. **Translations:** Add new `values-<lang>/strings.xml` translations.
4. **UI / UX improvements:** Keep the app **100% offline** (no `INTERNET` permission, no trackers, no bloat).

## Development Checklist
- Run `./gradlew testDebugUnitTest` and verify all tests pass.
- Run `./gradlew assembleDebug` with zero compiler warnings.
- Never add `android.permission.INTERNET` to `AndroidManifest.xml`.
