# Privacy policy – doormutex for Android

*Deutsch weiter unten.*

doormutex for Android (`net.padalton.doormutex.android`) shows the state of
doormutex lights from a doormutex hub that you run yourself in your home
network.

## What the app processes

- **Hub address and token** that you enter or scan. They are stored only on
  your phone; the token is encrypted with a key in the Android Keystore. They
  are excluded from cloud backups and device transfers.
- **The state of your lights** (status, reason, times, messages, the e-ink
  picture), fetched from your hub and cached on the phone so the widgets can
  show it.
- **Camera images** only while the QR scanner is open, only on the phone, to
  read the pairing code. Nothing is stored or sent.

## Where data goes

Only to and from **your own hub**, at the address you entered. The app has no
server of its own, no account, no advertising, no analytics, no crash
reporting and no third-party SDKs that collect data. The developer receives
nothing.

## Permissions

Network access and "nearby devices" (to reach the hub in your home network),
camera (QR scanner only), notifications (the "doormutex · live" notification
of live mode) and start at boot (to resume live mode).

## Deleting data

*Settings → Disconnect from the hub* removes address, token and cached state.
Uninstalling the app removes everything. Revoke the token in the hub's web UI
to cut access on the hub side as well.

## Contact

Questions: <https://github.com/padalton/doormutex-android/issues>

---

# Datenschutzerklärung – doormutex für Android

doormutex für Android (`net.padalton.doormutex.android`) zeigt den Zustand von
doormutex-Ampeln aus einem doormutex-Hub, den du selbst in deinem Heimnetz
betreibst.

## Was die App verarbeitet

- **Hub-Adresse und Token**, die du eingibst oder scannst. Sie bleiben nur auf
  deinem Handy; der Token ist mit einem Schlüssel im Android Keystore
  verschlüsselt. Sie sind von Cloud-Backups und Geräteumzug ausgenommen.
- **Der Zustand deiner Ampeln** (Status, Grund, Zeiten, Nachrichten, das
  E-Ink-Bild), von deinem Hub geholt und am Handy zwischengespeichert, damit
  die Widgets ihn zeigen können.
- **Kamerabilder** nur, solange der QR-Scanner offen ist, nur am Handy, um den
  Kopplungscode zu lesen. Nichts wird gespeichert oder gesendet.

## Wohin Daten gehen

Nur zu und von **deinem eigenen Hub**, unter der Adresse, die du angegeben
hast. Die App hat keinen eigenen Server, kein Konto, keine Werbung, keine
Analyse, keine Absturzberichte und keine Drittanbieter-SDKs, die Daten
sammeln. Der Entwickler bekommt nichts.

## Berechtigungen

Netzwerk und „Geräte in der Nähe“ (um den Hub im Heimnetz zu erreichen),
Kamera (nur der QR-Scanner), Benachrichtigungen (die Benachrichtigung
„doormutex · live“ des Live-Modus) und Start beim Hochfahren (um den
Live-Modus fortzusetzen).

## Daten löschen

*Einstellungen → Vom Hub trennen* entfernt Adresse, Token und
zwischengespeicherten Zustand. Deinstallieren entfernt alles. Widerrufe den
Token in der Web-Oberfläche des Hubs, um auch dort den Zugriff zu beenden.

## Kontakt

Fragen: <https://github.com/padalton/doormutex-android/issues>
