# Website-Betrieb (Empfänger und Dashboard)

Stand: Mod-Version 1.1.6. Beschreibt, wie die Website-Anbindung aufgebaut ist, was auf dem Server liegt und wie man sie betreibt. Der Code liegt in [`website/`](../website/README.md), das Datenformat in [API.md](API.md). **Zugangsdaten stehen nirgends in diesem Repo.**

## Überblick
```
Minecraft-Server (Hoster)                      Eigener Server (VPS)
  Mod: PushHttp                                  Caddy (HTTPS, Let's Encrypt)
  alle 30 s  ──HTTPS POST /fla/ingest──────────► reverse_proxy 127.0.0.1:8780
  Header X-FLA-Timestamp, X-FLA-Signature          Docker-Container "fla-receiver" (Node 24, SQLite)
                                                   ▲
Browser ──HTTPS /fla/ (Login) ────────────────────┘  Dashboard
```
- Die Mod sendet nur aus (Push). Der Minecraft-Hoster braucht keinen offenen Port.
- Der Empfänger prüft Zeitstempel (max. 5 Minuten Abweichung) und HMAC über den rohen Body, speichert **einen Wert pro Quelle, Item und Minute** und zeigt sie im Dashboard.
- Minuten ohne Push haben keine Zeile: Das Diagramm zeigt eine Lücke ("nicht gemessen"), nie eine 0.

## Was auf dem Server liegt
| Pfad / Objekt | Inhalt |
|---|---|
| `/opt/fla-receiver/app/` | `server.js`, `lib/`, `public/` (aus `website/`), im Container schreibgeschützt eingehängt |
| `/opt/fla-receiver/data/` | SQLite-Datenbank `fla.sqlite` (gehört UID 1000) |
| `/opt/fla-receiver/.env` | `FLA_SECRET`, `FLA_DASHBOARD_PASSWORD` u. a., Rechte 600, nur root |
| `/opt/fla-receiver/backup/` | Sicherung der ursprünglichen `index.html` der Startseite |
| Container `fla-receiver` | Image `node:24-alpine`, Port nur `127.0.0.1:8780`, Neustart automatisch |
| `/etc/caddy/Caddyfile` | Zusätzlicher Block `handle /fla/* { reverse_proxy 127.0.0.1:8780 }` im Site-Block der Hauptdomain |
| `/etc/caddy/Caddyfile.bak-fla-<Zeitstempel>` | Sicherung des Caddyfiles vor der Änderung |
| `/var/www/homepage/index.html` | Ein zusätzlicher Navigationslink "Analytics" nach `/fla/` |

Andere Dienste auf dem Server (z. B. NocoDB/Postgres, Xonotic) wurden nicht verändert.

## Container (Härtung)
Läuft mit `--user 1000:1000 --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges --memory 256m --pids-limit 100`. Beschreibbar ist nur `/data`. Nur über `127.0.0.1` erreichbar, öffentlich ausschließlich über Caddy.

Starten (genau so wurde er angelegt):
```
docker run -d --name fla-receiver --restart unless-stopped \
  -p 127.0.0.1:8780:8780 --env-file /opt/fla-receiver/.env \
  -v /opt/fla-receiver/app:/app:ro -v /opt/fla-receiver/data:/data -w /app \
  --user 1000:1000 --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges \
  --memory 256m --pids-limit 100 node:24-alpine node server.js
```

## Häufige Aufgaben
### Passwort oder Secret ändern
1. `/opt/fla-receiver/.env` bearbeiten.
2. **Container neu anlegen**, nicht nur neu starten: Docker liest `--env-file` nur beim Anlegen (`docker rm -f fla-receiver`, dann der `docker run`-Befehl oben). Ein `docker restart` übernimmt die Änderung **nicht**.
3. Beim Secret zusätzlich `push.secret` in der Minecraft-Server-Config angleichen und den Minecraft-Server neu starten.
Passwort mindestens 12, Secret mindestens 16 Zeichen, sonst bleiben Login bzw. Annahme gesperrt (Log-Warnung beim Start).

### Empfänger aktualisieren
1. Geänderte Dateien aus `website/` nach `/opt/fla-receiver/app/` hochladen (SFTP).
2. `docker restart fla-receiver` genügt, solange nur Code geändert wurde.
3. Prüfen: `https://<domain>/fla/health` liefert `{"status":"ok"}`.

