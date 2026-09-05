# Validation — September 5, 2026

Build: PhoneMood 1.0.0 debug, Kotlin/Compose, minSdk 29, target/compile SDK 35.
Environment: local macOS build with Gradle 8.13 and the IntelliJ bundled JDK; Pixel 6 profile Android 15/API 35 ARM64 emulator.

## Automated checks

- `:app:assembleDebug`: passed; installable debug APK generated.
- `:app:testDebugUnitTest`: 16 tests passed, zero failures/errors.
- `:app:connectedDebugAndroidTest`: 5 tests passed, zero failures/errors.
- `:app:lintDebug`: zero errors. Nonfatal notices concern newer dependency/SDK versions and Kotlin URI convenience APIs.
- `apksigner verify`: debug APK signature verified.
- APK manifest inspected: no `INTERNET` permission. WorkManager contributes `WAKE_LOCK` and `ACCESS_NETWORK_STATE`; network access is not enabled.

Unit coverage: short lock breaks, reset-threshold breaks, app switching, exclusions, duplicate/shuffled replay, missed polling boundaries, stable checkpoint IDs, pause, shutdown, configuration changes, midnight clipping, and DST day lengths.

Instrumented coverage: Room checkpoint/raw-event uniqueness; export timeline/summary totals and schema shape; repeated-export idempotency; first-response-wins and late-response replacement of the original day's export; monitoring gap metadata; regression protection for Settings.FallbackHome accidentally excluding Settings.

## Live emulator checks

- Fresh-install Today screen renders and opens the Android Usage Access settings page.
- Usage Access and notification permission can be granted; Start monitoring creates a `specialUse` foreground service with a persistent notification.
- Actual Settings activity is captured in a session and in the Timeline screen. An early smoke test found Android's fallback-home component was over-excluding Settings; the filter was corrected to use only the selected launcher and covered by an instrumented regression test.
- Reports → Export today writes a complete JSON file in Downloads/PhoneMoodHealth.
- The downloaded actual emulator record contains 41,286 ms of Settings usage. The Python validator confirms timeline, app, and daily totals agree. The day is correctly labeled partial.
- Updating and reopening the installed APK preserves records and previously enabled monitoring.
- Pausing in Settings, force-stopping, and reopening the app preserves the paused state; no UsageMonitorService is restarted.
- No AndroidRuntime crashes were observed during these checks.

`example-emulator-report.json` is a real export from this isolated test emulator, not user data or a sample injected into the app. Screenshots show real empty/initial monitoring states, not fabricated mood history.

## Remaining device validation

A physical-device soak test is still needed for manufacturer-specific battery policies, process eviction, multi-day recovery, reboot behavior, denied/revoked permissions over time, and full-interval notification timing. API 29 and API 36 have not been run on a device in this session. The test suite verifies threshold and export logic but does not establish battery efficiency or exactly-timed notification delivery.

See README for the MediaStore atomic-publication limitation, conservative history recovery, inferred backfill timezone offsets, and full-replay scaling tradeoff. This is a debug MVP, not a signed production/Play release.
