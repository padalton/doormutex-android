# doormutex for Android

A native Android app with home-screen widgets that show the state of every
[doormutex](https://github.com/padalton/doormutex) light at a glance – without
opening anything.

**Status:** not started yet. Scope, stack and milestones are in
[padalton/doormutex#28](https://github.com/padalton/doormutex/issues/28); the hub
prerequisites (read-only tokens, multi-profile event stream, QR pairing) are in
[padalton/doormutex#27](https://github.com/padalton/doormutex/issues/27).

## Principles

- **Widget first** – a glance at the home screen, not an app to open.
- **Read-only by default** – paired with a token that cannot change anything.
- **Never show an old state as current** – stale data says since when.
- **Nothing leaves the home network** – no account, no cloud, no analytics,
  no Google Play Services dependency.

## Development

Kotlin, Jetpack Compose, Jetpack Glance, minSdk 26. You need JDK 21 and the
Android SDK (`ANDROID_HOME`), or Android Studio.

```sh
./gradlew assembleDebug   # build
./gradlew test            # unit tests
./gradlew installDebug    # install on a connected phone (adb devices)
```

## Licence

Code under [Apache-2.0](LICENSES/Apache-2.0.txt), documentation under
[CC-BY-SA-4.0](LICENSES/CC-BY-SA-4.0.txt). The repository follows
[REUSE](https://reuse.software), see `REUSE.toml`.
