# Troubleshooting

Start with the app's own status, then reproduce once with the phone unlocked.
Most failures are a missing permission, a disconnected accessibility service, or
an OEM background restriction — not the toggle code itself.

A new public IP is not guaranteed. "Same IP" is a normal CGNAT result, not
automatically a bug. See
[Why the IP can stay the same](HOW-IT-WORKS.md#why-the-ip-can-stay-the-same).

## Quick triage

1. Open the app → **SETUP**.
2. Check the six status pills in **SYSTEM ACCESS**, **SHIZUKU — NO AIRPLANE**,
   and **DIRECT MODE — OPTIONAL**:
   - **Accessibility service** — `ON` or `OFF`
   - **Notifications** — `ON` or `OFF`
   - **Exact alarms** — `ON`, `OFF`, or `DEFAULT`
   - **Battery optimization** — `EXEMPT` or `OPTIMIZED`
   - **Shizuku** — `READY`, `GRANT NEEDED`, or `NOT CONNECTED`
   - **Direct** — `GRANTED` or `NOT GRANTED`
3. Read the **ACTIVITY** panel on the main screen. It shows the newest 10 of
   the 120 stored entries.
4. Run **RESET NOW** with the phone unlocked. Watch the persistent
   notification; it shows the current step.
5. If you changed a permission, return to the app before testing. Some status
   pills refresh in `onResume()`.

## Symptoms and fixes

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| Timer reaches zero, but Airplane mode never changes | No usable engine; Direct grant missing; accessibility off or disconnected; locked keyguard; OEM rejects the write | Check the Setup pills, enable accessibility or grant ADB, unlock the phone, then test. See [Toggle never happens](#toggle-never-happens). |
| Data run stops with `Shizuku is not connected or not granted` | Shizuku is stopped or not installed; the app was not granted; the phone rebooted | Start Shizuku, then **SETUP** → **CONNECT SHIZUKU**. See [Shizuku shows NOT CONNECTED](#shizuku-shows-not-connected). |
| Data run logs `Mobile data would not switch off` | `cmd phone data` is missing and `svc data` is broken on this build | Read the exit codes in the log, then use Direct or an accessibility engine. See [Mobile data command failed](#mobile-data-command-failed). |
| Data run completes but the IP is unchanged | CGNAT kept the mapping, hold was short, VPN active | Lengthen the hold, retry, and compare before/after. See [IP stayed the same after a data reset](#ip-stayed-the-same-after-a-data-reset). |
| Airplane toggles, but the public IP is unchanged | CGNAT, sticky mapping, same address reassigned, VPN, IPv6, or the check ran too early | Not necessarily a failure. Retry, lengthen the Airplane pause, disable VPN, and compare before/after in the log. See [Same public IP](#same-public-ip). |
| Hotspot disappears and stays off | Airplane mode turns off Wi-Fi/hotspot; restore is off or experimental; accessibility is off; the reset failed before restore | Turn the hotspot on manually, enable **Re-enable Wi-Fi hotspot after reset** only if you have accessibility, and test. See [Hotspot disappears](#hotspot-disappears-and-does-not-come-back). |
| Timer or notification disappears; no reset | Foreground service killed; force-stop; OEM battery manager; app updated | Set battery to Unrestricted, allow OEM autostart, reopen the app, and re-arm. See [Service killed](#service-killed-or-timer-disappears). |
| Accessibility toggle is greyed out, or Android says "Restricted setting" | Android 13+ sideload restriction | App info → **⋮** → **Allow restricted settings**, then enable **T9 Network Reset**. See [Restricted setting](#restricted-setting). |
| Accessibility was on, but the app says it is enabled and not connected | Service process was killed, app updated, or OEM stopped it | Toggle the service off/on in Android Settings, check battery, and reboot if needed. See [Accessibility disconnects](#accessibility-disconnects). |
| Exact alarms show `OFF`, or the timer is delayed | Android 12+ special access not granted; permission revoked | Setup → **Exact alarms** → **OPEN** → allow **Alarms & reminders**. Re-arm. See [Exact alarm denied](#exact-alarm-denied). |
| Timer fires late | Doze; inexact fallback; OEM killer; exact access revoked; clock changed | Allow exact alarms, exempt battery, allow autostart, re-arm. See [Timer fires late](#timer-fires-late). |
| No countdown or result notification | Notification permission denied; channel blocked; OEM suppresses the foreground notification | App info → **Notifications** → allow; enable **Reset countdown**, **Reset results**, **Attention needed**. See [Notification missing](#notification-missing). |
| `adb shell pm grant` says "not allowed" or "not a changeable permission type" | Wrong user or package; typo; MIUI security debugging off; work profile; OEM blocks `pm grant` | Use the exact command, grant for the right user, enable MIUI's security debugging, or use wireless debugging. See [ADB grant failures](#adb-grant-failures). |
| App is killed in the background | OEM autostart/battery restriction, not Android's normal Doze behavior | Allow autostart and background activity for your OEM. See [OEM background restrictions](#oem-background-restrictions). |
| Wireless debugging will not pair or connect | Pairing port confused with connect port; different network; VPN; mDNS blocked | Use `adb pair` with the pairing port, then `adb connect` with the connect port, on the same Wi-Fi. See [Wireless debugging](#wireless-debugging). |
| App installed, but no launcher icon | Launcher hides sideloaded apps; work profile; app disabled; launcher cache | Search the app drawer, open it from App info, check the work profile, or reinstall. See [App installed but no launcher icon](#app-installed-but-no-launcher-icon). |
| Log says "Phone stayed locked" | Keyguard was locked for more than 90 seconds; screen wake is off; accessibility engine cannot drive the lock screen | Unlock within 90 seconds, enable **Wake screen and wait for unlock**, or run while the phone is unlocked. See [Phone stayed locked](#phone-stayed-locked). |
| Log says "Network did not come back in 60 s" | Mobile data is off, the radio is slow, the carrier is down, or Airplane mode is stuck | Check the status bar, toggle Airplane mode manually, verify mobile data, and retry. See [Network did not come back](#network-did-not-come-back-in-60-s). |
| Log says "Airplane mode is still ON" | The restore path failed; OEM blocked the write; accessibility disconnected; process killed | Turn Airplane mode off manually, then use a more reliable engine and check battery. See [Airplane mode is still on](#airplane-mode-is-still-on). |
| Log says "Could not read the public IP" | No internet; endpoints blocked; VPN/firewall; timeout | Open a browser, use the refresh action, check VPN/private DNS, or turn off IP verification. See [Could not read the public IP](#could-not-read-the-public-ip). |
| Quick Settings tap toggles the wrong tile | Calibration percentages are stale, the tile moved, or the screen rotated | Recalibrate X/Y, put Airplane in the top-left, or switch to the Settings engine. See [Quick Settings tap hits the wrong tile](#quick-settings-tap-hits-the-wrong-tile). |
| Direct says `GRANTED`, but the log says the write was rejected | OEM ignores direct secure-setting writes, or the app is in a managed/work profile | Use the accessibility engine, verify the grant, reboot, and report the device. See [Direct grant is present but the write is rejected](#direct-grant-is-present-but-the-write-is-rejected). |

## Useful log messages

| Message | What it means |
| --- | --- |
| `No engine enabled. Grant ADB access, connect Shizuku, or turn on Accessibility in Setup.` | Auto found no connected Shizuku, no Direct grant, and no enabled accessibility service. |
| `Shizuku is not connected or not granted. Connect it in Setup.` | The Data engine was selected, but the binder is dead or `checkSelfPermission` failed. |
| `Shizuku did not start the shell service. Open Shizuku, confirm it is running, then try again.` | `bindUserService` did not connect within 15 seconds. |
| `Mobile data off (cmd-phone-disable) — Airplane mode untouched` | The shell command accepted the disable. The suffix says which command worked. |
| `fail:cmd=…,svc=…` | Both telephony commands failed with those exit codes; the Data engine cannot run on this build. |
| `Mobile data is reconnecting (cmd-phone-enable)` | The shell command accepted the enable. The IP check follows. |
| `ADB permission is missing. Open Setup and copy the grant command.` | The Direct engine was selected, but `WRITE_SECURE_SETTINGS` is not granted to this user. |
| `Accessibility is enabled but not connected. Toggle it off/on in Android Settings.` | The service is listed as enabled, but no live service instance responded within 4 seconds. |
| `Direct write was rejected by the system` | `putInt` failed or the setting did not read back within 5 seconds. |
| `Airplane switch not found in Settings` | The Settings page opened, but no matching node was found or clicked within 7 seconds. |
| `Airplane tile not found in Quick Settings` | The shade opened, but the tile was not found and the calibrated tap did not change the state. |
| `Phone stayed locked. Unlock it and run again.` | The keyguard was still locked after the 90-second wait. No engine ran. |
| `Network did not come back in 60 s. Check mobile data.` | The toggle and restore completed, but no usable network appeared. |
| `Airplane mode is still ON. Turn it off manually.` | The safety restore also failed. |
| `Hotspot restore skipped: Accessibility not connected` | The hotspot option is on, but no accessibility service was available after the run. |

## Toggle never happens

Work through the engine path:

1. **Is any engine available?** In **SETUP**, check **Shizuku**, **Direct**, and
   **Accessibility service**. If all are off, Auto cannot do anything. Enable
   one, or use **Accessibility — Settings UI** explicitly after enabling the
   service.
2. **Is Auto choosing the path you expect?** Auto uses Data when Shizuku is
   `READY`, then Direct if granted. If the Direct grant is present but the OEM
   ignores direct writes, Auto fails at Direct; it does not fall through. Select
   **Accessibility — Settings UI** or **Quick Settings tile**.
3. **Is the keyguard locked?** This only blocks the accessibility engines. They
   wait up to 90 seconds, post **Unlock to finish the reset**, then give up with
   `Phone stayed locked`. Data and Direct skip this wait and work with the
   screen off or locked, OEM permitting.
4. **Is this a Quick Settings layout problem?** The tile may not be on the first
   page, or the label may not contain `airplane`/`flight mode`. Put the tile in
   the top-left and calibrate, or use the Settings engine.
5. **Is the OEM blocking the write?** Try the Settings engine. If that works,
   the Direct path is being ignored by the firmware.

See [SETUP.md](SETUP.md#6-choose-an-engine) and
[Node matching and fallbacks](HOW-IT-WORKS.md#node-matching-and-fallbacks).

## Same public IP

This is the most common "failure" and usually is not one:

- Jio and many carriers use CGNAT. The public address belongs to the carrier's
  pool and can be reused after a reconnect.
- The carrier can keep the same NAT mapping across a brief radio detach.
- The app may have checked before the new session was fully usable.
- A VPN makes the app see the VPN exit, not the carrier.
- IPv6 and IPv4 can differ; the app compares strings, not address families.
- A fallback endpoint can return a different family than the first endpoint,
  which the app counts as "changed" even if the carrier mapping did not change.

Things worth trying:

1. Increase **Airplane mode stays on** to 5–12 seconds. For the Data engine, the
   same slider sets the mobile-data hold, with a floor of 4 seconds.
2. Retry later; carrier pools and leases change over time.
3. Turn the VPN off before testing.
4. Toggle mobile data manually, or reboot the phone, then try again.
5. Compare the before and after lines in the **ACTIVITY** log, not just the
   badge.

The app cannot force a new address and does not fake one. See
[Why the IP can stay the same](HOW-IT-WORKS.md#why-the-ip-can-stay-the-same).

## Hotspot disappears and does not come back

Airplane mode turns off Wi-Fi and the hotspot. The app restores Airplane mode,
but hotspot restoration is a separate, experimental feature:

- It is off by default.
- It runs only after a successful cycle, after the IP check. If the run fails
  earlier, the hotspot stays off.
- It needs the accessibility service; Android has no public third-party
  hotspot-toggle API.
- It opens `android.settings.TETHER_SETTINGS` and looks for a checkable control
  labeled "hotspot". OEM tethering pages differ.

Fix:

1. Turn the hotspot back on manually.
2. If you want automation, enable **Re-enable Wi-Fi hotspot after reset** in
   **SETUP** → **TIMING**, and make sure accessibility is on.
3. Test with **RESET NOW** before relying on it.
4. Keep connected clients in mind: even if the hotspot returns, they may need
   to reconnect.

See [SETUP.md](SETUP.md#11-hotspot-caveat).

## Service killed or timer disappears

`ResetService` is a foreground service, so it should survive ordinary background
limits. OEM battery managers can still kill it.

Check:

- **Battery optimization** is `EXEMPT` in **SETUP**.
- The OEM autostart/background setting is allowed. See
  [OEM background restrictions](#oem-background-restrictions).
- You did not use **Force stop** on the app. A force-stop cancels the service
  and prevents the boot receiver from running until you open the app again.
- The persistent notification was not hidden by an OEM "clean" action.

If the timer is gone, reopen the app and arm it again. If the target passed
while the app was not running, `BootReceiver` starts a reset immediately on the
next boot; it does not replay every missed cycle. If a run was interrupted,
Airplane mode may be left on; check and turn it off manually.

See [Foreground service, exact alarms, and Doze](HOW-IT-WORKS.md#foreground-service-exact-alarms-and-doze).

## Restricted setting

Android 13+ can block accessibility for apps installed outside a store.

1. Open **Settings** → **Apps** → **T9 APK Tools**.
2. Tap **⋮** in the top-right of the App info screen.
3. Choose **Allow restricted settings**.
4. Confirm with your screen lock.
5. Go to **Settings** → **Accessibility** → **T9 Network Reset** and turn it on.

If the menu item is missing, confirm you are on the App info screen for
`dev.t9.apktools`, not the app's own Setup screen. Some OEM skins put the item
under a **More** menu. If it is still absent, the build may not expose the
control; report the device and Android build with a screenshot of the App info
menu.

See [SETUP.md](SETUP.md#android-13-and-newer-restricted-setting).

## Accessibility disconnects

The log says:

```text
Accessibility is enabled but not connected. Toggle it off/on in Android Settings.
```

The service is enabled in Settings but no live instance is available. Common
causes are an app update, an OEM kill, or a service crash.

1. Open **Settings** → **Accessibility** → **T9 Network Reset**.
2. Turn it off, wait a second, and turn it on again.
3. Return to the app; the chip should read `ACCESSIBILITY ON`.
4. Check battery optimization and OEM autostart.
5. Reboot if the service still does not connect.

Avoid force-stopping the app: Android may keep the accessibility service down
until you reopen the app. If Quick Settings fails but Settings works, the
service is connected; the problem is tile matching, not the service.

## Exact alarm denied

On Android 12+:

1. **SETUP** → **Exact alarms** → **OPEN**.
2. Enable **Alarms & reminders** for T9 APK Tools.
3. Return to the app; the pill should read `ON`.
4. Re-arm the timer.

If the system screen does not list the app, open **Settings** → **Apps** → **T9
APK Tools** → **Alarms & reminders** (the path varies by OEM). If exact access
was revoked after a timer was armed, re-arm it after granting access.

Without exact access, `ResetScheduler` uses `setAndAllowWhileIdle()`. The timer
still runs, but Doze can delay it. See [Timer fires late](#timer-fires-late).

## Timer fires late

Possible causes, in order of likelihood:

1. **Exact alarms are off.** Grant them and re-arm.
2. **Doze or an OEM battery manager deferred the alarm.** Exempt the app and
   allow autostart.
3. **The service was killed and the alarm was not delivered.** Check battery,
   OEM settings, and whether the app was force-stopped.
4. **The exact-alarm permission was revoked after arming.** Re-grant it and
   re-arm.
5. **The phone clock or time zone was changed.** `RTC_WAKEUP` uses wall-clock
   time; a large manual change can move the target relative to the current
   time. Re-arm after changing the clock.

A short test interval is more likely to be affected by while-idle throttling
than the 15-minute default. If the target passed while the phone was off,
`BootReceiver` runs the reset immediately on boot.

## Notification missing

The timer can run without notifications, but you lose the countdown and the
result.

1. **Settings** → **Apps** → **T9 APK Tools** → **Notifications**.
2. Enable **All notifications**.
3. Open the channels and enable **Reset countdown**, **Reset results**, and
   **Attention needed**.
4. On Android 13+, confirm the app has the notification runtime permission.
5. If an OEM "battery saver" hides background notifications, allow the app in
   that manager too.

The countdown channel is intentionally low importance, so it does not make a
sound. It should still appear as a persistent notification while a timer is
armed.

## ADB grant failures

Use the exact command:

```bash
adb shell pm grant dev.t9.apktools android.permission.WRITE_SECURE_SETTINGS
```

Common mistakes and fixes:

- **Running it inside `adb shell`:** then the command is
  `pm grant dev.t9.apktools android.permission.WRITE_SECURE_SETTINGS`, without
  the leading `adb shell`.
- **Wrong package:** confirm with
  `adb shell pm list packages | grep t9`. The release package is
  `dev.t9.apktools`; a debug build uses `dev.t9.apktools.debug`.
- **Wrong user or work profile:** `pm grant` applies to the current user. Use
  `--user <id>` if the app is installed for a different user, or run the
  command while that profile is active.
- **Phone not authorized:** `adb devices` must show `device`, not
  `unauthorized` or `offline`. Unlock the phone and accept the debugging
  prompt.
- **MIUI/Redmi/POCO:** enable **USB debugging (Security settings)** in
  Developer options in addition to normal USB debugging. MIUI may block
  development permission grants otherwise.
- **OEM policy:** some managed devices and some manufacturer builds block
  `pm grant` for development permissions. Wireless debugging or a different
  engine may be the only option.
- **App not installed for the user:** Android may report that the permission is
  not changeable. Reinstall the app in the current user, then grant again.
- **Grant did not stick:** verify with the `dumpsys` command in
  [SETUP.md](SETUP.md#verify-the-grant), then revoke and re-grant.

If the command reports `Error: java.lang.SecurityException` or "not allowed",
include the exact output and the Android/OEM version in a report. Do not paste
your device serial number or account details.

## OEM background restrictions

These skins add their own app-killing controls on top of Android:

- **Samsung:** Settings → **Battery and device care** → **Battery** →
  **Background usage limits**. Remove T9 APK Tools from **Sleeping apps** and
  **Deep sleeping apps**, and turn off **Put unused apps to sleep** if you need
  reliable timers. Some builds also have **Auto disable unused apps**.
- **Xiaomi / Redmi / POCO:** Security app → **Permissions** → **Autostart** →
  enable T9 APK Tools. Then Settings → **Apps** → **Manage apps** → T9 APK
  Tools → **Battery saver** → **No restrictions**. Lock the app in the recents
  screen if your build supports it.
- **Oppo / Realme / OnePlus:** Settings → **Battery** → **App battery
  management** (or **Battery optimization**) → T9 APK Tools → **Allow
  background activity**. Check **Startup manager** / **Auto-start** too.
- **Vivo / iQOO:** Settings → **Battery** → **Background power consumption
  management** → allow T9 APK Tools. Check **Auto-start** in the app manager.
- **Other skins:** look for **Autostart**, **Startup**, **Background activity**,
  **Sleeping apps**, or **Battery optimization** and set the app to
  unrestricted.

Labels change between versions. Test with a 5-minute timer after changing a
setting. If the OEM still kills the service, the Direct engine plus a charger
is the most reliable combination, but it is still not a guarantee.

## Wireless debugging

Wireless debugging needs Android 11+ and both devices on the same network.

1. Phone: **Developer options** → **Wireless debugging** → on.
2. Phone: **Pair device with pairing code**. Note the pairing port.
3. Computer:

   ```bash
   adb pair 192.168.1.50:37123
   # enter the six-digit code
   ```

4. Phone: note the IP and port on the main Wireless debugging screen. This is
   the connect port, not the pairing port.
5. Computer:

   ```bash
   adb connect 192.168.1.50:5555
   adb devices
   ```

6. Grant:

   ```bash
   adb shell pm grant dev.t9.apktools android.permission.WRITE_SECURE_SETTINGS
   ```

If pairing fails:

- Disable VPNs on both sides.
- Confirm both devices are on the same subnet, not a guest network with client
  isolation.
- Toggle Wireless debugging off/on; the ports change.
- Try `adb kill-server` then `adb start-server`.
- Use USB as a fallback. The grant is the same.

The connect port can change after a reboot, so `adb connect` may need to be run
again. The grant itself survives reboots.

## App installed but no launcher icon

The manifest does declare a launcher activity
(`dev.t9.apktools.MainActivity`), so a missing icon is not intentional.

1. Search the app drawer for **T9 APK Tools**; some launchers hide sideloaded
   apps from the main page but still list them in search.
2. Open **Settings** → **Apps** → **T9 APK Tools** → **Open**. If this works,
   the app is installed and only the launcher entry is missing.
3. Check whether you are in a work profile. Apps installed in a work profile
   appear in that profile's app drawer, not the personal one. Install and grant
   in the profile you actually use.
4. Check that the app is not disabled: **Settings** → **Apps** → **T9 APK
   Tools** → **Enable**.
5. Restart the launcher or reboot the phone to clear a launcher cache.
6. Reinstall the APK if the package is missing for the current user.

Optional ADB check:

```bash
adb shell cmd package resolve-activity --brief dev.t9.apktools
```

A healthy install resolves to `dev.t9.apktools/.MainActivity`. No output usually
means the package is not installed for that user.

## Phone stayed locked

The accessibility engines check the keyguard before they start:

- They wait up to 90 seconds.
- They post the high-importance **Unlock to finish the reset** notification.
- If the keyguard is still locked, they return `Phone stayed locked. Unlock it
  and run again.`

Direct does not check the keyguard; it writes the secure setting with the screen
off or locked, though some OEMs block that write in the background. The Data
engine does not need the screen at all. For the accessibility engines, unlock
the phone when the timer is due, enable **Wake screen and wait for unlock**, or
run the reset while the phone is already unlocked. If the screen wake option is
off, the phone may not light up to show the notification.

## Network did not come back in 60 s

The toggle and restore completed, but `IpUtil.waitForInternet()` did not see a
network with `NET_CAPABILITY_INTERNET` and Airplane mode off within 60 seconds.

Check:

- Mobile data is enabled and the SIM is registered.
- The status bar does not still show Airplane mode.
- The carrier is not in an outage.
- A VPN or private DNS setting is not blocking connectivity.
- The hotspot clients are not the only network path.

Turn Airplane mode off manually if it is stuck, wait for mobile data, and run
**RESET NOW** again. If the modem is slow, a longer Airplane pause can help the
next run.

## Airplane mode is still on

The safety restore failed. Do this first:

1. Turn Airplane mode off manually from Quick Settings or Settings.
2. Confirm Wi-Fi/mobile data returns.
3. Open the **ACTIVITY** log and note which engine was used.
4. If Direct is granted but the write is ignored, switch to the accessibility
   engine.
5. If accessibility was disconnected, reconnect it and check battery
   restrictions.

The app tries Direct first for the restore, then the Settings UI, but it cannot
override an OEM that refuses both. If the process was killed mid-run, no
in-process restore can run. See
[Safety restore](HOW-IT-WORKS.md#safety-restore).

The Data engine does not touch Airplane mode at all. If a Data run fails, check
mobile data instead: the log should end with `Mobile data is reconnecting`. A
watchdog in the shell process re-enables data if the app died during the hold.

## Could not read the public IP

`IpUtil.fetch()` returns null if all three endpoints fail or return something
that is not an IP literal.

- Open a browser and check that the phone has internet.
- Try the refresh action on the **PUBLIC IP** card.
- Disable VPN, private DNS, or a firewall that blocks the endpoints.
- Check whether `api.ipify.org`, `icanhazip.com`, and `ifconfig.me` are
  reachable from the phone.
- Turn off **Verify public IP before and after** if you only care about the
  toggle.

The toggle can succeed while the IP check fails. In that case the result is
"IP check unavailable", not proof that the IP did not change. See
[PRIVACY.md](PRIVACY.md#network-calls).

## Quick Settings tap hits the wrong tile

The calibrated fallback taps a percentage of the screen. If the layout changed,
it can hit another tile.

1. Open **SETUP** → **QUICK SETTINGS CALIBRATION**.
2. Put the Airplane tile in the top-left of the expanded shade.
3. Adjust **Tile X position** and **Tile Y position** to the center of the
   tile. Defaults are 12% and 20%.
4. Test with **RESET NOW** while watching the shade.
5. If the device rotates or changes resolution, check the coordinates again.
6. If calibration is unreliable, use **Accessibility — Settings UI** or
   **Direct** instead.

A wrong tap can toggle a different tile, such as Wi-Fi or Bluetooth. Check those
states if the reset behaved strangely. The app only verifies the Airplane state,
so it cannot detect that another tile was hit.

## Direct grant is present but the write is rejected

The log says `Direct write was rejected by the system`, or the setting reads back
unchanged.

- Verify the grant with the `dumpsys` command in
  [SETUP.md](SETUP.md#verify-the-grant). `GRANTED` in the app means
  `checkSelfPermission` passed; it does not prove the firmware honors the write.
- Reboot and try once more.
- Try the accessibility engine. Some OEM builds accept UI clicks but ignore
  direct secure-setting writes.
- Check whether the app is running in a work profile with a device policy that
  blocks secure settings.
- Report the device model, Android build, and exact log line. This is
  device-specific behavior, not something the app can force.

## Shizuku shows NOT CONNECTED

The Setup pill checks two things: `Shizuku.pingBinder()` (or `Sui.isSui()`) and
`Shizuku.checkSelfPermission()`.

- `NOT CONNECTED` means the binder is dead: Shizuku is not installed, was
  stopped, or the phone rebooted. Start the Shizuku app and wait for its own
  status to say it is running.
- `GRANT NEEDED` means Shizuku is running but this app has no grant. Tap
  **CONNECT SHIZUKU** and allow the system dialog.

On Android 11 and newer, Shizuku can be started on the phone through wireless
debugging. On older builds it needs a computer and the start command shown in
its app. Root and Sui survive a reboot; a plain non-rooted install does not.

If the pill still says `NOT CONNECTED` while Shizuku reports running:

1. Open Shizuku and confirm its own service is alive.
2. Force-stop T9 APK Tools and reopen it. The binder is handed over when the app
   process starts.
3. Reboot, start Shizuku, then open T9 APK Tools.
4. Check battery restrictions for both apps; some OEMs kill Shizuku in the
   background.

A Data run with a dead binder stops before touching anything. Auto skips Data
and uses the next available engine instead.

## Mobile data command failed

The Data engine runs two commands inside a Shizuku user service:

```text
/system/bin/cmd phone data disable
/system/bin/svc data disable
```

`cmd phone data` exists on Android 11 and newer. `svc data` is a legacy entry
point that some Android 11–13 builds ship broken and some Android 14+ builds ship
working. The app tries `cmd phone` first and falls back to `svc`.

A failure looks like:

```text
Mobile data would not switch off (fail:cmd=1,svc=1)
```

What to do:

- Confirm the Shizuku pill is `READY`; a missing grant fails earlier with a
  different message.
- Try **RESET NOW** once more. A bind timeout is transient.
- If both exit codes are non-zero, this build does not expose either command to
  the shell UID. Use the Direct engine or an accessibility engine.
- Report the device model, Android build, and the exact `fail:` line. This is
  build-specific behavior.

The engine always runs the enable path before it gives up, and a watchdog in the
shell process turns data back on if the app dies during the hold. Mobile data
should not be left off by a failed cycle.

## IP stayed the same after a data reset

The cycle ran, but the carrier kept the mapping. This is common under CGNAT.

1. Read the log: `Mobile data off` and `Mobile data is reconnecting` prove the
   cycle ran; `Public IP after … (unchanged)` is the verdict.
2. Lengthen **Airplane mode stays on** to 8–12 seconds. The Data engine uses it
   as the mobile-data hold, with a floor of 4 seconds.
3. Retry. A fresh data context does not always get a new public address.
4. Turn off VPNs before testing; otherwise the app sees the VPN exit.
5. Compare the before and after lines, not only the badge.
6. If several cycles keep the same address, the carrier is holding the session.
   The app cannot force a change.

The Data engine does not touch Wi-Fi or the hotspot, so a same-IP result is not
caused by the hotspot dropping.

## Collecting a useful report

Include:

- app version from the **SETUP** footer;
- Android version, device model, and OEM skin;
- the engine selected in **SETUP**;
- whether the ADB grant is present and the output of the `dumpsys` check;
- the Shizuku pill state (`READY`, `GRANT NEEDED`, or `NOT CONNECTED`);
- whether accessibility is on and connected;
- whether the phone was unlocked and the screen on;
- if the app closed by itself, reopen it and use **Last launch crashed** →
  **Copy log** (`last-crash.txt`); background failures are in `last-failure.txt`
  and can be viewed by long-pressing the footer build label;
- the exact **ACTIVITY** lines around the failure;
- whether a hotspot was active, and whether hotspot restore was on;
- steps to reproduce, including the timer duration and whether it was a manual
  reset.

Redact public IP addresses, device serial numbers, and anything else you do not
want public. Open a normal issue at
[github.com/engrtarun/t9-apk-tools/issues](https://github.com/engrtarun/t9-apk-tools/issues).
For a security problem, use the private process in
[SECURITY.md](../SECURITY.md), not a public issue.
