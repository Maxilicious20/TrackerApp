# Device QA checklist

Automated checks cover deterministic logic and a selected UI flow; these scenarios are useful for broader release testing on physical Android devices.

- Start outside, pause, lock/unlock, resume, play a game, rotate, background, kill the process, and reopen. Compare elapsed time to an independent stopwatch; paused time must remain excluded.
- Reboot while running: the app must show interrupted state and the conservative checkpoint notice. Review/adjust missing intervals. Reboot while paused: elapsed time must not change.
- Change the wall clock forward/backward while running and paused. Verify durations and visible notices; inspect synthetic dates in Journal. Travel to another timezone: reporting dates must remain in the saved zone.
- Add sessions crossing midnight, Monday/Sunday week starts, month/year boundaries and daylight saving transitions. Add overlapping records and confirm totals count their union.
- Finish and save optional notes/mood; edit multiple intervals without filling paused gaps; cancel and confirm deletions. Try invalid dates, future manual times, and nonexistent local DST times.
- Exercise both Leaf Match difficulties, legal/illegal slides, a full Firefly round, both Sudoku sizes with notes/conflicts/hints, and Merge win/game-over rules. Pause, leave, rotate and relaunch each game.
- Verify indoor summaries remain separate and switching requires confirmation. Check breathing guide stops animating while backgrounded.
- Deny notification permission; track normally. Grant later; test pause/resume/finish actions and approximate reminders. Revoke in Android settings and confirm graceful behavior.
- Export JSON and CSV through DocumentsUI. Import a valid backup, cancel replacement, then confirm. Attempt malformed, oversized, duplicate-ID, unsupported-version and invalid-puzzle backups; existing records must remain intact.
- Delete-all: incorrect confirmation must not enable deletion. Confirm that timers, games, preferences and reminders reset.
- Test 320 dp compact widths, tablets, portrait/landscape, 200% font scaling, TalkBack traversal, dark theme, every accent, reduced motion and muted/unavailable audio. Sudoku maintains 48 dp cells by scrolling horizontally on small screens.
- Uninstall/restore manually from an exported backup. Explain that uninstalling without a backup loses local data.
