# Changelog

All notable changes to T9 APK Tools are recorded here. The project follows
[Semantic Versioning](https://semver.org/).

## [1.1.1] - 2026-11-02

Hardening pass on the new storage layer, from a second code review.

### Fixed

- Cancelling the timer while a reset is mid-flight is now ignored. Dropping the
  foreground service and wake lock between "airplane on" and "airplane off"
  could leave the phone without mobile data.
- Cold-start log and IP history loads no longer overwrite lines written while
  the database read was in flight (alarm start, reset progress).
- Clearing the log can no longer resurrect rows from writes that were already
  queued.
- The "best window" insight now uses local time and groups data into real
  three-hour blocks instead of UTC hours.
- The log version counter is volatile, so the UI can no longer miss an update
  and stay stale for the session.
- Log listeners are coalesced: one UI rebuild per batch of lines instead of one
  per line.

### Changed

- SQLite maintenance is batched: retention runs every 32 writes and the
  incremental vacuum every 128, instead of five write transactions per log line.
- Release builds are no longer minified, so a crash produces a readable stack
  trace and the APK stays easy to inspect.
- A fatal crash is saved on the phone and shown on the next launch with a
  "Copy log" button, instead of the app silently closing.
- Startup failures draw a recovery screen with the same Copy log action, and
  background failures go to `last-failure.txt` instead of killing the process.
- The version code now derives from `VERSION` (`1.1.1` → `10101`), so a
  docs-only push can no longer publish a second APK under the same version
  name.

## [1.1.0] - 2026-11-02

History, storage and lock-screen fixes.

### Added

- SQLite storage for IP readings, reset runs and the activity log. One file,
  indexed reads, automatic pruning, no more rewriting a JSON blob on every log
  line.
- **History** screen: every reset with the address before and after, the engine
  that ran it, how long it took, and a filter for new IP / same IP / failed.
  Tap a run to see the log lines that belong to it.
- Aggregate insights from the database: totals, success rate, the best hour
  window, and a warning when the carrier keeps handing back the same address.
- Share the history as plain text, and clear it from the same screen.
- Public IP card now shows a real BEFORE → AFTER block with timestamps and a
  CHANGED / SAME verdict, plus an expandable list of the last eight checks.
- IPv4 readings are stored with the moment they were taken, so the comparison
  is honest rather than inferred.

### Fixed

- Arming a timer with no engine enabled now explains the problem and opens
  Setup instead of silently failing when the alarm fires.
- A secure lock screen (PIN, pattern, password) is detected before automation
  runs. The accessibility engine fails in seconds with an actionable message
  instead of waiting 90 seconds and giving up.
- The wake lock is only taken when the chosen engine actually needs the screen,
  so the ADB engine can run with the phone locked.
- Reset runs are recorded even when the network did not come back.

## [1.0.0] - 2026-11-02

Initial release.

### Added

- Countdown timer with a default of 15 minutes and an exact alarm to wake the
  app when the timer ends.
- Foreground service that runs a reset and shows its progress in a persistent
  notification.
- Three airplane-mode engines:
  - Direct: writes the airplane-mode secure setting after a one-time ADB grant
    of `android.permission.WRITE_SECURE_SETTINGS`.
  - Accessibility: opens the Airplane mode Settings page and taps the switch
    through accessibility nodes.
  - Quick Settings: opens the Quick Settings shade and taps the Airplane tile,
    with calibrated percentage coordinates as a fallback.
- Automatic engine selection: Direct first, then Accessibility with Quick
  Settings before the Settings page.
- Public IP check before and after a reset using api.ipify.org, with
  icanhazip.com and ifconfig.me as fallbacks.
- Safety restore that turns airplane mode back off after a run, including after
  an error.
- Rescheduling after reboot through the boot receiver.
- Local activity log with the last 120 entries and a clear action.
- Optional, experimental re-enable of the Wi-Fi hotspot through Settings
  automation.
- GitHub Actions build pipeline that produces a signed APK, publishes a GitHub
  Release for `v*` tags, and deploys the download page and APK metadata to
  GitHub Pages from `main`.
- Setup documentation, privacy notes, security policy, contribution guide, and
  MIT license.

### Known limitations

- A new public IP is not guaranteed. Jio and other carriers use carrier-grade
  NAT, and the carrier decides whether a reconnect receives a different
  address.
- UI automation requires an unlocked screen and varies between OEM skins.
- Re-enabling the hotspot after airplane mode is experimental and may not
  restore connected clients.
