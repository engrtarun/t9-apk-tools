# Security Policy

## The accessibility service is powerful

T9 APK Tools uses an Android `AccessibilityService` for two of its three
engines. While that service is enabled, Android allows it to inspect window
content and perform clicks and gestures. That is a broad capability, and it is
the reason the service is optional.

The app limits itself to the reset flow:

- It reads the active window tree only while a reset is running.
- It looks for the Airplane mode switch or tile and the controls needed to
  reach it.
- It performs clicks and, for Quick Settings calibration, a gesture at a stored
  screen position.
- It does not type text, read notifications, capture screenshots, or send
  window content anywhere. See [docs/PRIVACY.md](docs/PRIVACY.md).

The Android system still grants the service broad access. Enable it only if you
are comfortable with that trade-off, and turn it off when you no longer need
the app. You can revoke it at any time in Settings > Accessibility.

## Signing key: read this before installing

The release keystore is committed to this repository as
`keystore/t9-release.jks.b64`, and the password is in the workflow. This is
intentional. It is a **public community signing key** for reproducible sideload
updates: every build from this repository can update an earlier install without
asking users to uninstall first.

It is not a private key and it is not a Play Store key.

Consequences:

- Anyone with the repository contents can sign an APK that Android will accept
  as an update to the community build. The signature proves the APK came from
  this key, not that a particular person built it.
- Do not reuse this key for another app, a Play Store listing, or anything that
  depends on the key being secret.
- If you need a private signing identity, generate your own keystore and build
  your own APK. Android will not install it over the community build unless you
  uninstall first, which deletes local settings and logs.
- Verify the signer SHA-256 in `download/version.json` against the certificate
  shown by `apksigner verify --print-certs` if you want to confirm which key
  signed a download.

Never commit a private keystore, a Play Store upload key, or a signing password
that is meant to stay secret.

## Reporting a vulnerability

Do not open a public issue for a security problem.

Use GitHub's private vulnerability reporting for this repository: open the
**Security** tab and choose **Report a vulnerability**. If that option is not
available, open a normal issue that asks for a private contact channel and do
not include technical details, personal data, or exploit steps.

A useful report includes:

- Affected version and where it was downloaded.
- Android version, device model, and OEM skin.
- The engine and steps involved.
- What an attacker could do, and what access they need.
- A minimal reproduction, if one is safe to share.

Reports are handled on a best-effort basis. There is no bug bounty. Please give
the maintainer reasonable time to fix the issue before publishing details.

## Supported versions

| Version | Supported |
| --- | --- |
| 1.0.x | Yes |
| Older or unreleased builds | No |

Security fixes are made on the latest release. The app supports Android 8.0
(API 26) through Android 14 (API 34) as its declared range. Devices outside
that range may work but are not promised.

## Scope

In scope:

- The app's Java code, manifest, exported components, and accessibility
  service behavior.
- The GitHub Actions workflow and release/Pages publishing process.
- Handling of logs, IP addresses, and accessibility window data.
- Signature and update behavior.

Out of scope:

- Carrier network behavior, CGNAT, and whether a reset changes your public IP.
- Device firmware, OEM restrictions, and rooting or modifying the device.
- The availability or correctness of api.ipify.org, icanhazip.com, or
  ifconfig.me.
- Someone with physical access to an unlocked device enabling the
  accessibility service.
