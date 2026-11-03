# Website-Empfänger und Dashboard

Nimmt die Push-Snapshots der Mod entgegen, speichert sie als Minutenwerte (SQLite) und zeigt sie in einem passwortgeschützten Dashboard. Keine Abhängigkeiten, nur Node >= 22.13 (`node:sqlite`).

## Was es tut
- `POST /fla/ingest`: prüft Zeitstempel (max. 5 Minuten Abweichung) und HMAC-SHA256 über den **rohen** Body, antwortet `204`. Body max. 1 MB.
- `GET /fla/`: Dashboard (Login per Passwort).
  - **Zeiträume:** 5 min, 15 min, 30 min, 1 h, 2 h, 3 h, 6 h, 12 h, 24 h, 3 d, 7 d, 14 d, 30 d, Alles (alle gespeicherten Daten, höchstens 40 Tage).
  - **Quellen und Items** per Kästchen wählbar, mehrere Quellen im selben Diagramm (gleiche Items werden addiert). Gleich benannte Items aus verschiedenen Mods zeigen den Namensraum in Klammern.
  - **Größe:** Net, In, Out pro Minute oder Bestand.
  - **Zeitachse auf Daten zuschneiden** (Standard an): der leere Bereich vor dem ersten Messwert wird abgeschnitten, junge Daten nutzen die ganze Breite.
  - **Diagramm:** scharf auf hochauflösenden Bildschirmen, weiche Flächen unter den Linien, gestrichelte Hilfslinien mit runden Achsenwerten (Null auf einer Rasterlinie), Zeiten ohne Messung als rot schraffierte Bereiche "nicht gemessen", Fadenkreuz mit Punkten und Tooltip (Farbmarkierung, nach Wert sortiert) beim Überfahren.
  - **Statuszeile** oben rechts: "Letzter Empfang: N s her"; wird rot, wenn länger als 5 Minuten nichts ankam oder noch nie etwas.
- Pro Quelle, Item und Minute gibt es einen Wert. Minuten ohne Daten haben **keine** Zeile: Das Diagramm zeigt dort eine Lücke, nie eine 0. Ohne gemessene Zeit im Fenster (`coveredSeconds = 0`) werden keine Raten gespeichert.
- Gleiche Items aus mehreren Quellen werden addiert (wie in der Mod).
- Unter dem Diagramm: Tabelle mit Letzter Wert, Min, Ø, Max und gemessenen Minuten je Item; **CSV herunterladen** exportiert, was das Diagramm zeigt.
- Aufbewahrung `RETENTION_DAYS` (Standard 30).

## Demo ohne Minecraft
```
cd website && node tools/seed-demo.js     # http://127.0.0.1:8792/fla/ , das zufällige Passwort steht in der Konsole (nur lokal)
```
Füllt eine temporäre Datenbank mit künstlichen Daten (3 Quellen, 20 Minuten Ausfall) und beendet sich nach 20 Minuten.

## Konfiguration (Umgebungsvariablen)
| Variable | Bedeutung |
|---|---|
| `FLA_SECRET` | Gleiches Secret wie `push.secret` in der Mod-Config (mind. 16 Zeichen). Ohne gültiges Secret antwortet `ingest` mit 503. |
| `FLA_DASHBOARD_PASSWORD` | Passwort fürs Dashboard (mind. 12 Zeichen). Ohne gültiges Passwort ist der Login gesperrt. |
| `FLA_BASE` | URL-Präfix, Standard `/fla` |
| `PORT`, `BIND` | Standard `8780`, `127.0.0.1` |
| `DATA_DIR` | Datenbankordner, Standard `./data` |
| `TRUST_PROXY` | `1` hinter Caddy/nginx (nutzt `X-Forwarded-For` und `-Proto`) |

Die Werte gehören in eine Datei außerhalb des Repos (`.env` mit Rechten 600), nie in Git.

## Betrieb (so läuft es auf dem Server)
```
docker run -d --name fla-receiver --restart unless-stopped \
  -p 127.0.0.1:8780:8780 --env-file /opt/fla-receiver/.env \
  -v /opt/fla-receiver/app:/app:ro -v /opt/fla-receiver/data:/data -w /app \
  --user 1000:1000 --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges \
  --memory 256m --pids-limit 100 node:24-alpine node server.js
```
Caddy, im Block der Website:
```
handle /fla/* {
    reverse_proxy 127.0.0.1:8780
}
```
Nach Änderungen an der `.env`: `docker restart fla-receiver`.

## Mod-Config (`[push]`)
```
enabled = true
url = "https://<deine-domain>/fla/ingest"
secret = "<dasselbe wie FLA_SECRET>"
intervalSeconds = 30
```

## Dateien
| Datei | Aufgabe |
|---|---|
| `server.js` | Routen (`/ingest`, Login, Dashboard, `/api/*`), Sitzungen, Sicherheits-Header |
| `lib/verify.js` | Zeitstempel- und HMAC-Prüfung |
| `lib/store.js` | SQLite: Minutenwerte, Summe über Quellen, Abfrage mit Bucket-Größe |
| `public/app.js`, `public/style.css` | Dashboard (Canvas-Diagramm ohne Fremdbibliothek) |
| `tools/seed-demo.js` | Lokale Demo mit künstlichen Daten |
| `test/receiver.test.js` | 6 Node-Tests |

## Tests
```
cd website && node --test test/receiver.test.js
```
6 Tests: Signatur, Zeitstempel, Größenlimit, Login und Sperre, Lücken ohne Nullwerte, Summe über Quellen. Zusätzlich wurde der echte Java-Code der Mod (`PushHttp`) gegen den Empfänger geprüft: richtiges Secret `204`, falsches `401`.

## Sicherheit
- Dashboard-Antworten mit Content-Security-Policy (nur eigene Skripte), `HttpOnly`/`SameSite=Strict`-Cookie, `Secure` hinter HTTPS.
- Login: nach 5 Fehlversuchen 15 Minuten Sperre pro IP.
- Container ohne Rechte, Dateisystem schreibgeschützt (nur `/data` beschreibbar), nur auf `127.0.0.1` erreichbar.
