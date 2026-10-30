# HomeLab Monitor (Android)

Native Android-App (Kotlin, Jetpack Compose, Material 3) zur Überwachung des Heimnetzwerks:

| Gerät / Dienst | Adresse (Standard) | Zugriff | Was wird angezeigt |
|---|---|---|---|
| Ubuntu Server (Lenovo ThinkCentre) | 192.168.19.5 | SSH | OS, Kernel, Uptime, Load, RAM, Disk, Temperatur, apt-Updates, Neustart nötig, Docker-Container |
| OpenMediaVault 8 (Raspberry Pi) | 192.168.19.50 | SSH | OMV-Version, Pi-Modell, Datenträger unter /srv, RAID, apt-/OMV-Updates, Temperatur |
| Immich (Docker auf Ubuntu) | http://192.168.19.5:2283 | REST-API | Version, neueste Version (GitHub), Speicher, Fotos/Videos, Container-Status |
| Pi-hole v6 (Docker auf Ubuntu) | http://192.168.19.5 | REST-API | Core/Web/FTL/Docker-Version + Updates, Blocking-Status, Statistik, Gravity |

**Aktionen:** apt update / upgrade, `omv-upgrade`, Neustart, Docker-Container aktualisieren
(`docker compose pull && docker compose up -d`) bzw. neu starten, Pi-hole-Blocking pausieren/aktivieren, Gravity-Update.

**Hintergrundprüfung:** WorkManager prüft alle X Stunden (nur im WLAN) und meldet Updates, nötige Neustarts
oder ausgefallene Dienste per Benachrichtigung.

---

## 1. Installation

### Variante A – fertige APK
`HomelabMonitor.apk` aufs Handy kopieren → öffnen → „Aus dieser Quelle installieren“ erlauben.

### Variante B – selbst bauen
* **Android Studio** (Ladybug oder neuer): Ordner öffnen → *Run*.
* **Kommandozeile:** `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
* **GitHub Actions:** Repo pushen – der Workflow `.github/workflows/build-apk.yml` baut die APK automatisch.

> Die Release-APK ist mit dem Debug-Key signiert (privater Gebrauch). Für eine Veröffentlichung eigenen Keystore verwenden.

---

## 2. Vorbereitung der Server

### Ubuntu Server (192.168.19.5)
```bash
# eigener Benutzer für die App (optional, empfohlen)
sudo adduser homelab
sudo usermod -aG docker homelab        # Docker-Status ohne sudo lesen
```
Für **Aktionen** (Updates, Neustart) braucht der Benutzer sudo-Rechte. Zwei Möglichkeiten:

1. **SSH-Passwort in der App hinterlegen** – die App nutzt es automatisch für `sudo -S` (empfohlen, keine Server-Änderung nötig).
2. **SSH-Key ohne Passwort** – dann muss sudo ohne Passwort funktionieren (`sudo -n`), z. B.:
   ```bash
   echo 'homelab ALL=(ALL) NOPASSWD: ALL' | sudo tee /etc/sudoers.d/homelab && sudo chmod 440 /etc/sudoers.d/homelab
   ```
   (Das gibt dem Benutzer volle Root-Rechte ohne Passwort – nur mit Key-Login und nur im LAN verwenden.)

Nur zum **Lesen** des Status reichen normale Benutzerrechte (+ Gruppe `docker` für die Container-Liste).

### OpenMediaVault 8 (192.168.19.50)
1. OMV-Weboberfläche → **Dienste → SSH** aktivieren.
2. **Benutzer → Benutzer**: Benutzer anlegen/bearbeiten und Gruppen **`_ssh`** und **`sudo`** zuweisen.
3. In der App Benutzer + Passwort (oder SSH-Key) eintragen. Updates laufen über `omv-upgrade` (offizieller Weg).

### Immich
* **API-Key:** Immich → Avatar → *Kontoeinstellungen* → *API-Schlüssel* → neuen Schlüssel erstellen
  (für Statistiken muss es ein **Admin**-Konto sein bzw. die Berechtigung `server.statistics` haben).
* **Compose-Verzeichnis:** Ordner mit der `docker-compose.yml` von Immich, z. B. `/opt/immich`.
* Container-Name ist standardmäßig `immich_server`.

### Pi-hole v6
* **URL** inkl. Port, unter dem das Web-Interface im Docker veröffentlicht ist (z. B. `http://192.168.19.5:8080`
  wenn ihr `8080:80` gemappt habt).
* **Passwort:** Admin-Passwort oder besser ein **App-Passwort** (*Settings → Web interface / API → Configure app password*).
* In Docker ist das Pi-hole-Update = neues Image ziehen → Aktion „Container aktualisieren“ (Compose-Verzeichnis eintragen).

---

## 3. Sicherheit
* Alle Zugangsdaten werden mit **EncryptedSharedPreferences** (Android Keystore, AES-256) gespeichert; App-Backup ist deaktiviert.
* SSH-Host-Keys werden im Heimnetz **nicht** gegen known_hosts geprüft. Die App ist für den Einsatz im eigenen LAN gedacht –
  Server nicht ungeschützt ins Internet stellen. Von unterwegs am besten per VPN (WireGuard/Tailscale) zugreifen.
* Klartext-HTTP ist erlaubt, weil Immich/Pi-hole im LAN meist ohne TLS laufen.
* SSH-Keys: OpenSSH- oder PEM-Format (RSA, ECDSA, Ed25519), ohne Passphrase.

---

## 4. Projektstruktur
```
app/src/main/java/de/heimnetz/monitor/
├── MainActivity.kt
├── data/
│   ├── HomelabRepository.kt   # Abfragen (SSH-Skripte, Immich-/Pi-hole-API) + Aktionen
│   ├── SshClient.kt           # JSch-Wrapper inkl. sudo/docker-Helfer
│   ├── Settings.kt            # Konfiguration, verschlüsselt gespeichert
│   ├── Models.kt              # Status, Aktionen, Versionsvergleich
│   ├── JsonExt.kt / Format.kt
├── ui/                        # Compose: Dashboard, Detail, Einstellungen, Theme
└── work/UpdateCheckWorker.kt  # Hintergrundprüfung + Benachrichtigung
```

## 5. Erweitern
Neues Gerät hinzufügen: in `HomelabRepository.placeholders()` + `collectAll()` eine weitere Abfrage ergänzen,
Felder in `AppConfig`/`SettingsScreen` hinzufügen, Aktionen in `ServiceAction.forService()` eintragen.
