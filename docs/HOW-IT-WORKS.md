# How T9 APK Tools works

T9 APK Tools (`dev.t9.apktools`) is a sideloaded, no-root Android app that
automates the network toggle people use to make a mobile session request a new
public IP. It runs a countdown, briefly switches Airplane mode — or cycles
mobile data through Shizuku without touching Airplane mode — restores the
previous state, and then asks a public-IP service what address the network has
now.

It is not a VPN, a proxy, or an IP pool. It only automates a toggle a user could
perform by hand, and it reports what happened. **A new public IP is never
guaranteed.** The carrier decides whether a reconnect gets a different address.

- Android 8.0+ (minSdk 26), target and compile SDK 34
- Native Java 17, no root, no Play Store listing
- Data (Shizuku, no Airplane mode), Direct, Accessibility, and Quick Settings
  engines
- Foreground service, exact alarm, boot receiver
- Local SQLite history: newest 300 log events, 150 runs, 80 IP readings

## The constraint: no public airplane-mode API

Android does not expose a normal third-party API for Airplane mode.

`Settings.Global.AIRPLANE_MODE_ON` is a secure setting. Writing it requires
`android.permission.WRITE_SECURE_SETTINGS`, whose protection level is
`signature|privileged|development`. A regular app cannot request it with a
runtime permission dialog. The hidden/system API
`ConnectivityManager.setAirplaneMode()` is also unavailable to third-party
apps. The public `Settings.ACTION_AIRPLANE_MODE_SETTINGS` intent only opens the
Settings screen; it does not change the state.

That leaves three practical, non-root paths, and the app implements all three:

1. **Data:** run `/system/bin/cmd phone data disable|enable` (or `svc data` as a
   fallback) with the ADB shell identity. The shim belongs to the shell UID,
   which holds `MODIFY_PHONE_STATE`; a normal app cannot call it. The app binds a
   tiny Shizuku user service for exactly these two commands. Airplane mode is
   never touched.
2. **Direct:** a one-time ADB grant of `WRITE_SECURE_SETTINGS`. This is a
   development permission, not root. It is granted per Android user, survives
   reboots and app updates, and is removed when the app is uninstalled.
3. **Accessibility:** a user-enabled `AccessibilityService` opens the Settings
   page or Quick Settings shade and clicks the Airplane control.

```bash
adb shell pm grant dev.t9.apktools android.permission.WRITE_SECURE_SETTINGS
```

`pm grant` works here because Android classifies `WRITE_SECURE_SETTINGS` as a
development permission. It does not make the app a system app, and it does not
grant any other privileged permission.

