# Verification — 2026-10-10

## Executed checks

- `bash scripts/setup-cloud.sh`: succeeded in the actual cloud machine, including dependency refresh, APK assembly, unit tests and lint. Downloaded Gradle/JDK/SDK tools were verified against authoritative checksums. Java uses the host's trusted proxy CA; TLS verification was not disabled.
- `./gradlew assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest`: successful build on full Temurin JDK 17.0.16+8, Gradle 8.9, SDK 35, and Build Tools 34.0.0. The Kotlin/Compose compiler version pairing compiled successfully.
- **28 JVM tests passed; 0 failed, 0 errors, 0 skipped.** Seven timer tests, six reporting tests, eight game-rule tests, five backup tests, and two Robolectric persistence tests. Both persistence tests actually executed against Room; this was not a zero-test result.
- **3 Android instrumentation tests passed; 0 failed, 0 skipped**, on an API 28 x86_64 software emulator: outside start/pause/navigation/resume/rotation/finish/save; opening, playing and pausing all five games; and Leaf Match state across Activity recreation. Tests wait for asynchronous persisted state and reset the disposable debug app between cases.
- **Android lint: no errors.** One advisory warning recommends KSP instead of kapt. This project deliberately uses the compiled and verified Room/kapt configuration; the warning is not suppressed.
- APK installed with `adb install -r`. The first-launch introduction and home screen were visually inspected. The emulator experienced a System UI ANR during its slow initial boot, before the successful UI test run. This is not a physical-device performance measurement.
- `apksigner verify --verbose`: the debug APK's v2 signature verifies. `aapt dump permissions`: POST_NOTIFICATIONS, WAKE_LOCK, RECEIVE_BOOT_COMPLETED, and AndroidX's app-scoped receiver signature permission. No INTERNET, location, camera, broad storage, ACCESS_NETWORK_STATE, or FOREGROUND_SERVICE permission in the delivered APK. WorkManager supplies wake/boot permissions for local reminders.

## What the tests establish

Timer tests cover multiple running intervals, excluded pauses, serialization/restoration without UI ticks, backward/forward wall-clock effects, reboot checkpoints, paused reboot restoration and idempotent actions. Reporting tests cover midnight, configurable week boundaries, month/year boundaries, overlapping intervals, paused gaps, daylight saving, fixed historical timezone and gentle streak semantics.

Game tests verify 100 seeded solvable, non-complete sliding boards; legal slide movement and completion; uniqueness and validity of both bundled Sudoku puzzles; immutable clues, notes, conflicts, hints and completion; merge-once rules, spawn behavior, legal moves, game over and 2048 completion; complete Leaf Match rules; and Firefly round completion/best-score persistence.

Persistence tests close/reopen Room with a paused timer and saved game, test competing starts, persist DataStore preferences, reject an invalid import without replacing records, and exercise a valid replacement. Backup tests round-trip all five game states and preferences, reject unsupported/malformed/oversized/duplicate/invalid records, and verify CSV escaping and paused-time exclusion.

## Remaining verification limits

- No physical phone, Android 13+ notification-permission interaction, OEM background policies, battery benchmarks, frame-rate measurements, or exhaustive TalkBack/large-font/tablet testing.
- Actual device reboot and manual clock/timezone changes were not end-to-end automated; their deterministic calculations are unit-tested. Device checklist: [QA.md](QA.md).
- SAF document-provider cancellation/errors, local reminders firing while idle, and every notification action were not all exercised through device UI. Backup validation/replacement logic and underlying persistence were tested.
- Sudoku intentionally bundles one uniquely solved puzzle per size. Firefly game time checkpoints once per second; abrupt process death may restore up to one extra second of game time. This does not affect activity timer accuracy.
- A reboot cannot recover an unobserved interval reliably; the app conservatively flags interrupted tracking for review. Full details, reporting policy and backup atomicity limits are in [README.md](../README.md).

Cloud installation and startup instructions were saved as a configuration draft. Saving does not publish the environment or demonstrate restoration in a new cloud task. The source project and debug APK are supplied in this GitHub repository. Nothing has been published to an app store.
