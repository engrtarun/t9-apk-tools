# Engineering methods behind T9 APK Tools

T9 APK Tools is a solo-built, native-Java Android utility. There is no server,
no analytics SDK, no cloud database, and no JavaScript framework in the shipped
product. This note records the engineering methods used to build it and points
to the file where each claim can be checked. It is written for technical due
diligence, not for marketing.

## Methods and evidence

| Skill / method | What it enforced | Evidence in this repository |
| --- | --- | --- |
| Spec-driven development | Requirements were fixed before code: no root, three reset engines, offline history, one SQLite file, a signed APK built in CI, and a static download site. | `README.md` (scope and limits), `app/src/main/AndroidManifest.xml` (permissions and components), `T9Database.java` (schema). |
| Planning and task breakdown | Work was sliced into shippable releases instead of one large drop: 1.0.0 (timer and engines), 1.1.0 (history and IP comparison), 1.1.1 (launch recovery and release hardening). | `CHANGELOG.md`, `VERSION`. |
| Incremental implementation | Each change stayed small enough to build and inspect on its own; release behaviour was never mixed with site-only edits in the same decision. | `CHANGELOG.md` entries; the workflow builds from `VERSION`, not from an unrelated commit counter (`app/build.gradle`). |
| Verification-first development | Every change had to pass a local debug build and a local release build. The release APK is verified with `apksigner` before upload, and the published checksum is computed from the exact file the site serves. | `.github/workflows/android.yml` (`Verify APK signature`, `Prepare Pages payload`), `site/download/version.json` generated per build. |
| Debugging and error recovery | A release that dies before the first frame must still produce evidence. The release build is unminified, fatal traces are written to `last-crash.txt`, non-fatal background failures to `last-failure.txt`, and `MainActivity` draws a recovery screen instead of closing if startup fails. | `App.java` (`installCrashHandler`, `noteFailure`, `runIo`), `MainActivity.java` (`showStartupFailure`, `reportLastCrash`), `app/build.gradle` (`minifyEnabled false`). |
| Code review and quality | Independent audits covered the database, the Pages pipeline, the documentation, the download section, and the service/history code. Findings were triaged; release-blocking ones were fixed and the rest were explicitly deferred. | `T9Database.java`, `.github/workflows/android.yml`, `site/app.js`, `site/styles.css`; deferred items listed below. |
| Performance optimization | History is append-only and bounded. Retention runs every 32 writes, the incremental vacuum every 128, every read uses a `LIMIT`, and no disk access happens on the UI thread. | `T9Database.java` (`KEEP_EVENTS`, `KEEP_RUNS`, `KEEP_CHECKS`, `writeCount`), `App.runIo`. |
| Security and hardening | The app requests no account, advertising, or analytics permission. Cleartext traffic is disabled, exported components are minimised, and the committed signing key is documented as a community key, not a Play key. | `AndroidManifest.xml`, `res/xml/network_security_config.xml`, `keystore/README.md`, `docs/PRIVACY.md`. |
| Frontend and UI engineering | The download site is hand-written HTML, CSS, and vanilla JavaScript. Motion is opacity/transform only, tap targets meet 44 px, reduced-motion is honoured, and the build manifest is loaded from the same directory as the APK. | `site/index.html`, `site/styles.css`, `site/app.js`. |

## How the work was parallelised

Implementation ran alongside read-only audits: one each for the SQLite schema,
the GitHub Pages pipeline, the documentation, the download-section UX, and the
service/history code. The audits produced concrete findings with file and line
references. Release-blocking findings were fixed before tagging; the remainder
were written down rather than silently dropped.

Deliberately deferred to the next release:

- a live upload/download speed readout on the main screen;
- carrier-behaviour analytics (verified-change rate, wait-time ladder, failure
  reason counts) built from the existing `run` table;
- extra `run` columns for transport (Wi-Fi vs mobile) and pause length.

## What is deliberately not used

- No Electron, React, or web runtime inside the APK.
- No Python in the shipped product; Python appears only in the CI packaging
  step that writes `version.json`.
- No cloud database, sync service, or account system.
- No advertising, analytics, or crash-reporting SDK.
- No background-job framework beyond Android's own alarm, service, and
  broadcast APIs.

## Reproducibility

The build is defined by `VERSION`, `app/build.gradle`, and
`.github/workflows/android.yml`. A pushed tag equal to `v$(cat VERSION)` builds
the APK, verifies its signature, publishes the GitHub Release, and refreshes the
Pages payload from the same binary. The site never invents a build number: it
reads `version.json` generated by that run.

_Prepared for technical due diligence. Every claim above maps to a file in this
repository; no external service is required to reproduce the build._
