# Privacy

T9 APK Tools is a local utility. There is no account, no analytics service, no
advertising SDK, and no project-operated backend. The app does not sell or share
data, and it does not try to identify you.

This note describes what the current app (`dev.t9.apktools`, version 1.0.x)
actually does. It is a plain-language engineering note, not a legal compliance
statement.

## Short version

- No accounts, ads, analytics, telemetry, or crash reporting.
- The app's own network calls go only to three public-IP endpoints over HTTPS.
- Logs and settings stay in the app's private storage on the phone.
- The log holds the last 120 entries and can be cleared in the app.
- Accessibility window content is inspected only while a reset runs. It is not
  stored and not transmitted.
- The optional ADB grant can be revoked, and uninstalling removes the app, its
  data, and the grant.
- There is no Play Store listing and no server-side copy of anything.

## What the app does not collect

The app has no code for:

- user accounts, sign-in, or cloud sync;
- advertising or ad identifiers;
- analytics, event tracking, or session recording;
- crash reporting or remote logging;
- contacts, SMS, call logs, calendar, photos, camera, microphone, or location;
- clipboard reading (the Setup screen only writes the ADB command to the
  clipboard when you tap **COPY COMMAND**);
- background screenshot capture or screen recording.

The app declares only AndroidX AppCompat, AndroidX Core, and Material
Components. There is no analytics or ads dependency in `app/build.gradle`.

## Network calls

The only endpoints the app contacts are:

| Endpoint | Purpose | Method |
| --- | --- | --- |
| `https://api.ipify.org` | Read the current public IP | HTTPS GET |
| `https://icanhazip.com` | Fallback public-IP read | HTTPS GET |
| `https://ifconfig.me/ip` | Second fallback | HTTPS GET |

`IpUtil.fetch()` tries them in that order and returns the first valid answer.
Each request has a 5-second connect timeout and a 5-second read timeout. The app
sets `User-Agent: T9-APK-Tools/1.0` and `Accept: text/plain`. There are no query
parameters, API keys, account tokens, or device identifiers in the request.

The remote server necessarily sees the source IP address and standard HTTP
headers. Those three services are independent third parties; their own privacy
policies apply. The app does not control their logging, retention, or
availability. A request can fail, be blocked, or return an unexpected address.

If **Verify public IP before and after** is turned off, a reset makes no IP
requests. The **refresh** action on the main screen and the manual check still
use the endpoints. Opening the project's GitHub Pages link or a GitHub issue
opens your browser; that traffic is between your browser and GitHub, not the
app's networking code.

## Data stored on the phone

Everything is in the app's private `SharedPreferences` files. No external
storage, no database server, and no cloud backup is requested.

| Data | Location | Why | Retention |
| --- | --- | --- | --- |
| Preferences: duration, engine, off delay, Quick Settings percentages, repeat, wake screen, IP verification, hotspot restore, armed target time | `t9_prefs` | Remember your setup and restore a timer | Until you clear data or uninstall |
| Last result: success flag, changed flag, message, before/after IP, timestamp | `t9_prefs` | Show the result card after a run | Until replaced, cleared, or uninstalled |
| Activity log: timestamp, severity, message, engine, addresses, errors | `t9_log` (JSON array) | Let you see what happened | Last 120 entries; clearable |
| One-time prompt flags: notification asked, accessibility hint shown | `t9_prefs` | Avoid repeating the same dialog | Until you clear data or uninstall |

The manifest sets `android:allowBackup="false"`, so the app does not ask Android
to back up its preferences or logs.

The log is a ring buffer: `LogStore.add()` removes the oldest entry when the
array exceeds 120. The main screen renders the newest 10; the stored log still
holds up to 120. **CLEAR** writes `[]` to the log. **RESET ALL SETTINGS & LOGS**
clears both preferences and the log. Android's **Clear storage** does the same
at the system level.

Public IP addresses are not account identifiers, but they can identify a
network or a household. Treat them as potentially personal. If you paste a log
into an issue, redact the addresses and any other details you do not want
public.

## Accessibility service

The service is optional and is controlled by you in Android Settings. When it
is enabled, Android grants it the ability to inspect window content and perform
clicks and gestures. That is a broad capability.

The app's use of it is narrow:

