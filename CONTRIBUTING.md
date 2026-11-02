# Contributing

Thanks for helping improve T9 APK Tools. The project is small on purpose:
native Java, no root, no account system, and no telemetry. Changes that keep it
that way are easiest to review.

## Start with an issue

Open an issue before a large change. Bug reports and feature requests use the
issue templates. Small fixes, documentation corrections, and obvious build
repairs can go straight to a pull request.

Search existing issues first. If you are unsure whether something is a bug,
describe the device, Android version, OEM, engine, and what you expected.

## Keep pull requests small

- One concern per pull request.
- Explain what changed and why.
- Include the testing you actually did.
- Do not mix formatting-only changes with behavior changes.
- Do not modify `app/`, `site/`, or `art/` for a documentation-only change.

## Permissions and dependencies

Do not add a permission without a concrete justification in the pull request.
The current permission set is documented in [docs/PRIVACY.md](docs/PRIVACY.md).
New permissions, services, receivers, or network endpoints need a clear reason
and a privacy note.

Avoid new third-party libraries unless the platform cannot reasonably do the
job. The release build currently uses AndroidX AppCompat, AndroidX Core, and
Material Components only. No analytics, ads, crash-reporting SDKs, or tracking
libraries are accepted.

## Build and test

The supported build path is GitHub Actions, not a local machine:

1. Fork the repository.
2. Enable Actions on the fork.
3. Run the `Build APK & Deploy Pages` workflow, or push to your fork's `main`.
4. Download the `t9-apk-tools-apk` artifact from the run.

The workflow needs `keystore/t9-release.jks.b64`, which is committed. Forks
inherit it. Do not replace it with a personal keystore in a pull request.

If you prefer a local build, install JDK 17 and Gradle 8.8. There is no Gradle
wrapper in the repository, so use the `gradle` command:

```bash
gradle :app:assembleRelease --no-daemon
```

The release build reads these environment variables:

| Variable | Purpose |
| --- | --- |
| `T9_KEYSTORE_PATH` | Path to the JKS file |
| `T9_KEYSTORE_PASSWORD` | Keystore password |
| `T9_KEY_ALIAS` | Key alias |
| `T9_KEY_PASSWORD` | Key password |

Do not commit `local.properties`, generated build output, or a real private
keystore. `.gitignore` already covers those paths.

## Test on a real device

UI automation cannot be validated in an emulator alone. Before opening a pull
request that touches an engine, test on a physical phone and note:

- Android version, device model, and OEM skin.
- Which engine you tested.
- Whether the screen was on, off, or locked.
- Whether the public IP changed, stayed the same, or could not be read.
- Whether the hotspot survived the reset.

A change that works on one OEM can fail on another. Say what you tested rather
than claiming universal support.

## Code style

- Java 17, four-space indentation, braces on the same line.
- Keep user-facing strings in `res/values/strings.xml`.
- Prefer small methods and clear names over comments that restate the code.
- Do not add wildcard imports.
- Keep `minSdk` compatibility in mind; guard newer APIs by version.
- Preserve the existing package name `dev.t9.apktools`.
- Do not reformat unrelated files.

## Documentation

Update the relevant document when behavior, setup, permissions, or limitations
change:

- `README.md` for the product overview.
- `docs/HOW-IT-WORKS.md` for engine and Android behavior.
- `docs/SETUP.md` for installation and permissions.
- `docs/PRIVACY.md` for data handling.
- `SECURITY.md` for signing and vulnerability reporting.

## Commits and licensing

Use short imperative commit messages, for example `Fix QS tile lookup on
Samsung`. By contributing, you agree that your contribution is licensed under
the MIT License in [LICENSE](LICENSE).

## Never commit secrets

The committed keystore is the public community key described in
[SECURITY.md](SECURITY.md). That is the only signing material that belongs in
the repository. Do not add a private keystore, a Play Store key, an API token,
or a password that is meant to remain secret. If you accidentally commit a
secret, report it privately rather than opening a public issue.
