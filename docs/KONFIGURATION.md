# Konfiguration

Datei: `config/factoryledgeranalytics-common.toml` (wird beim ersten Start erzeugt, gilt für alle Welten bzw. den Server).

| Schlüssel | Standard | Bereich | Bedeutung |
|---|---|---|---|
| `monitoringEnabled` | true | | Hauptschalter der Messung |
| `sampleIntervalTicks` | 20 | 2–1200 | Ticks zwischen Messungen (20 = 1 s) |
| `saveIntervalSeconds` | 60 | 10–3600 | Speicherintervall der Historie |
| `retentionDays` | 90 | 1–3650 | Aufbewahrung der Stundenwerte (siehe [BEKANNTE-PROBLEME.md](BEKANNTE-PROBLEME.md)) |
| `maxMeasurementPoints` | 256 | 1–4096 | Obergrenze Messpunkte |
| `maxHistorySizeMb` | 256 | 8–4096 | Obergrenze der Historie auf der Platte |
| `chartUpdateSeconds` | 2 | 1–60 | Aktualisierung im Fenster |
| `debug` | false | | Loggt jede Messung |
| `exportEnabled` | true | | Erlaubt `/flanalytics export` |

## `[compacting]`
| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `mode` | `NORMALIZED` | `OFF`, `DEDUPLICATE` oder `NORMALIZED` (im Spiel umschaltbar, siehe BEDIENUNG) |

## `[permissions]`
| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `viewLevel` | 0 | Mindest-Berechtigungsstufe, um Daten zu sehen (0 = alle, 2 = Operatoren) |
| `modeLevel` | 2 | Mindest-Berechtigungsstufe, um den Drawer-Modus zu ändern |

## `[api]` (Pull, standardmäßig aus)
| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `enabled` | false | HTTP-Endpunkt an/aus |
| `bind` | `127.0.0.1` | Adresse; `0.0.0.0` nur mit HTTPS-Proxy davor |
| `port` | 8765 | Port |
| `token` | leer | Pflicht, mindestens 16 Zeichen, sonst startet die API nicht |
| `allowedOrigin` | leer | CORS-Ursprung deiner Website |
| `rateLimitPerMinute` | 120 | Anfragen pro Minute |

## `[push]` (standardmäßig aus)
| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `enabled` | false | Push an/aus |
| `url` | leer | Ziel, nur `https` (oder `localhost`) |
| `secret` | leer | HMAC-Schlüssel, mindestens 16 Zeichen |
| `intervalSeconds` | 30 | Abstand der Sendungen (5–3600) |

Format und Prüfung der Website-Seite: [API.md](API.md).

## Client-Datei (kein Config-Eintrag)
`config/factoryledgeranalytics-ui.properties` merkt sich die Fensterauswahl (Quellen, ausgeblendete Items, Zeitbereich, Diagrammtyp, Größe, Einheit, Panels). Wird vom Client beim Schließen des Fensters geschrieben, gilt nur lokal; Datei löschen setzt alles zurück.
