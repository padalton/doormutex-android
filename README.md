# doormutex for Android

A native Android app with home-screen widgets that show the state of every
[doormutex](https://github.com/padalton/doormutex) light at a glance – without
opening anything.

**Status:** milestone 1 (read and show) done. The original plan – scope, stack,
milestones – is [padalton/doormutex#28](https://github.com/padalton/doormutex/issues/28);
the next milestones are tracked in this repository's issues. The hub side
(read-only tokens, multi-profile event stream, QR pairing) is
[padalton/doormutex#27](https://github.com/padalton/doormutex/issues/27), merged in
[#29](https://github.com/padalton/doormutex/pull/29).

## Principles

- **Widget first** – a glance at the home screen, not an app to open.
- **Read-only by default** – paired with a token that cannot change anything.
- **Never show an old state as current** – stale data is dimmed and says since when.
- **Nothing leaves the home network** – no account, no cloud, no analytics,
  no Google Play Services dependency.

## What works (M1)

- Pairing by manual entry: hub address + token, checked against
  `GET /api/v1/profiles` and `GET /api/v1/me` (shows the profiles and whether
  the token is read-only). HTTPS by default; plain HTTP only for private
  addresses, after a warning.
- Overview: one card per profile with lamp, status, reason, until, next free
  slot and message. Polls every 30 s while open, pull to refresh.
- Widget "doormutex light" (1×1 up to 2×2), one profile per widget, chosen
  when it is placed and changeable later. Refreshed every 15 minutes by
  WorkManager and whenever the app is open; tap opens the app at that profile.
- Stale handling: hub unreachable or data older than twice the refresh
  interval → dimmed, with "since 14:32".
- English and German, wording as in the hub and the Plasma widget.

Not yet: live updates (SSE, M2), the "all lights" widget (M2), QR pairing and
override buttons (M3).

## Privacy and security

- The token is encrypted with an AES key in the Android Keystore and never
  logged. App data is excluded from cloud backup and device transfer.
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE` and, on Android 17+,
  `ACCESS_LOCAL_NETWORK`. The latter is a runtime permission ("nearby
  devices"); without it Android silently blocks every connection to the hub,
  so the app asks for it before the first check and explains it otherwise.
- User-installed CAs are trusted, so a hub with a private CA works.

## Development

You need JDK 21 and the Android SDK (`ANDROID_HOME`), or Android Studio.
Package / application id: `net.padalton.doormutex.android` (debug builds get
the suffix `.debug`).

```sh
./gradlew assembleDebug   # build
./gradlew test            # unit tests
./gradlew ktlintCheck lintDebug
./gradlew installDebug    # install on a connected phone (adb devices)
```

A local test hub from the main repository:

```sh
cd hub && go build -o /tmp/dm-hub ./cmd/doormutex-hub
/tmp/dm-hub token create phone --read-only     # with DOORMUTEX_CONFIG set
/tmp/dm-hub serve -config config.yaml
```

From the emulator the PC is `http://10.0.2.2:<port>`. For a phone on USB,
`adb reverse tcp:<port> tcp:<port>` and use `http://127.0.0.1:<port>` –
loopback counts as private, so no firewall changes are needed.

`app/src/test/resources/api/state-example.json` is the `State` example from the
hub's `api/openapi.yaml`; copy it again when the API changes.

## Licence

Code under [Apache-2.0](LICENSES/Apache-2.0.txt), documentation under
[CC-BY-SA-4.0](LICENSES/CC-BY-SA-4.0.txt). The repository follows
[REUSE](https://reuse.software), see `REUSE.toml`.
