# Setup

This is the install and permission guide for T9 APK Tools. It is written for a
phone, not an emulator. The app is sideloaded; there is no Play Store listing.

- Package: `dev.t9.apktools`
- Android 8.0+ (minSdk 26), target 34
- No root, no account, no analytics
- Optional PC for the Direct grant, or to start Shizuku on Android 10 and older

A new public IP is not guaranteed. Setup makes the toggle reliable; it cannot
make the carrier assign a different address.

## Before you start

Download the APK from the project's
[GitHub Pages download page](https://engrtarun.github.io/t9-apk-tools/) or from
[GitHub Releases](https://github.com/engrtarun/t9-apk-tools/releases). The
Pages directory also publishes `version.json` with the version, size, SHA-256,
and signer SHA-256 if you want to verify the file before installing.

Builds from the repository use a public community signing key. Read the
[security note](../SECURITY.md) before installing if you care about update
provenance.

## 1. Install the APK

1. Open the download page on the phone and download
   `t9-apk-tools.apk`.
2. Open the file from the browser's download notification or from your file
   manager.
3. If Android asks for permission to install unknown apps, allow it for the
   app you are installing from — the browser or the file manager. You can
   revoke that permission afterwards.
4. Tap **Install**. If Play Protect shows an "unsafe app" or "app not
   recognized" warning, review the source and the signature first. Do not
   continue if you do not trust the build.
5. Open T9 APK Tools. The main screen shows a large countdown ring, a status
   pill (`IDLE`, `ARMED`, `RUNNING`, `DONE`, or `CHECK`), duration chips
   (`5`, `10`, `15`, `20`, `30`, `CUSTOM`), a **START … MIN TIMER** button, a
   **RESET NOW** button, a **REPEAT** switch, two access chips
   (`ACCESSIBILITY OFF/ON`, `ADB DIRECT OFF/ON`), a **PUBLIC IP** card, an
   **ACTIVITY** log panel, an **ALL RESETS** button that opens History, and a
   **SETUP** button in the top-right. History lists every run with before →
   after, engine, duration, filters (new/same/failed), share and clear, plus
   expandable log lines.

If you do not see an icon, see
[App installed but no launcher icon](TROUBLESHOOTING.md#app-installed-but-no-launcher-icon).

## 2. Allow notifications

The countdown and the result are notifications. On Android 13 and newer the
first launch asks for notification permission.

1. When the permission dialog appears, tap **Allow**.
2. If you denied it, open the app and tap **SETUP**.
3. In the **SYSTEM ACCESS** card, find **Notifications**. The status pill
   shows `ON` or `OFF`.
4. Tap **OPEN**. If the runtime prompt appears, allow it. Otherwise Android
   opens the app's notification settings; enable **All notifications**.
5. Check the three channels: **Reset countdown**, **Reset results**, and
   **Attention needed**. The first is low importance by design; it is the
   persistent foreground-service notification, not an alarm sound.

Denying notifications does not stop the timer. The service still runs and the
log records a warning, but you will not see the countdown or result.

## 3. Allow exact alarms

Android 12 and newer treat exact alarms as special app access.

1. Open **SETUP**.
2. In **SYSTEM ACCESS**, find **Exact alarms**. The pill shows `ON`, `OFF`, or
   `DEFAULT` (Android 8–11, where exact alarms are allowed by default).
3. Tap **OPEN**.
4. On the system screen, enable **Alarms & reminders** for T9 APK Tools. The
   label can be **Allow setting alarms and reminders** on some builds.
5. Return to the app. The pill should read `ON`.

You can also trigger the same flow when you start a timer: if exact alarms are
not allowed, the app shows **Allow exact alarms** with an **Open settings**
button.

If exact alarms are denied, `ResetScheduler` falls back to
`setAndAllowWhileIdle()`. The timer still works, but Doze can delay it. See
[Timer fires late](TROUBLESHOOTING.md#timer-fires-late).

## 4. Disable battery optimization

Android and OEM battery managers can kill a foreground service. Exempt the app.

1. Open **SETUP**.
2. Find **Battery optimization**. The pill shows `EXEMPT` or `OPTIMIZED`.
3. Tap **OPEN**.
4. If the app is not exempt, Android shows a dialog asking whether to let T9
   APK Tools run in the background. Tap **Allow**.
5. If it is already exempt, Android opens the battery-optimization list. Find
   T9 APK Tools and choose **Don't optimize** / **Unrestricted**.

On Samsung, Xiaomi, Oppo, Vivo, Realme, OnePlus, and similar skins, also check
the OEM autostart or background-activity settings. See
[OEM background restrictions](TROUBLESHOOTING.md#oem-background-restrictions).

## 5. Enable the accessibility service

This is required for the **Accessibility — Settings UI** and
**Quick Settings tile** engines. It is optional if you use Direct.

The service is named **T9 Network Reset** in Android Settings; the app is
**T9 APK Tools**. That naming difference is intentional and is a common source
of confusion.

1. Open **SETUP**.
2. In **SYSTEM ACCESS**, find **Accessibility service**. The pill shows `ON` or
   `OFF`.
3. Tap **OPEN**.
4. On Android 13 and newer, the first tap may show **If the toggle is greyed
   out**. Read it, then tap **Continue**. If the toggle really is greyed out,
   use the restricted-settings workaround below.
5. In Android Settings, open **Accessibility** → **Downloaded apps** (or
   **Installed apps** / **Installed services**, depending on the skin).
6. Tap **T9 Network Reset**.
7. Turn the service on. Android shows a confirmation explaining that the
   service can view and control the screen. Allow it only if you are
   comfortable with that access.
8. Return to the app. The **Accessibility service** pill should read `ON`, and
   the main screen chip should read `ACCESSIBILITY ON`.

The service performs window traversal only during a reset. It does not process
events in the background and does not store or transmit window content. The
trade-off is still broad: while enabled, Android lets it inspect window content.
Turn it off
in Android Settings when you no longer need it. See
[PRIVACY.md](PRIVACY.md#accessibility-service).

### Android 13 and newer: "Restricted setting"

Android 13 and newer can block accessibility for apps installed outside a
store. The toggle appears greyed out, or Android shows **Restricted setting**
when you try to enable it.

1. Open **Settings** → **Apps** → **T9 APK Tools**.
2. On the App info screen, tap the **⋮** (three-dot) menu in the top-right.
3. Choose **Allow restricted settings**.
4. Confirm with your PIN, pattern, or password if asked.
5. Go back to **Settings** → **Accessibility** → **T9 Network Reset** and turn
   the service on again.

The menu item is only present for apps Android considers restricted. If you do
not see it, make sure you are on the App info screen for `dev.t9.apktools`, not
the app's own Setup screen. On some OEM builds the item is under a **More**
menu instead of **⋮**.

## 6. Choose an engine

Open **SETUP** → **RESET ENGINE**. The options are:

| Option | What it does | Needs |
| --- | --- | --- |
| **Auto (recommended)** | Data if Shizuku is connected and granted; otherwise Direct, then Quick Settings, then the Settings UI | Shizuku, a Direct grant, or accessibility |
| **Data — Shizuku, no Airplane mode** | Switches mobile data off and on through Shizuku; Airplane mode, Wi-Fi, and the hotspot are untouched | Shizuku installed, running, and granted; no screen required |
| **Direct — ADB grant, instant** | Writes the Airplane secure setting; no UI automation | One-time ADB grant; runs screen-off or locked (OEM permitting) |
| **Accessibility — Settings UI** | Opens the Airplane mode page and clicks the switch | Accessibility on, phone unlocked |
| **Quick Settings tile** | Opens the shade and clicks the tile, with percentage fallback | Accessibility on, phone unlocked, tile visible |

Auto order at run time is Data → Direct → Quick Settings → Settings UI. Auto uses
Data only when Shizuku reports `READY`; otherwise it skips to the next engine.
If Direct is granted but the OEM rejects the write, Auto does not fall through to
accessibility; the run fails. Choose Accessibility explicitly on such a device.

The strategy is read when the reset runs, so changing it while a timer is armed
affects the next reset.

## 7. Data reset (optional, no Airplane mode)

The Data engine cycles mobile data instead of Airplane mode, so Wi-Fi, the
hotspot, and the Airplane setting are never touched. It needs the Shizuku helper
app; it does not need root, and it can run with the screen locked.

1. Install Shizuku from
   [shizuku.rikka.app/download](https://shizuku.rikka.app/download/).
2. Start Shizuku:
   - **Android 11 and newer:** open Shizuku, choose **Start via wireless
     debugging**, enable **Wireless debugging** in Developer options, pair, and
     start. No computer is needed.
   - **Android 10 and older, or if the on-device start fails:** connect the
     phone to a computer with USB debugging and run the start command Shizuku
     shows. It is usually
     `adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`.
3. Open T9 APK Tools → **SETUP**. The **SHIZUKU — NO AIRPLANE** card shows
   `NOT CONNECTED` while Shizuku is not running.
4. Tap **CONNECT SHIZUKU**. Android shows a permission dialog; allow T9 APK
   Tools. The pill changes to `READY`.
5. Choose **Data reset — Shizuku, no Airplane mode**, or leave **Auto** on. Auto
   uses Data only when the pill says `READY`.
6. Test with **RESET NOW** while watching the **ACTIVITY** panel.

A working run logs:

```text
Data engine: Shizuku shell, Airplane mode untouched
Mobile data off (cmd-phone-disable) — Airplane mode untouched
Data hold finished — switching mobile data back on
Mobile data is reconnecting (cmd-phone-enable)
```

The hold time is the **Airplane mode stays on** value in **SETUP** → **TIMING**,
with a floor of 4 seconds, because a data context needs longer than an Airplane
toggle to release. 4–8 seconds is a reasonable range.

If Shizuku is not running, the pill reads `NOT CONNECTED`, Auto skips Data, and
an explicit Data run stops with
`Shizuku is not connected or not granted. Connect it in Setup.` instead of
falling back to Airplane mode.

Notes:

- Shizuku stops when the phone reboots unless it runs through Sui or root. Start
  it again after a reboot.
- The engine tries `cmd phone data disable|enable` first. Android 11+ has the
  command; some OEM builds and older releases do not, so it falls back to
  `/system/bin/svc data disable|enable`. Both are build-dependent; the log shows
  which one worked.
- The data context drop can take mobile data away from hotspot clients for the
  hold window. Wi-Fi itself stays up.
- A new public IP is still not guaranteed.

## 8. Optional: ADB grant for Direct

This is the only step that needs a computer, and it is optional. The grant:

- is a development permission, not root;
- is per Android user;
- survives reboots and app updates;
- is removed when the app is uninstalled;
- lets the app write `Settings.Global.AIRPLANE_MODE_ON` directly.

The exact command is also shown in **SETUP** → **DIRECT MODE — OPTIONAL**, with
a **COPY COMMAND** button:

```bash
adb shell pm grant dev.t9.apktools android.permission.WRITE_SECURE_SETTINGS
```

### Prepare the phone

1. Open **Settings** → **About phone**.
2. Tap **Build number** seven times. On Samsung, use **About phone** →
   **Software information** → **Build number**.
3. Enter your PIN, pattern, or password if asked. Developer options are now
   enabled.
4. Open **Settings** → **System** → **Developer options** (the path varies by
   skin).
5. Turn on **USB debugging**.
6. Connect the phone to the computer by USB.
7. On the phone, choose **File transfer** (or **MTP**) if a USB mode prompt
   appears.
8. When **Allow USB debugging?** appears, tap **Allow**. Check **Always allow
   from this computer** if you do not want to repeat it.

### Windows

1. Install Android SDK Platform-Tools from Google. If you use Android Studio,
   the tools are usually in
   `%LOCALAPPDATA%\Android\Sdk\platform-tools`.
2. Open PowerShell in that folder, or add it to `PATH`.
3. Check that the phone is visible:

   ```powershell
   cd "$env:LOCALAPPDATA\Android\Sdk\platform-tools"
   .\adb devices
   ```

   You should see the serial number followed by `device`. If it says
   `unauthorized`, unlock the phone and accept the debugging prompt. If it says
   `offline`, unplug and replug, or run `.\adb kill-server` and try again.

4. Grant the permission:

   ```powershell
   .\adb shell pm grant dev.t9.apktools android.permission.WRITE_SECURE_SETTINGS
   ```

5. If Windows does not recognize the phone, install the OEM USB driver or the
   Google USB driver. A charge-only cable will not work.

### macOS and Linux

1. Install Platform-Tools. With Android Studio, the usual paths are
   `~/Library/Android/sdk/platform-tools` on macOS and
   `~/Android/Sdk/platform-tools` on Linux. A distribution package such as
   `android-tools-adb` also works.
2. Check the connection:

   ```bash
   cd ~/Library/Android/sdk/platform-tools   # macOS
   # or: cd ~/Android/Sdk/platform-tools     # Linux
   ./adb devices
   ```

3. Grant the permission:

   ```bash
   ./adb shell pm grant dev.t9.apktools android.permission.WRITE_SECURE_SETTINGS
   ```

On Linux, if `adb devices` shows `no permissions`, add a udev rule for your
device or run the tools with the appropriate group access. On macOS, no driver
is normally needed.

### Wireless debugging

Android 11 and newer can do the grant without a USB cable. The phone and the
computer must be on the same network, and a VPN on either side can block
discovery.

1. On the phone, open **Developer options** → **Wireless debugging** and turn
   it on.
2. Tap **Pair device with pairing code**. Note the IP address, the pairing
   port, and the six-digit code.
3. On the computer:

   ```bash
   adb pair 192.168.1.50:37123
   # enter the six-digit code when prompted
   ```

4. Go back to the Wireless debugging screen and note the IP address and port
   shown there. This is a different port from the pairing port.

   ```bash
   adb connect 192.168.1.50:5555
   adb devices
   ```

5. Grant the permission:

   ```bash
   adb shell pm grant dev.t9.apktools android.permission.WRITE_SECURE_SETTINGS
   ```

The ports in the example are placeholders; use the ones on your screen. The
connect port can change after a reboot or after toggling wireless debugging.

### Verify the grant

In the app, open **SETUP** → **DIRECT MODE — OPTIONAL**. The status pill should
read `GRANTED`, and the main screen chip should read `ADB DIRECT ON`.

From a computer, run the check requested in the project docs:

```bash
adb shell dumpsys package dev.t9.apktools | grep WRITE_SECURE_SETTINGS
```

PowerShell does not ship `grep`; use:

```powershell
adb shell dumpsys package dev.t9.apktools | Select-String WRITE_SECURE_SETTINGS
```

Or in Command Prompt:

```bat
adb shell dumpsys package dev.t9.apktools | findstr WRITE_SECURE_SETTINGS
```

Look for `android.permission.WRITE_SECURE_SETTINGS` under
`grantedPermissions`. Some Android builds print `granted=true` on the same
line; others only list the permission.

If the command prints nothing, the grant did not apply. Check the package name,
the user, and the command syntax.

### Revoke the grant

```bash
adb shell pm revoke dev.t9.apktools android.permission.WRITE_SECURE_SETTINGS
```

The app's Direct status returns to `NOT GRANTED`. Uninstalling the app also
removes the grant.

If the phone has multiple users or a work profile, grant or revoke the
permission for the user that actually runs the app. `pm` accepts
`--user <id>` before the package name on current Android versions.

## 9. Tune timing, IP checks, and Quick Settings

Open **SETUP** → **TIMING**.

- **Airplane mode stays on** — 2–12 seconds, default 3. The app's own hint says
  3–5 seconds gives the radio time to detach and reattach. A longer pause is
  not a guarantee of a new IP. The Data engine uses the same value as its
  mobile-data pause, with a floor of 4 seconds.
- **Wake screen and wait for unlock** — default on. Accessibility cannot drive
  a locked screen. This wait applies only to the accessibility engines; Direct
  skips it and runs screen-off or locked, though some OEMs block secure-setting
  writes in the background.
- **Verify public IP before and after** — default on. Turning it off removes
  the HTTPS calls, but the app can no longer tell you whether the address
  changed.
- **Re-enable Wi-Fi hotspot after reset** — experimental, default off. It uses
  the accessibility service to open tethering Settings and click the hotspot
  switch. Android and OEMs do not guarantee this works.

**QUICK SETTINGS CALIBRATION** is only for the Quick Settings engine. Put the
Airplane tile in the top-left of the expanded shade, then set **Tile X
position** and **Tile Y position** as percentages of the screen. The defaults
are 12% and 20%. The app uses these coordinates only when it cannot find the
tile by its label. This build exposes the percentages as sliders; it does not
capture a tap for you. Test with **RESET NOW** after changing them.

**DANGER ZONE**:

- **RUN A RESET NOW** starts a manual cycle. It does not cancel an armed timer.
- **RESET ALL SETTINGS & LOGS** stops the timer, clears the activity log, and
  resets preferences. It does not revoke the ADB grant or Android permissions.

## 10. Arm the timer

1. On the main screen, choose a preset (`5`, `10`, `15`, `20`, `30`) or
   **CUSTOM** and enter 1–180 minutes.
2. Turn **REPEAT** on if you want the timer to re-arm after each reset. It is
   off by default.
3. Tap **START … MIN TIMER**. The button changes to **CANCEL TIMER**, the ring
   counts down, and the persistent notification appears.
4. If you are using an accessibility-based engine, leave the phone unlocked
   and the screen available. Direct and Data skip that wait and run screen-off
   or locked, OEM permitting.
5. Cancel from the main button or from the notification's **Cancel** action.

The alarm is the reliable wake-up path. The foreground service keeps a visible
timer, but a killed service should still be replaced by the alarm if Android
delivers it.

## 11. Hotspot caveat

Airplane mode turns off Wi-Fi and the Wi-Fi hotspot. If the hotspot was on, it
will drop for the reset. The Data engine does not cause this: it never touches
Wi-Fi or the hotspot. The optional restore is experimental:

- it runs only after a successful reset cycle, not after an early failure;
- it needs the accessibility service, because Android does not give normal apps
  a public hotspot-toggle API;
- it opens tethering Settings and looks for a checkable control labeled
  "hotspot";
- OEM tethering pages vary, and some carriers or device policies block it.

Plan for the hotspot to stay off. If connected clients matter, keep a second
connection or turn the hotspot back on manually after the run. See
[Hotspot disappears](TROUBLESHOOTING.md#hotspot-disappears-and-does-not-come-back).

## 12. Verify a run

After a run, check:

- the notification title: **IP changed**, **Network reset complete**, or
  **Reset needs attention**;
- the **PUBLIC IP** card: `CHANGED`, `SAME`, `CURRENT`, `BEFORE`,
  `NOT CHECKED`, `CHECKING`, or `OFFLINE`;
- the **ACTIVITY** panel: the 10 most recent entries, with the engine, before
  and after addresses, and any error;
- **SETUP** status pills, especially if the run says an engine is missing.

The app keeps up to 300 log events. The ACTIVITY panel shows the newest 120 and
the main screen the newest 10. **CLEAR** removes the stored events.

A successful reset does not imply a new IP. If both addresses are present and
equal, the message is `Reset done. IP stayed … — the carrier may hold the
session.` That is a normal CGNAT outcome. See
[Why the IP can stay the same](HOW-IT-WORKS.md#why-the-ip-can-stay-the-same).

## Troubleshooting

| Symptom | Start here |
| --- | --- |
| Timer ends but Airplane mode never changes | [Toggle never happens](TROUBLESHOOTING.md#toggle-never-happens) |
| Airplane toggles but the IP is unchanged | [Same public IP](TROUBLESHOOTING.md#same-public-ip) |
| Shizuku pill says `NOT CONNECTED` | [Shizuku shows NOT CONNECTED](TROUBLESHOOTING.md#shizuku-shows-not-connected) |
| Data run fails with `svc data` / `cmd phone` errors | [Mobile data command failed](TROUBLESHOOTING.md#mobile-data-command-failed) |
| Hotspot is gone after the reset | [Hotspot disappears](TROUBLESHOOTING.md#hotspot-disappears-and-does-not-come-back) |
| Timer or notification disappears | [Service killed](TROUBLESHOOTING.md#service-killed-or-timer-disappears) |
| Accessibility toggle is greyed out | [Restricted setting](TROUBLESHOOTING.md#restricted-setting) |
| Accessibility says enabled but not connected | [Accessibility disconnects](TROUBLESHOOTING.md#accessibility-disconnects) |
| Exact-alarm status is OFF | [Exact alarm denied](TROUBLESHOOTING.md#exact-alarm-denied) |
| Timer fires late | [Timer fires late](TROUBLESHOOTING.md#timer-fires-late) |
| No countdown or result notification | [Notification missing](TROUBLESHOOTING.md#notification-missing) |
| `pm grant` is rejected | [ADB grant failures](TROUBLESHOOTING.md#adb-grant-failures) |
| App is killed in the background | [OEM background restrictions](TROUBLESHOOTING.md#oem-background-restrictions) |
| Wireless debugging will not connect | [Wireless debugging](TROUBLESHOOTING.md#wireless-debugging) |
| Installed, but no launcher icon | [No launcher icon](TROUBLESHOOTING.md#app-installed-but-no-launcher-icon) |

## Uninstall and cleanup

- **Clear the log only:** main screen → **ACTIVITY** → **CLEAR**.
- **Reset app settings and log:** **SETUP** → **DANGER ZONE** → **RESET ALL
  SETTINGS & LOGS**. The ADB grant and Android permissions stay.
- **Clear all app data:** Android **Settings** → **Apps** → **T9 APK Tools** →
  **Storage & cache** → **Clear storage** / **Clear data**.
- **Revoke the ADB grant:** run the `pm revoke` command above.
- **Uninstall:** long-press the icon → **App info** → **Uninstall**, or
  **Settings** → **Apps** → **T9 APK Tools** → **Uninstall**. This removes the
  app, its preferences, its logs, and the ADB grant. Nothing is stored on a
  server.

For the full data-handling note, see [PRIVACY.md](PRIVACY.md). For how the
engines work internally, see [HOW-IT-WORKS.md](HOW-IT-WORKS.md).