Android 13 and newer can also block accessibility for apps installed outside a
store until the user allows restricted settings. See
[SETUP.md](SETUP.md#android-13-and-newer-restricted-setting).

## Reset lifecycle

The app stores an absolute end time in `SharedPreferences` and schedules an
alarm for it. The foreground service is only a visible timer until the alarm
fires; the alarm is the wake-up mechanism.

1. **Arm.** `MainActivity` writes `target_at = now + duration`. Duration is
   1–180 minutes, default 15. It calls `ResetScheduler.schedule()` and starts
   `ResetService` with `ACTION_START`.
2. **Schedule.** `ResetScheduler` uses
   `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, target, pendingIntent)`
   when exact alarms are allowed. If Android 12+ has not granted exact-alarm
   access, it falls back to `setAndAllowWhileIdle`, which is inexact and may be
   deferred by Doze.
3. **Countdown.** `ResetService` calls `startForeground()` with a low-importance
   notification. The main screen updates its ring every second; the service
   refreshes the notification text every five ticks.
4. **Alarm.** `AlarmReceiver` receives the broadcast. If it fired more than 3
   seconds early, it reschedules. Otherwise it starts `ResetService` with
   `ACTION_RUN_NOW`.
5. **Run.** `ResetService.runReset()` sets the busy flag, acquires a 10-minute
   partial wake lock, and calls `ResetEngine.run()` on the app's I/O executor.
6. **Screen and keyguard.** For the accessibility engines (Settings and Quick
   Settings), if "Wake screen" is enabled, `ResetEngine` acquires a
   `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP` for up to 4 minutes. It
   then checks `KeyguardManager.isKeyguardLocked()`. If the keyguard is locked,
   it posts a high-importance unlock notification and waits up to 90 seconds.
   Data and Direct skip both steps and run screen-off or locked, OEM
   permitting.
7. **Before IP.** If "Verify public IP" is enabled, `IpUtil.fetch()` tries
   `https://api.ipify.org`, then `https://icanhazip.com`, then
   `https://ifconfig.me/ip`. Each request has a 5-second connect timeout and a
   5-second read timeout. The first valid answer is stored as the before
   address.
8. **Toggle.** The Airplane engines switch Airplane mode to the opposite of the
   state they found, wait `offDelaySec` (2–12 seconds, default 3), then restore
   the original state. Normally that is off → on → off. If Airplane mode was
   already on when the run started, it is on → off → on. The Data engine instead
   switches mobile data off, holds for `max(4, offDelaySec)` seconds, and
   switches it back on.
9. **Safety check.** Every engine reports success or failure. For the Airplane
   engines, `ResetEngine` reads `Settings.Global.AIRPLANE_MODE_ON` after the
   engine returns. If the state is not the original state, it runs the restore
   path again. The Data engine skips this read: the run never writes the
   Airplane setting.
10. **Connectivity.** `IpUtil.waitForInternet()` waits up to 60 seconds for an
    active network with `NET_CAPABILITY_INTERNET` and Airplane mode off. A data
    run uses `IpUtil.waitForOnline()`, which drops the Airplane requirement
    because that engine never changes it.
11. **After IP.** If verification is enabled, `IpUtil.fetch()` is attempted up
    to 4 times, 2 seconds apart. The result is compared as a string with the
    before address.
12. **Hotspot.** If enabled, and only after a successful cycle, the app asks
    the accessibility service to open tethering Settings and turn the hotspot
    back on. This is experimental.
13. **Finish.** The result is written to `Prefs`, a result notification is
    posted, and the local log is updated. If Repeat is on, the next target is
    scheduled from the current time; a manual `RESET NOW` preserves an already
    armed future target instead of cancelling it.

`success` means the cycle completed: the toggle was accepted, the state was
restored, and the network came back. It does **not** mean the IP changed.
`ipChanged` is true only when both addresses were read and differ. If the
before read failed, or the after read failed, the cycle can still be successful
while `ipChanged` is false.

## Sequence

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as MainActivity
    participant AM as AlarmManager
    participant AR as AlarmReceiver
    participant SVC as ResetService
    participant RE as ResetEngine
    participant E as Engine
    participant OS as Android
    participant IP as IP endpoints

    U->>UI: Pick duration and tap Start
    UI->>UI: Prefs.targetAt = now + duration
    UI->>AM: setExactAndAllowWhileIdle(target)
    UI->>SVC: startForegroundService(ACTION_START)
    SVC-->>U: Countdown notification

    Note over AM: Doze or a denied exact-alarm grant can delay this
    AM->>AR: ALARM broadcast
    AR->>SVC: ACTION_RUN_NOW
    SVC->>RE: run()

    opt Verify IP is enabled
        RE->>IP: HTTPS GET (ipify, then fallbacks)
        IP-->>RE: before address, or null
    end

    RE->>E: reset the network (Airplane engines toggle; Data cycles data)
    E->>OS: write setting / click switch / tap tile / run shell command
    OS-->>E: state readback
    RE->>RE: sleep offDelaySec (2-12 s)
    RE->>E: restore the initial state
    E->>OS: write setting / click switch / tap tile / run shell command

    alt Restore failed
        RE->>OS: safety restore (Direct, then Settings UI)
    end

    RE->>OS: wait up to 60 s for connectivity
    opt Verify IP is enabled
        RE->>IP: HTTPS GET, up to 4 attempts
        IP-->>RE: after address, or null
    end

    RE-->>SVC: Result
    SVC-->>U: Result notification and local log
    opt Repeat is enabled
        SVC->>AM: schedule the next target
    end
```

## Engines

The UI names differ slightly from the internal strategy values:

| UI option | Internal value | Mechanism | Screen requirement |
| --- | --- | --- | --- |
| Auto (recommended) | `auto` | Data if Shizuku is connected and granted; otherwise Direct, then Quick Settings, then Settings UI | Depends on the chosen path |
| Data — Shizuku, no Airplane mode | `data` | Binds a Shizuku user service and runs `cmd phone data disable` then `enable`, with `svc data` as a fallback | No UI automation; screen-off and locked are supported |
| Direct — ADB grant, instant | `direct` | Writes `Settings.Global.AIRPLANE_MODE_ON` | No UI automation; screen-off and locked are supported (OEM permitting). A secure lock fails UI automation in seconds. |
| Accessibility — Settings UI | `settings` | Opens the Airplane mode Settings page and clicks the switch | Unlocked, screen on |
| Quick Settings tile | `qs` | Opens the Quick Settings shade and clicks the tile, then taps calibrated coordinates if needed | Unlocked, screen on |

### Data engine

Requires Shizuku installed, running, and this app granted. The check is
`ShellEngine.available()`: `Sui.isSui()` or `Shizuku.pingBinder()`, plus
`Shizuku.checkSelfPermission()`.

`ShellEngine.dataReset()`:

- binds a Shizuku user service (`ShellUserService`) that runs with the ADB shell
  identity;
- asks it to switch mobile data off, and arms a watchdog in that process;
- sleeps `max(4, offDelaySec)` seconds;
- asks it to switch mobile data back on, which cancels the watchdog;
- unbinds the service and stops its process.

If the app process is killed during the hold, the watchdog switches data back on
after the hold plus 30 seconds. It stays in the shell process, so it does not
depend on the app UI. Enabling data that is already enabled is a no-op, so a
late watchdog cannot fight the normal path.

Inside the service, `setMobileData()` runs:

```text
/system/bin/cmd phone data disable
/system/bin/cmd phone data enable
```

If that exits non-zero, it tries `/system/bin/svc data disable|enable`. The
return value says which path worked (`ok:cmd-phone-disable` or
`ok:svc-disable`), and the app puts it in the log.

Failure modes:

- **Shizuku not running.** `NOT CONNECTED` in Setup; Auto skips Data; an
  explicit Data run logs `Shizuku is not connected or not granted. Connect it in Setup.`
- **Grant missing.** The binder is alive but `checkSelfPermission` is not
  granted; same message.
- **Neither command exists.** `cmd phone` was added in Android 11; `svc data` is
  broken on several Android 11–13 stock builds and works again on some Android
  14 builds. The log shows both exit codes, e.g. `fail:cmd=1,svc=1`.
- **The service does not start.** The bind has a 15-second timeout; the log says
  `Shizuku did not start the shell service.`
- **Mobile data does not come back.** The run fails with
  `Mobile data did not come back (…)`. Turn mobile data on manually.
- **Airplane mode was on before the run.** The cycle cannot help; the
  connectivity wait still fails or the IP does not change. Turn Airplane mode
  off first.

Shizuku is stopped by a reboot on a non-rooted phone. Sui or root keeps it
available across boots.

### Direct engine

Requires `WRITE_SECURE_SETTINGS` to be granted to `dev.t9.apktools`.

`directToggle()`:

- reads the initial state;
- calls `Settings.Global.putInt(..., AIRPLANE_MODE_ON, !initial)`;
- polls the setting every 200 ms for up to 5 seconds until it reads back the
  requested value;
- logs the toggle, sleeps `offDelaySec`, and writes the initial value back;
- returns true only if the restore readback matches the initial state.

Failure modes:

- **Grant missing.** The engine refuses to run and the log says
  `ADB permission is missing. Open Setup and copy the grant command.`
- **Write rejected.** `putInt` can throw `SecurityException`, or the write can
  be silently ignored. The 5-second readback catches both.
- **Setting accepted, radios not cycled.** On some OEM builds the framework
  stores the value but does not detach the modem. The setting looks changed;
  the public IP does not.
- **Work profile or secondary user.** The grant is per user. A grant in user 0
  does not apply to a work-profile copy.
- **Policy or OEM restriction.** Some managed devices and some manufacturer
  builds ignore direct secure-setting writes.
- **Keyguard.** Direct does not wait for the keyguard. It writes the secure
  setting with the screen off or locked, though some OEMs block that write in
  the background.

### Accessibility Settings engine

Requires the accessibility service to be enabled and connected.

`resetViaSettings()`:

- reads the current Airplane state;
- starts `Settings.ACTION_AIRPLANE_MODE_SETTINGS`, falling back to
  `Settings.ACTION_WIRELESS_SETTINGS` if the first intent cannot be resolved;
- waits 1.6 seconds for the page;
- calls `waitForToggle(!before, 7000)` to find and click the switch;
- sleeps `offDelaySec`;
- calls `waitForToggle(before, 7000)` to restore it;
- presses Back twice in a `finally` block.

Failure modes:

- **Accessibility enabled but not connected.** `ResetEngine` waits 4 seconds
  for the service instance, then logs
  `Accessibility is enabled but not connected. Toggle it off/on in Android Settings.`
- **Different Settings layout.** The service matches English labels, not view
  IDs. A translated or heavily customized page may have no matching node.
- **Switch not exposed.** Some OEMs render the control as a custom view that
  does not report `clickable` or `checkable`; the parent/sibling fallback may
  still find it, but not always.
- **Page below the fold.** After three empty loops, the service scrolls one
  scrollable node forward once. It does not keep scrolling.
- **Timeout.** Each direction has 7 seconds. A slow device or a page that
  redirects to a sub-page can miss it.
- **Locked screen.** UI automation cannot drive the keyguard.

### Accessibility Quick Settings engine

Requires the accessibility service.

`resetViaQuickSettings()`:

- reads the current Airplane state;
- calls `GLOBAL_ACTION_QUICK_SETTINGS`, waits 1.1 seconds;
- calls `waitForToggle(!before, 2600)`;
- if node matching fails, taps the calibrated screen percentage, waits 0.9
  seconds, and checks the state again;
- sleeps `offDelaySec`;
- restores by checking the state, waiting 2 seconds, tapping the percentage if
  needed, reopening the shade and waiting 2.6 seconds if needed, then tapping
  once more if needed;
- closes the shade with `GLOBAL_ACTION_BACK` in a `finally` block.

The percentage tap is a fallback, not the primary path. `Prefs` defaults are
12.5% X and 20% Y; the Setup sliders show and store whole percentages. The tap
is clamped to 1–99% of the current window bounds.

Failure modes:

- **Tile not on the first page.** The collapsed shade may not contain the
  Airplane tile. Node matching runs against the interactive windows that are
  present; if the tile is absent, the coordinate fallback is the only option.
- **Wrong label or language.** Matching looks for `airplane` or `flight mode`
  in English. A translated tile may not match.
- **Stale calibration.** Rotation, resolution changes, display scaling, or an
  OEM layout change moves the tile. The percentage then hits whatever is at
  that position. The app checks only the Airplane state, so a wrong tile can be
  left toggled.
- **Shade fails to open.** A locked device or a system policy can block the
  global action.
- **Gesture blocked.** `canPerformGestures` is enabled, but some OEMs still
  ignore injected gestures in system windows.

### Auto selection

`Auto` is evaluated when the reset runs, not when the timer is armed:

1. If Shizuku is connected and granted, use Data.
2. Else if `WRITE_SECURE_SETTINGS` is granted, use Direct.
3. Else if the accessibility service is enabled, try Quick Settings.
4. If Quick Settings returns false, try the Settings UI.
5. If no engine is available, fail with
   `No engine enabled. Grant ADB access, connect Shizuku, or turn on Accessibility in Setup.`

Auto does not silently skip a granted-but-broken Direct write. If the Direct
path runs and fails, the run fails; it does not automatically fall through to
accessibility. Select a different engine if your OEM ignores direct writes.

## Node matching and fallbacks

The accessibility service is configured with:

- `typeWindowStateChanged` and `typeWindowContentChanged` events;
- `flagRetrieveInteractiveWindows`, `flagReportViewIds`, and
  `flagIncludeNotImportantViews`;
- `canRetrieveWindowContent="true"` and `canPerformGestures="true"`;
- a 100 ms notification timeout.

`onAccessibilityEvent()` is intentionally empty. The service does not react to
events, does not cache the screen, and does not parse windows in the background.
All traversal happens synchronously when a reset calls the service.

### Finding the control

For each root (the active window plus every interactive window returned by
`getWindows()`), `collectAirplaneCandidates()` walks the tree:

- depth limit 50;
- at most 80 children per node;
- at most 12 candidates total;
- for each node, `text` and `contentDescription` are lowercased and checked for
  `airplane` or `flight mode` (substring match, not equality);
- when a match is found, the node is added, then its parent and up to 12
  clickable/checkable siblings are considered;
- `addCandidate()` walks up to 5 ancestors to find the first
  `clickable`/`checkable` node;
- duplicates are removed with a key of
  `className|bounds|text|contentDescription`.

`clickNode()` tries, in order:

1. `ACTION_CLICK` on the node;
2. `ACTION_SELECT` on the node;
3. `ACTION_CLICK` on up to 4 ancestors.

`waitForToggle(desired, timeout)` polls until the state matches or the timeout
expires:

- if no candidates are found for three loops, it calls `scrollForward()` once,
  which performs `ACTION_SCROLL_FORWARD` on the first scrollable node it finds;
- otherwise it clicks each candidate, waits 900 ms, and re-reads the state;
- it stops after 5 clicks;
- it sleeps 350 ms between empty loops and 300 ms after a click round.

The source of truth is always `Settings.Global.AIRPLANE_MODE_ON`. A successful
click is not trusted; the setting must read back as the desired value.

### Quick Settings fallback

If `waitForToggle()` cannot find or activate the tile within 2.6 seconds, the
service calls `tapPercent(xPercent, yPercent)`:

- the percentages come from `Prefs.qsXPercent()` and `Prefs.qsYPercent()`;
- the screen size comes from `WindowManager.getCurrentWindowMetrics()` on
  Android 11+, or `getRealMetrics()` on older versions;
- the point is clamped to 1..width-1 and 1..height-1;
- a 60 ms single-stroke gesture is dispatched;
- the service waits up to 2 seconds for the gesture callback, then 0.9 seconds
  before re-reading the state.

The first direction makes one calibrated tap attempt. The restore path has more
attempts: check state, wait 2 seconds, tap, then reopen Quick Settings, wait
2.6 seconds, and tap again. The shade is closed with Back in all cases.

The Setup screen exposes X and Y as sliders. This build does not capture a tap
to calibrate automatically; you position the tile and set the percentages
manually. See
[SETUP.md](SETUP.md#9-tune-timing-ip-checks-and-quick-settings).

## Safety restore

The app is designed to leave Airplane mode in the state it found, even when a
toggle fails:

- `directToggle()` attempts its own restore and returns false if the restore
  readback is wrong.
- `resetViaSettings()` and `resetViaQuickSettings()` return false if either
  direction fails.
- After any engine returns false, `ResetEngine` compares the current state with
  the initial state. If they differ, it calls `restoreAirplane(initial)`.
- `restoreAirplane()` tries the Direct write first if the grant exists, then
  calls `AccessibilityResetService.enforceAirplaneState(desired)`, which opens
  the Settings page and waits 7 seconds for the switch.
- If the final read still differs, the result message is
  `Airplane mode is still ON. Turn it off manually.`

There are real limits:

- If the process is force-stopped, killed by an OEM battery manager, or the
  device reboots in the middle of a run, the in-process restore cannot execute.
  Airplane mode may stay on. The app does not register a persistent
  "restore on boot" job for that case. For the Data engine, the shell watchdog
  re-enables mobile data after the hold plus 30 seconds, but only while the
  Shizuku service process itself is still alive.
- A `finally` block releases the screen wake lock and cancels the unlock
  notification, but it cannot undo a radio state that the OS refuses to change.
- The 10-minute partial wake lock is not a guarantee that the process survives.

If a run is interrupted, check the phone's network state and turn Airplane mode
off manually.

## Why the IP can stay the same

Jio and many other carriers use carrier-grade NAT (CGNAT). The phone typically
gets a private address, and the carrier maps many subscribers to a shared
public IPv4 address. The public address a website sees belongs to the carrier's
NAT pool, not to the phone.

A reconnect can return the same result because:

- the carrier reassigns the same public address from its pool;
- the CGNAT mapping is sticky and survives the brief radio detach;
- the phone receives the same private address and the carrier keeps the same
  translation;
- the check ran before the new session was usable, or before the carrier
  updated its mapping;
- a VPN is active, so the observed address is the VPN exit, not the carrier;
- IPv6 is in use, and the address family or prefix did not change;
- the app fell back to a different endpoint and compared an IPv4 answer with an
  IPv6 answer. The comparison is a string comparison, not a normalized address
  comparison.

A longer Airplane-mode pause can help on some networks; the Setup slider allows
2–12 seconds. Toggling mobile data, waiting, or retrying later can also help.
None of that guarantees a new address. The app reports what it observed and
does not fake a change.

## Foreground service, exact alarms, and Doze

- **Foreground service.** `ResetService` is declared with
  `android:foregroundServiceType="specialUse"` and the subtype
  `Scheduled network reset timer for developer testing`. It calls
  `startForeground()` immediately for the timer and for a run. On Android 14
  it passes `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`.
- **Notification.** The countdown uses the low-importance `Reset countdown`
  channel. Results use `Reset results`; the unlock prompt uses the
  high-importance `Attention needed` channel. On Android 13+, denial of
  `POST_NOTIFICATIONS` does not stop the timer, but the notification may not
  appear in the shade.
- **Exact alarms.** Android 12+ treats `SCHEDULE_EXACT_ALARM` as special app
  access, not a runtime permission. If it is allowed,
  `setExactAndAllowWhileIdle()` is used. If not, the app uses
  `setAndAllowWhileIdle()` and the timer can drift.
- **Doze.** `RTC_WAKEUP` wakes the device for the alarm, but Doze rate-limits
  while-idle alarms. Android may defer alarms set very close together. A 15
  minute default is above the usual while-idle throttle; shorter
  intervals are more likely to be late.
- **Boot.** `BootReceiver` handles `BOOT_COMPLETED` and
  `MY_PACKAGE_REPLACED`. If the target is still in the future, it re-arms the
  alarm and restarts the foreground timer. If the target has passed, it starts
  a reset immediately. The app must have been launched at least once after
  installation for Android to take it out of the stopped state.
- **Task removal.** `onTaskRemoved()` and `onDestroy()` re-arm a future target.
  A force-stop still cancels everything until the app is opened again.
- **OEM managers.** Samsung, Xiaomi, Oppo, Vivo, Realme, OnePlus, and others
  can kill a foreground service or block autostart even when Android's own
  battery optimization is disabled. See
  [TROUBLESHOOTING.md](TROUBLESHOOTING.md#oem-background-restrictions).

## Battery cost

A reset is short but not free:

- the screen can be woken for up to 4 minutes when "Wake screen" is on;
- a partial wake lock is held for up to 10 minutes;
- the modem detaches and reattaches, which is the largest radio cost. The Data
  engine does the same for the mobile-data context and leaves Wi-Fi alone;
- a hotspot, if present, drops and its clients reconnect;
- IP verification makes up to two fetches per run, each trying up to three
  HTTPS endpoints with 5-second timeouts;
- the foreground service and its notification stay alive during the countdown.

The default is a one-shot 15-minute timer with a 3-second Airplane pause and IP
verification on. Repeat is off by default. For lower battery use, turn off IP
verification, turn off "Wake screen" if you do not need it, use Direct, and
avoid very short repeat intervals. None of these settings changes the carrier's
address assignment.

## Limits

- A new public IP is not guaranteed.
- The Data engine needs a running Shizuku and a build where `cmd phone data` or
  `svc data` works. Shizuku must be restarted after a reboot on a non-rooted
  phone.
- UI automation requires an unlocked screen and an English `airplane` or
  `flight mode` label, or a calibrated Quick Settings position.
- OEM Settings pages, tile layouts, and background policies vary.
- The IP endpoints are third parties and can be slow, blocked, or wrong.
- The app does not bypass carrier controls, root the device, or hide the
  connection from the carrier.
- Direct runs screen-off and locked; the accessibility engines need the screen
  on and the phone unlocked. A secure lock fails UI automation in seconds.
- The app is sideloaded. It is not on the Play Store.

For setup, see [SETUP.md](SETUP.md). For data handling, see
[PRIVACY.md](PRIVACY.md). For failure-specific fixes, see
[TROUBLESHOOTING.md](TROUBLESHOOTING.md).