### Logs und Zustand
- `docker logs --tail 50 fla-receiver`
- `docker ps --filter name=fla-receiver`
- Empfangene Daten: im Dashboard oben rechts "Letzter Empfang: N s her".

### Sicherung und Wiederherstellung
- Daten: Ordner `/opt/fla-receiver/data/` kopieren (am besten bei gestopptem Container).
- Caddy zurücksetzen: `cp /etc/caddy/Caddyfile.bak-fla-<Zeitstempel> /etc/caddy/Caddyfile && caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile && systemctl reload caddy`.
- Startseite zurücksetzen: `/opt/fla-receiver/backup/index.html.bak-fla-20261103` nach `/var/www/homepage/index.html` kopieren.

### Aufbewahrung
`RETENTION_DAYS` (Standard 30) in der `.env`. Löschen läuft beim Start und alle 6 Stunden.

## Einschalten in Minecraft
Server-Config `config/factoryledgeranalytics-common.toml`, Abschnitt `[push]`:
```
enabled = true
url = "https://<domain>/fla/ingest"
secret = "<dasselbe wie FLA_SECRET>"
intervalSeconds = 30
```
Danach Minecraft-Server neu starten. Ein erfolgreicher Push schreibt nichts ins Log; Fehler stehen als `Push failed (Nx): …`.

## Fehlersuche
| Beobachtung | Ursache / Maßnahme |
|---|---|
| `ingest` antwortet 503 | `FLA_SECRET` fehlt oder ist kürzer als 16 Zeichen. `.env` prüfen, Container neu anlegen |
| `ingest` antwortet 401 | Secret stimmt nicht überein, oder die Uhr von Minecraft-Server und VPS weicht mehr als 5 Minuten ab |
| Login nicht möglich, Seite ohne Fehler | `FLA_DASHBOARD_PASSWORD` kürzer als 12 Zeichen, oder nach 5 Fehlversuchen 15 Minuten Sperre pro IP |
| Dashboard zeigt "Noch keine Daten empfangen" | Push in Minecraft nicht eingeschaltet, kein Inventar im Factory Ledger verlinkt, oder Server nicht neu gestartet |
| `Push failed … HTTP 404` | URL falsch (muss auf `/fla/ingest` enden) oder Caddy-Route fehlt |
| `Push failed` mit Verbindungsfehler | Domain/HTTPS nicht erreichbar oder ausgehende Verbindungen beim Minecraft-Hoster blockiert |
| Diagramm hat Lücken | Normal: In diesen Minuten kam kein Push (Server aus oder Verbindung weg) bzw. wurde nichts gemessen |

## Sicherheit
- Dashboard: Passwort-Login, Cookie `HttpOnly`, `SameSite=Strict`, `Secure` hinter HTTPS, Sperre nach 5 Fehlversuchen, Content-Security-Policy (nur eigene Skripte).
- Empfänger: nur POST, 1 MB Limit, Zeitstempel- und Signaturprüfung mit konstanter Zeit, Snapshot-Version wird geprüft.
- Passwort und Secret nur in der `.env` (Rechte 600), nie im Repo. Wurden sie in einem Chat oder Screenshot gezeigt, neu erzeugen.
- **Server-Zugang:** Ein Root-Passwort, das irgendwo geteilt wurde, sollte geändert werden (`passwd`). Besser: SSH-Schlüssel einrichten und den Passwort-Login in `sshd_config` abschalten.
- **Datenschutz:** Das Dashboard verarbeitet Spiel- und Anlagendaten (Koordinaten, Item-Mengen) und setzt ein Login-Cookie. Die Datenschutzerklärung der Seite sollte das erwähnen.

## Grenzen
- Gemessen werden **Bestandsänderungen** (`STOCK_DELTA`), keine Brutto-Produktion. Förderbänder werden nicht gemessen.
- Die Mod schickt nur die Werte der letzten 5 Minuten; die Langzeit-Historie baut der Empfänger selbst auf. Daten vor dem ersten Push gibt es dort nicht.
- Das Dashboard wurde im Browser mit künstlichen Demo-Daten geprüft (`website/tools/seed-demo.js`): Diagramm, Quellen- und Item-Auswahl, Lücken, Tabelle. Seit dem 3.11.2026 kommen auch **echte** Pushes von Minecraft an (8 Quellen); eine längere Beobachtung über Stunden und Server-Neustarts steht noch aus.
