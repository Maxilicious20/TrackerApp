# Outside Days

A complete offline Android app for collecting outside time and enjoying a quiet moment with five pocket games. Kotlin, Jetpack Compose, Material 3, Room, DataStore, and original locally drawn artwork. Android 8.0 (API 26) or newer. No accounts, ads, analytics, location, cloud service, or INTERNET permission.

## Downloads

- [Installable debug APK](https://github.com/Maxilicious20/TrackerApp/raw/refs/heads/main/downloads/OutsideDays-debug.apk)
- [Complete Android Studio source ZIP](https://github.com/Maxilicious20/TrackerApp/raw/refs/heads/main/downloads/OutsideDays-source.zip)
- [SHA-256 checksums](downloads/SHA256SUMS.txt)

These are real built artifacts. The APK is a debug build for Android 8.0 or newer. If a direct download requires authentication, sign into GitHub and open the file in the `downloads` folder, then choose **Download raw file**. The source ZIP excludes generated build outputs and machine-specific SDK paths.

## Run in Android Studio

1. Open this directory as a Gradle project in Android Studio Ladybug or newer.
2. Select a **full JDK 17** as the Gradle JDK (not a JRE). Install Android SDK Platform 35 and Build Tools 34.0.0 through SDK Manager. Android Studio creates `local.properties` for your own SDK path; do not copy the cloud machine's path.
3. Sync Gradle and run the `app` configuration on an API 26+ phone or emulator.

Command line, with `JAVA_HOME` pointing at JDK 17 and `ANDROID_HOME` at your SDK:

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest  # use a disposable emulator: tests reset local app data
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. It is a development build, signed with a local debug key, not a Play Store release.

Pinned versions: AGP 8.7.3, Gradle 8.9 (wrapper SHA-256 verification), Kotlin 1.9.25, Compose compiler 1.5.15, Compose BOM 2024.12.01, Room 2.6.1, DataStore 1.1.1, Lifecycle 2.8.7, WorkManager 2.9.1. Artifact availability was checked against Google's Maven repository and Maven Central; compilation validates the Kotlin/Compose pairing. Gradle needs internet for initial dependency installation; the installed app works offline.

For the prepared Codex machine only: `source scripts/cloud-env.sh`, then use the same Gradle commands. `scripts/setup-cloud.sh` refreshes the tools and dependencies without changing application source. Use the existing checkout; each cloud task is already isolated, so no extra worktree is needed.

## What is implemented

- Outside timer with category/custom category, pause/resume, finish, optional mood and notes, manual entry, interval editing, and confirmed deletion. A single shared activity slot prevents simultaneous outside/indoor timers.
- Today/week/month/year reports, daily average, longest session in the period, session count, seven-day bar chart, browsable monthly calendar, daily filtering, gentle goals and streaks, and lifetime achievements.
- Original leaf-spirit mascot **Mori** and programmatic meadow/landscape illustrations; warm light/dark/system themes, four accents, reduced motion, game haptics, optional synthesized sounds, and introductory guidance.
- **Leaf Match:** 12/16 cards, full matching rules, moves, completion and best score.
- **Cloud Slide:** 3×3 original landscape, legal-move shuffling, move count, completion and new puzzles.
- **Firefly Garden:** 30-second active-play rounds, target tapping, pause and best scores.
- **Pocket Sudoku:** verified unique 4×4 and 9×9 puzzles, immutable clues, notes, conflicts, erase, hints and completion. One bundled puzzle per size; restart resets it.
- **Cozy Merge:** complete directional 2048 rules, once-per-move merging, random spawns, saved board/score, win and game-over detection.
- Reading, focus/study and relaxing/stretching timers with separate daily summaries, explicit activity switching and optional 4–2–6 breathing guide.
- Optional notification controls and approximate daily WorkManager reminders. Android 13+ permission is requested only when enabled. Denial does not affect tracking.
- System-picker JSON backup/import and CSV export. Strict bounded validation; import previews record counts and explicitly **replaces** sessions, games, and preferences. Reminder/notification preferences are disabled after import until re-enabled. No broad storage permission.
- Delete-all requires typing `DELETE`. Android cloud backup is disabled. Uninstalling/clearing storage loses local data unless the user has exported a backup.

## Architecture and correctness

`core/Tracking.kt` contains pure clock and reporting rules. `core/Games.kt` contains pure puzzle rules and the bounded Sudoku solver. `core/Backup.kt` defines versioned serializable models, limits, validation and CSV escaping. `data/Storage.kt` owns Room, DataStore and serialized mutations. `AppModel` performs IO on background dispatchers and exposes observable state. Compose collects lifecycle-aware state. Games share neither timer state nor timer calculations.

### Timer and reporting policy

A running interval persists its wall-clock start, monotonic `elapsedRealtime` start, and boot count. Duration uses monotonic differences, **not** incremented UI counters. Every start, pause, resume and finish is written to Room immediately through a serialized repository operation. The UI refreshes only while the app is visible. A visible-app checkpoint is saved every 30 seconds and a checkpoint is requested when backgrounding; no continuous background service is required.

Screen locks, backgrounding and process death preserve accuracy within the same device boot, even with no checkpoints. A changed boot count or decreased monotonic clock conservatively changes the session to **interrupted**, keeps only checkpointed time and displays a recovery explanation. The user can resume or finish, then edit/add missing intervals. Time while the device was off, and time after the last checkpoint before reboot, cannot be inferred reliably without a trusted external clock. They are never silently invented. A foreground service is deliberately unnecessary for this duration model. Notifications are optional UI affordances, not the source of truth, and Android may dismiss them.

A wall-clock change during an interval does not alter its elapsed duration. The interval's reporting end is its original wall start plus monotonic duration. Large detected changes leave a visible notice. If the clock moves backward while paused, the resumed interval starts no earlier than the previous interval's end to preserve all active time; its notice asks the user to review dates. These synthetic reporting dates can be corrected in Journal.

The reporting timezone is fixed to the device timezone on first launch and saved in preferences/backups. Travel therefore never reassigns past sessions to new days. Reports union overlapping intervals **within the selected activity**, then split at that timezone's real day/week/month/year boundaries, including daylight saving transitions. Indoor activities and outside statistics remain separate. Paused gaps contribute zero. A period's daily average includes every elapsed calendar day in that period, including zero days; its longest-session metric is clipped to that period. Streaks count days with any outside time and tolerate an unfinished today.

### Persistence and backups

Room stores versioned JSON payloads in keyed session/game tables; each timer mutation is serialized with a mutex and each replacement of sessions/games is one database transaction. DataStore persists typed preference models. Backup v1 includes completed sessions, every saved game, and preferences. Finish the active activity before a full JSON export/import; CSV exports completed sessions independently. JSON imports are limited to 5 MB and 10,000 sessions, enforce field bounds and puzzle invariants, and validate before writes. Session/game replacement is transactional; preferences live in a separate store, with compensation if the database transaction fails. A device crash between these two stores may leave imported preferences with older sessions; records themselves remain transactionally consistent.

Games persist every move. Leaving a game or backgrounding pauses play; firefly countdown saves at one-second checkpoints and can recover with up to one second of round time after abrupt process death. Activity tracking is unaffected. Sudoku uses a bounded search to reject invalid/ambiguous backup puzzles safely. CSV quotes embedded separators/newlines and neutralizes common spreadsheet formula prefixes.

## Verification and limitations

See [docs/VERIFICATION.md](docs/VERIFICATION.md) for exact executed checks and outcomes, and [docs/QA.md](docs/QA.md) for the device checklist. This app does not claim measured frame-rate, battery, OEM-background behavior, or exhaustive accessibility coverage. Reminders are approximate and may be delayed by Android, disabled notifications or force-stop. Reopening refreshes scheduling after travel. A force-stop cancels notification availability until reopening; monotonic timer restoration still works within the same boot.

The project contains complete source files and a real Gradle wrapper; no remote imagery, hidden API keys, generated-code placeholders or unfinished feature stubs are needed to run it.
