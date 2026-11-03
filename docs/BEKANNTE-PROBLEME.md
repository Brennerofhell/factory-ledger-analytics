# Bekannte Probleme und Grenzen

Stand: Version 1.1.4. Quelle der Fehler: automatischer Code-Review am 2026-11-03. **Noch nicht behoben**, sofern nicht anders vermerkt. Mit ✔ markierte Punkte (1, 3, 7, 9, 10) sind seit 1.1.3 behoben; offen sind 2, 4, 5, 6, 8.

## Fehler aus dem Code-Review
| # | Bereich | Problem | Auswirkung |
|---|---|---|---|
| ~~1~~ ✔ 1.1.3 | `AnalyticsNetwork` | Das Rate-Limit merkt sich auch abgelehnte Anfragen; die GUI fragt beim Zoomen/Ziehen alle 50 ms | Diagramm "friert" ein, solange man zoomt oder zieht |
| 2 | `MonitoringService` | `ChainNormalizer` (mit `ServerLevel`) wird nie geleert, `invalidateRules()` wird nie aufgerufen | Nach Weltwechsel oder `/reload` veraltete Regeln, Speicherleck |
| ~~3~~ ✔ 1.1.3 | `MonitoringService` | `points` wird nie aufgeräumt | Nach 256 verwaisten Punkten wird nichts Neues mehr gemessen (bis zum Neustart) |
| 4 | `HistoryRepository` | Datei einer neueren Version wird nicht gelesen, kann aber beim nächsten Speichern überschrieben werden | Datenverlust nach Mod-Downgrade |
| 5 | `MonitoringService` | `recent` ist global auf 2048 Messungen begrenzt | Live-Ansicht und API decken bei vielen Messpunkten nur wenige Sekunden ab, obwohl "5 Minuten" angegeben ist |
| 6 | `PointHistory` | `retentionDays` und das Größenlimit wirken nur auf die Stundenwerte | Feinere Stufen bleiben bis zu 7 Tage; Größenlimit kann unwirksam sein |
| ~~7~~ ✔ 1.1.3 | `WebhookPusher` | Bei ungültiger URL endet die Schleife; Wiederholabstand kann kürzer als eingestellt werden | Push stoppt still bzw. sendet zu oft |
| 8 | `ExportService` | Kopie aller Daten auf dem Server-Thread, unabhängig vom Zeitraum | Kurzes Ruckeln beim Export großer Historien |
| ~~9~~ ✔ 1.1.3 | `HistoryService.metas()` | Abgeschnitten bei 256, unsortiert | Ab 256 Messpunkten sind nicht alle wählbar |
| ~~10~~ ✔ 1.1.3 | `ClientHooks` | `openRequested` bleibt gesetzt, wenn der Server ablehnt | Keine Rückmeldung bei fehlender Berechtigung; Fenster kann später unerwartet aufgehen |

## Offen / ungeklärt
- **Anzeigeeinheit Barren/Block:** Im Spiel bestätigt (Eisen: `Unit: middle` zeigt Iron Ingot). Der frühere Eindruck „nur Nuggets“ lag am abgeschnittenen `Unit`-Knopf (behoben in 1.0.3). Für andere Ketten gilt weiter: Liefert Storage Drawers keine übereinstimmende Stufe, bleibt es bei der kleinsten Einheit; das Log nennt dann den Grund (`No larger unit found`).
- **Button „Analytics“ im Factory-Ledger-Fenster:** Auf der Testinstanz erschien er nicht; Ursache ungeklärt, seit 1.1.4 robustere Erkennung und Log-Diagnose. `K` funktioniert unabhängig davon.
- **Mehrere Messpunkte:** Nur im Spiel an einer Instanz mit mehreren Quellen gesehen, Live-Zusammenführung (Rundung auf das Messraster) nicht mit stark unterschiedlichen Intervallen getestet.
- **Create-Förderbänder:** werden nicht gemessen (Create-JAR zum Kompilieren fehlte).
- **Brutto-Produktion:** Inventar-Messungen sind Bestandsänderungen (`STOCK_DELTA`); gleichzeitiges Ein-/Auslagern im selben Intervall hebt sich auf.
- **Nicht im Spiel getestet:** Dedicated Server/Mehrspieler, mehrere Dimensionen, Chunk-Unloading, Server-Neustart, Export, Website-Schnittstelle.
- **Version von Factory Ledger:** Dateiname sagt 1.5.0, Metadaten melden 1.0.0.
- **Einzelspieler:** Gemessen wird nur, solange die Welt geöffnet ist. Zeiten mit geschlossenem Spiel erscheinen als Messlücke.