- `onAccessibilityEvent()` is empty. The service does not react to events, does
  not monitor what you type, and does not keep a window cache.
- Traversal happens synchronously only while a reset is running: it reads the
  active window and interactive windows to find a control whose text or content
  description contains `airplane` or `flight mode`, or to find the hotspot
  control.
- It performs clicks, `ACTION_SELECT`, and a short gesture at calibrated Quick
  Settings coordinates.
- It does not type text, read notifications, take screenshots, record the
  screen, or send window content anywhere.
- Node data is held in memory for the duration of the operation and is not
  written to preferences, the log, or a file.

Android still gives the enabled service broad access, and the app cannot change
that. Enable it only if you are comfortable with the trade-off, and turn it off
when you are not using the app. See
[SETUP.md](SETUP.md#5-enable-the-accessibility-service) and
[SECURITY.md](../SECURITY.md).

## Permissions and why they exist

| Permission | Reason | Optional? |
| --- | --- | --- |
| `INTERNET` | HTTPS calls to the public-IP endpoints | Required for IP checks; the toggle still works without a successful check |
| `ACCESS_NETWORK_STATE` | Wait for connectivity after the toggle | Required for the connectivity wait |
| `FOREGROUND_SERVICE` | Run the countdown and reset as a foreground service | Required |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14 foreground-service type for the timer | Required on Android 14+ |
| `POST_NOTIFICATIONS` | Countdown, result, and unlock notifications on Android 13+ | Optional; the timer still runs |
| `SCHEDULE_EXACT_ALARM` | Exact alarm at the target time on Android 12+ | Optional; inexact fallback |
| `WAKE_LOCK` | Screen wake and partial wake lock during a reset | Required for the current wake behavior |
| `RECEIVE_BOOT_COMPLETED` | Re-arm a future timer after a reboot | Required for boot restore |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ask you to exempt the app from battery optimization | Optional; the app asks, it does not silently exempt itself |
| `WRITE_SECURE_SETTINGS` | Direct engine writes `Settings.Global.AIRPLANE_MODE_ON` | Optional; only after a manual ADB grant |

The accessibility service declaration uses
`android.permission.BIND_ACCESSIBILITY_SERVICE`. That is a system binding
permission on the service, not a permission the app requests from you. Android
binds it only after you enable the service.

The app does not request location, phone state, contacts, storage, camera, or
microphone permissions.

## Deletion

You can remove data in several ways:

1. **Clear the log:** main screen → **ACTIVITY** → **CLEAR**.
2. **Reset app settings and log:** **SETUP** → **DANGER ZONE** → **RESET ALL
   SETTINGS & LOGS**. The ADB grant and Android permissions remain.
3. **Clear all app data:** Android **Settings** → **Apps** → **T9 APK Tools** →
   **Storage & cache** → **Clear storage**.
4. **Revoke the ADB grant:**

   ```bash
   adb shell pm revoke dev.t9.apktools android.permission.WRITE_SECURE_SETTINGS
   ```

5. **Uninstall:** removes the app, its preferences, its logs, and the ADB grant.
   There is no server copy to delete.

## Security and distribution

The app uses the platform's standard HTTPS validation for the three endpoints.
It does not implement certificate pinning. It does not hide traffic from the
carrier, a VPN, or the operating system.

Release builds are signed with a public community key committed to the
repository. That makes reproducible sideload updates possible, but the
signature proves the key, not a specific builder. Read
[SECURITY.md](../SECURITY.md) before installing or redistributing a build.

The app is not distributed through the Play Store. Download it from the
project's GitHub Pages or GitHub Releases, and compare the published SHA-256 if
you want to verify a file.

## Contact

For ordinary questions and non-sensitive bug reports, open an issue at
[github.com/engrtarun/t9-apk-tools/issues](https://github.com/engrtarun/t9-apk-tools/issues).
Do not post personal data, public IP addresses, or other sensitive details in a
public issue.

For a security problem, use GitHub's private vulnerability reporting as
described in [SECURITY.md](../SECURITY.md). Do not open a public issue with
exploit details.

## Changes

This note is kept with the app's documentation. If the app's data handling
changes, the note should change in the same pull request. The current behavior
described here is for version 1.0.x. Documentation corrections are welcome via
the same issue tracker.
