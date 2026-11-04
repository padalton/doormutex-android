# Google Play: releasing doormutex

Everything the Play Console asks for, in the order it asks. The texts and
graphics are in [`fastlane/metadata/android`](../fastlane/metadata/android)
(`en-US`, `de-DE`).

## Keys

- **Upload key:** a PKCS12 keystore outside the repository, alias `upload`,
  RSA 4096, valid until 2054. Keep a copy of the keystore and its password in
  a password manager. If it is lost, Google can reset the upload key (Play
  Console → *Test and release → App integrity*); the app itself stays the
  same.
- **App signing key:** choose *Let Google manage and protect your app signing
  key* (Play App Signing) on the first upload. Google re-signs what Play
  delivers, so an APK from GitHub releases (signed with the upload key) and
  the Play app cannot update each other – uninstall to switch.

Build locally:

```sh
source ~/.android-keys/doormutex-upload.env   # DOORMUTEX_KEYSTORE… variables
./gradlew bundleRelease                        # app/build/outputs/bundle/release/app-release.aab
```

Or from CI: put the four values into the repository secrets once, then push
a tag `v<versionName>`; the *Release* workflow attaches the AAB to the run and
publishes the APK as a GitHub pre-release.

The values come from `~/.android-keys/doormutex-upload.env`, written when the
key was created; keystore and key share one password. Nothing needs to be
copied by hand:

```sh
source ~/.android-keys/doormutex-upload.env
R=padalton/doormutex-android
base64 -w0 "$DOORMUTEX_KEYSTORE" | gh secret set DOORMUTEX_KEYSTORE_BASE64 -R $R
printf %s "$DOORMUTEX_KEYSTORE_PASSWORD" | gh secret set DOORMUTEX_KEYSTORE_PASSWORD -R $R
printf %s "$DOORMUTEX_KEY_ALIAS" | gh secret set DOORMUTEX_KEY_ALIAS -R $R
printf %s "$DOORMUTEX_KEY_PASSWORD" | gh secret set DOORMUTEX_KEY_PASSWORD -R $R
```

The Play Console itself never asks for this password.

Every upload needs a higher `versionCode` (`app/build.gradle.kts`).

## Closed testing (alpha)

*Test and release → Testing → Closed testing → Alpha → Create release*:
upload the AAB, release notes from `changelogs/<versionCode>.txt` (en-US and
de-DE). Testers join through the opt-in link on the *Testers* tab, with the
Google account that is on the list.

## Store listing

| Field | File |
|---|---|
| App name | `title.txt` |
| Short description | `short_description.txt` |
| Full description | `full_description.txt` |
| App icon (512×512) | `images/icon.png` |
| Feature graphic (1024×500) | `images/featureGraphic.png` |
| Phone screenshots | `images/phoneScreenshots/` |

Category: *Tools* (or *House & Home*). Contact e-mail: required, shown in the
store.

## App content

- **Privacy policy:** `https://github.com/padalton/doormutex-android/blob/main/PRIVACY.md`
- **Ads:** no ads.
- **App access:** *All or some functionality is restricted.* Instructions for
  review: "doormutex shows status lights from a doormutex hub, self-hosted
  hardware/software in the user's home network
  (github.com/padalton/doormutex). There are no accounts or logins. Without a
  hub the app shows the pairing screen; the widgets say 'Open the app to
  connect'."
- **Content rating:** questionnaire, category *Utility, productivity,
  communication or other*; everything *no*.
- **Target audience:** 18 and over keeps the Families policy out of scope;
  the users are the adults who set up the hub.
- **Data safety:** the app sends nothing to the developer or any third party;
  it only talks to the user's own hub at an address the user enters. Answer
  *Does your app collect or share any of the required user data types?* →
  **No**; *Is all of the user data collected by your app encrypted in
  transit?* is then not asked. If Google treats traffic to a user-run server
  as collection, the honest fallback is: *App activity → Other actions*
  (switching the light), collected, not shared, processed ephemerally, not
  optional only for full tokens, purpose *App functionality*; encrypted in
  transit only when the hub uses HTTPS.
- **Advertising ID:** the app does not use it.
- **Foreground service permissions:** `FOREGROUND_SERVICE_SPECIAL_USE`.
  Declaration text:
  > Live mode keeps one connection to the user's own doormutex hub open while
  > the phone is on Wi-Fi, so the home-screen widgets show the status light
  > (do not disturb / come in) the moment it changes. The user turns it on or
  > off in the settings; a persistent low-priority notification
  > "doormutex · live" is shown while it runs. On mobile data the service
  > stays idle. Deferring the work (WorkManager, at least 15 minutes) would
  > show an outdated state, e.g. "come in" while a video call has started.

  Video (required): a short screen recording that shows a widget change while
  the app is closed and the notification – see
  `fastlane/live-mode-demo.mp4`; upload it unlisted to YouTube and paste the
  link.
- **Nearby devices (`ACCESS_LOCAL_NETWORK`):** if the Console asks, the
  reason is that the hub is a device in the user's local network.

## After the alpha

Production needs the same declarations; Google may require 12 testers for 14
days on a new personal developer account before production access.
