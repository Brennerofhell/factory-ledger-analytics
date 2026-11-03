# Bekannte Probleme und Grenzen

Stand: Version 1.0.2. Quelle der Fehler: automatischer Code-Review am 2026-11-03. **Noch nicht behoben**, sofern nicht anders vermerkt.

## Fehler aus dem Code-Review
| # | Bereich | Problem | Auswirkung |
|---|---|---|---|
| 1 | `AnalyticsNetwork` | Das Rate-Limit merkt sich auch abgelehnte Anfragen; die GUI fragt beim Zoomen/Ziehen alle 50 ms | Diagramm "friert" ein, solange man zoomt oder zieht |
| 2 | `MonitoringService` | `ChainNormalizer` (mit `ServerLevel`) wird nie geleert, `invalidateRules()` wird nie aufgerufen | Nach Weltwechsel oder `/reload` veraltete Regeln, Speicherleck |
| 3 | `MonitoringService` | `points` wird nie aufgeräumt | Nach 256 verwaisten Punkten wird nichts Neues mehr gemessen (bis zum Neustart) |
| 4 | `HistoryRepository` | Datei einer neueren Version wird nicht gelesen, kann aber beim nächsten Speichern überschrieben werden | Datenverlust nach Mod-Downgrade |
| 5 | `MonitoringService` | `recent` ist global auf 2048 Messungen begrenzt | Live-Ansicht und API decken bei vielen Messpunkten nur wenige Sekunden ab, obwohl "5 Minuten" angegeben ist |
| 6 | `PointHistory` | `retentionDays` und das Größenlimit wirken nur auf die Stundenwerte | Feinere Stufen bleiben bis zu 7 Tage; Größenlimit kann unwirksam sein |
| 7 | `WebhookPusher` | Bei ungültiger URL endet die Schleife; Wiederholabstand kann kürzer als eingestellt werden | Push stoppt still bzw. sendet zu oft |
| 8 | `ExportService` | Kopie aller Daten auf dem Server-Thread, unabhängig vom Zeitraum | Kurzes Ruckeln beim Export großer Historien |
| 9 | `HistoryService.metas()` | Abgeschnitten bei 256, unsortiert | Ab 256 Messpunkten sind nicht alle wählbar |
| 10 | `ClientHooks` | `openRequested` bleibt gesetzt, wenn der Server ablehnt | Keine Rückmeldung bei fehlender Berechtigung; Fenster kann später unerwartet aufgehen |

## Offen / ungeklärt
- **Anzeigeeinheit Barren/Block:** Zeigt die Oberfläche nur Nuggets, liefert die Regel "nach oben" vermutlich keine übereinstimmende Stufe. Fehlersuche mit `/flanalytics rules minecraft:iron_nugget` (und `…iron_ingot`, `…iron_block`).
- **Create-Förderbänder:** werden nicht gemessen (Create-JAR zum Kompilieren fehlte).
- **Brutto-Produktion:** Inventar-Messungen sind Bestandsänderungen (`STOCK_DELTA`); gleichzeitiges Ein-/Auslagern im selben Intervall hebt sich auf.
- **Nicht im Spiel getestet:** Dedicated Server/Mehrspieler, mehrere Dimensionen, Chunk-Unloading, Server-Neustart, Export, Website-Schnittstelle.
- **Version von Factory Ledger:** Dateiname sagt 1.5.0, Metadaten melden 1.0.0.
- **Einzelspieler:** Gemessen wird nur, solange die Welt geöffnet ist. Zeiten mit geschlossenem Spiel erscheinen als Messlücke.
