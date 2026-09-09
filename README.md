# Ardoise

An unofficial native Android client for [Spliit](https://spliit.app), the open
source app for splitting expenses with other people, with no account to create
and nobody to sign up to.

[![CI](https://github.com/fmaruejol/ardoise/actions/workflows/ci.yml/badge.svg)](https://github.com/fmaruejol/ardoise/actions/workflows/ci.yml)

*L'ardoise* is the running tab you settle later, which is the thing this app
keeps. The name is its own: it is not Spliit's, and nothing here speaks for the
Spliit project.

> **Unofficial.** This is not affiliated with, endorsed by, or maintained by
> the Spliit project. Upstream ships a web client and an iOS client; this is a
> third client written against the same server, so anything wrong with it is
> wrong with *this*, not with Spliit.

It is a port rather than a wrapper: no WebView, no shared cross-platform layer,
and the settlement arithmetic is a line-by-line port of upstream's, with
upstream's own test suites, because two clients that disagree would show two
different balances to people in the same group.

## Privacy

Spliit has no accounts, so there is no profile, no email address and no
password anywhere in this app. What follows from that is the whole of the
privacy story:

- **The only server it talks to is the one you choose.** It defaults to
  `spliit.app`, and the first screen lets you point it at your own instance
  instead. Nothing is mirrored anywhere else.
- **One exception, and it is narrow.** Entering an expense in a currency the
  group is not counted in fetches that day's rate from
  [Frankfurter](https://frankfurter.dev), the same source upstream's web
  client uses, so both fill the field the same way. It is asked once when you
  pick the currency, and again only if you tap for a fresh rate. It receives a
  date and two currency codes: no amount, no group, no identifier.
- **Which participant you are never leaves the device.** Spliit has no field
  for it on the server, so it lives in local preferences, per group. It is
  what marks a balance as yours.
- **No analytics, no crash reporting, no advertising, no tracking.** There is
  no SDK for any of it. The dependencies are AndroidX and Compose, Ktor,
  kotlinx.serialization, Koin, Room, CameraX and zxing-cpp.
- **QR codes are read on the device.** Camera frames go to zxing-cpp in the
  app's own process; no image or frame is uploaded anywhere.
- **Request logging exists only in debug builds.** A request URL carries the
  group id, and the group id is the only credential Spliit has.
- **Closed-source components are avoided** where there is a choice: QR
  scanning uses zxing-cpp rather than ML Kit, so the app builds from source.

### What is stored, and where

Two local stores, split by whether losing it would cost you anything:

- **A cache** (Room) holds only what can be fetched again: the groups, their
  expenses, balances, activity and categories. It is never written to first
  and never treated as the truth, so it is safe to lose.
- **Preferences** (DataStore) hold what cannot be fetched again: your group
  ids, which participant you are in each group, the server address, and your
  language, theme and default currency.
- **Expenses typed offline** live in a third, durable database until they are
  sent, because those are the one thing the server has never seen.

**None of it is backed up to the cloud.** A group id is the only credential
Spliit has, so letting Android back one up would put it on somebody else's
server: `allowBackup` is off, and the extraction rules exclude cloud backup
again for Android 12 and up, where that flag no longer covers both halves.
Device-to-device transfer is left on, since it moves your groups straight to
the phone replacing this one and never touches a server. Keep your group links
somewhere anyway: they are the only way back into a group.

## What it does

- Join a group by link or by scanning its QR code, or create one
- The expenses feed, by day, with the group's total and your own share
- What each expense did to your balance, not just what your share of it was
- Add and edit expenses: 44 categories, notes, split evenly, by amount, by
  shares or by percentage
- Expenses in another currency, converted at the rate for that day
- Repeating expenses, daily, weekly or monthly
- Balances, suggested payments, and settling up by recording a reimbursement
- Totals: what the group spent, and who put it in
- The group's activity log
- Group settings, participants, and a QR code to invite people
- **Works offline**: every screen reads from the cache, and an expense typed
  with no network is queued where you can see it and sent when you say so
- Light, dark or the system's theme; English and French

### What it does not do

**Receipt scanning and attachments are the server's, and it does not offer a
client a way in.** Spliit reads receipts server-side and stores documents on
the instance's own S3; the extraction is a framework-internal action with no
endpoint, the feature flags are never sent to a client, and the upload route
belongs to a third-party library rather than to Spliit's own API. Reading
receipts on the device instead would mean two clients reading one receipt two
different ways, which is the disagreement this app exists to avoid. Both stay
absent until upstream exposes a contract.

## Installing

There is no Play Store or F-Droid listing. Releases are built by GitHub
Actions from a tag and published here, as a signed APK attached to the
[latest release](https://github.com/fmaruejol/ardoise/releases/latest).

**With [Obtainium](https://github.com/ImranR98/Obtainium)** — recommended,
because it checks for new releases and updates the app the way a store would:

1. Add app → paste `https://github.com/fmaruejol/ardoise`.
2. Obtainium finds `ardoise-<version>.apk` on each release.

Or, on the phone itself, one tap: [add to Obtainium](obtainium://app/%7B%22id%22%3A%22io.github.fmaruejol.ardoise%22%2C%22url%22%3A%22https%3A//github.com/fmaruejol/ardoise%22%2C%22author%22%3A%22fmaruejol%22%2C%22name%22%3A%22Ardoise%22%7D).
The link only works on a device that has Obtainium installed.

**By hand** — download the APK from a release and open it. Android will ask
you to allow installing from your browser or file manager. Updates are then
yours to check for, which is the reason Obtainium is worth the extra app.

Every release is signed with the same key, so an update installs over the
previous version and keeps your data. If you ever see Android refuse an update
with a signature error, something is wrong: stop and open an issue rather than
uninstalling, because uninstalling loses your group ids and those cannot be
recovered.

## Requirements

- Android 8.0 (API 26) or newer
- A Spliit instance, `spliit.app`, or your own

## Building

```
./gradlew assembleDebug             # the app
./gradlew testDebugUnitTest         # :app JVM tests, no device needed
./gradlew test                      # :core and :api JVM tests
./gradlew connectedDebugAndroidTest # instrumented, needs a device
./gradlew lint                      # Android Lint
./gradlew spotlessCheck             # ktlint and the Compose rules
```

Everything but `connectedDebugAndroidTest` runs without an emulator, and
`.github/workflows/ci.yml` runs all of it on every push and pull request.

`assembleRelease` works from a clean clone and produces an **unsigned** APK:
the release key reaches the build only through `KEYSTORE_PATH` and its three
companion variables, so nothing in this repository can sign a release and
nothing here needs to. `.github/workflows/release.yml` supplies them from
repository secrets when a `v*` tag is pushed, and the version comes from the
tag rather than from `build.gradle.kts`.

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET` | Talking to the instance you chose. |
| `ACCESS_NETWORK_STATE` | Knowing the phone has lost signal, so a screen can say the rows came from the cache rather than sit there looking current. |
| `CAMERA` | Scanning a group's QR code, and asked for only when you open the scanner. The camera is declared optional, so the app installs on a device without one. |

On a build where `INTERNET` can be revoked per app (GrapheneOS, for one), the
app detects that it has been and says so, rather than blaming your
connection.

## Layout

```
:app    Compose UI, navigation, ViewModels, DI wiring
:core   Domain models, settlement, formatting. Pure Kotlin/JVM
:api    tRPC client, superjson, DTOs. Pure Kotlin/JVM
```

`CLAUDE.md` is the working notes: the API's sharp edges, how money is
represented, and the decisions that are settled.

## Licence

[Apache License 2.0](LICENSE), for this client. Spliit itself is a separate
project under its own licence, and this borrows nothing from it but the shape
of its API.
