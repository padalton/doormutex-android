# doormutex for Android

A native Android app with home-screen widgets that show the state of every
[doormutex](https://github.com/padalton/doormutex) light at a glance – without
opening anything.

**Status:** milestones 1–4 done; first closed test on Google Play. The original plan – scope, stack,
milestones – is [padalton/doormutex#28](https://github.com/padalton/doormutex/issues/28);
what is left is listed below. The hub side
(read-only tokens, multi-profile event stream, QR pairing) is
[padalton/doormutex#27](https://github.com/padalton/doormutex/issues/27), merged in
[#29](https://github.com/padalton/doormutex/pull/29).

## Principles

- **Widget first** – a glance at the home screen, not an app to open.
- **Read-only by default** – paired with a token that cannot change anything.
- **Never show an old state as current** – stale data is dimmed and says since when.
- **Nothing leaves the home network** – no account, no cloud, no analytics,
  no Google Play Services dependency.

## What works

- Pairing by QR code: the hub's web UI (*Settings → Phones and tokens → Add a
  phone*) shows `doormutex://pair?hub=…&token=…&name=…`; the app scans it
  (CameraX + ZXing, no Play Services) or opens it as a link, e.g. from the
  system camera. By hand as a fallback. Either way the app checks
  `GET /api/v1/profiles` and `GET /api/v1/me` and shows the profiles and
  whether the token is read-only before anything is saved. HTTPS by default;
  plain HTTP only for private addresses, after a warning.
- Overview: one card per profile with lamp, status, reason, until, next free
  slot and message. Live while open (`GET /api/v1/events?profile=*`), pull to
  refresh; a hub before API 0.2.0 is polled every 30 s instead.
- Detail screen per profile: the next 24 hours (`/timeline`), the e-ink
  picture (`/eink.png`) and – for full tokens only – the switches Red, Red for
  1 h, Green, Off and Automatic (`PUT`/`DELETE /override`, as the shortcuts in
  the main repository's `docs/android.md`).
- Widget "doormutex light" (1×1 up to 2×2), one profile per widget, chosen
  when it is placed and changeable later; tap opens the profile.
- Widget "doormutex – all lights" (3×2 and up): one row per profile.
- Live mode (setting, on by default): a foreground service keeps the event
  stream open while the phone is on Wi-Fi, Ethernet or a VPN and redraws the
  widgets on every change, with a quiet notification "doormutex · live". On
  mobile data it waits without any traffic. Resumes after a reboot and an
  update. Off, or away from home: WorkManager refreshes every 15 minutes.
- Stale handling: hub unreachable or data older than twice the refresh
  interval → dimmed, with "since 14:32". While live, the hub's keep-alive
  every 25 s counts as confirmation.
- English and German, wording as in the hub and the Plasma widget.

Next:

- **M2 follow-up:** measure the battery cost of live mode over a day on a real
  phone; push (UnifiedPush / Web Push from the hub) only if it is too high.
- **After the alpha:** production on Google Play, a link from the main
  repository's `docs/android.md` and doormutex.org, F-Droid if wanted (no Play
  Services, no dependency metadata, store texts in `fastlane/` already).

## Privacy and security

- The token is encrypted with an AES key in the Android Keystore and never
  logged. App data is excluded from cloud backup and device transfer.
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE` and, on Android 17+,
  `ACCESS_LOCAL_NETWORK`. The latter is a runtime permission ("nearby
  devices"); without it Android silently blocks every connection to the hub,
  so the app asks for it before the first check and explains it otherwise.
  Live mode adds `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`
  (`dataSync` is limited to 6 hours a day since Android 15),
  `POST_NOTIFICATIONS` (asked once; live mode works without it, the
  notification just stays hidden) and `RECEIVE_BOOT_COMPLETED`. `CAMERA` is
  asked for only when the QR scanner opens.
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
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
                          # Compose UI tests; pin the device – AGP uninstalls
                          # the app afterwards, pairing and widgets included
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

## Release

Releasing on Google Play – keys, tracks, store listing, data safety and the
foreground service declaration – is described in
[`docs/play-console.md`](docs/play-console.md). In short: a tag
`v<versionName>` builds a signed AAB (for Play) and APK (GitHub release) in
CI; locally, `./gradlew bundleRelease` with the `DOORMUTEX_KEYSTORE…`
variables set. Privacy policy: [`PRIVACY.md`](PRIVACY.md).

## Licence

Code under [Apache-2.0](LICENSES/Apache-2.0.txt), documentation under
[CC-BY-SA-4.0](LICENSES/CC-BY-SA-4.0.txt). The repository follows
[REUSE](https://reuse.software), see `REUSE.toml`.
