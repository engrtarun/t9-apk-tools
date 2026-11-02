# Changelog

All notable changes to T9 APK Tools are recorded here. The project follows
[Semantic Versioning](https://semver.org/).

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
