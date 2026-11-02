# Community signing key

`t9-release.jks.b64` is the **public community signing key** for sideload builds of T9 APK Tools.

- Alias: `t9`
- Store/key password: `t9-apk-tools`
- Certificate SHA-256:
  `1B:9B:BE:11:98:91:75:C4:63:C7:C9:EC:38:35:23:3F:4A:12:46:D2:DA:CA:73:C9:CC:6A:76:72:44:3F:57:06`
- Valid until: 2054-03-20

The key is committed on purpose so GitHub Actions can sign every build without repository
secrets, and so a new APK installs as an update over an older one. **It is not a Play Store
key and must never be used for another app.** Anyone with this repository can sign an APK
that Android will accept as an update to this package; the site publishes the SHA-256 of
every release so you can verify what you downloaded.

To verify a downloaded APK:

```bash
apksigner verify --print-certs t9-apk-tools.apk
```

The signer digest must match the fingerprint above and the value in
`site/download/version.json` / the GitHub Actions run summary.

To build with your own key, set `T9_KEYSTORE_PATH`, `T9_KEYSTORE_PASSWORD`,
`T9_KEY_ALIAS`, and `T9_KEY_PASSWORD` before running Gradle. Never commit a private
production key.
