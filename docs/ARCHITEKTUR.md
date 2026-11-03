# Architektur

Package `dev.flanalytics`, Mod-ID `factoryledgeranalytics`. Reine Logik ist von Minecraft getrennt, damit sie per Unit-Test prüfbar ist.

## Datenfluss
```
Factory Ledger (verlinkt Inventare)
        │  LedgerBlockEntity.getViews() -> Link-Positionen
        ▼
integration.LedgerRegistry  (Ledger-Positionen aus Chunk-Load / Platzieren)
        ▼
monitoring.MonitoringService  (alle sampleIntervalTicks, auf dem Server-Thread)
   IItemHandler-Slots ─► StockSampler ─► DeltaTracker ─► MeasurementSample
        │                  ▲ ChainNormalizer (Regeln aus Storage Drawers)
        ├─► history.HistoryService ─► PointHistory (Buckets) ─► storage.HistoryRepository ─► *.fla (async)
        ├─► network.QueryService ◄── Anfragen des Clients (AnalyticsNetwork); 1..8 Messpunkte, zusammengeführt
        └─► api.ApiSnapshot ─► ApiServer (Pull) / WebhookPusher (Push)
```

## Pakete
| Paket | Aufgabe |
|---|---|
| `config` | `AnalyticsConfig` (NeoForge-Config), `CompactingMode` |
| `integration` | Anbindung an Factory Ledger (`LedgerRegistry`) und Storage Drawers (`McRuleSource`, `DrawersCompat`) |
| `monitoring` | Messung: `MonitoringService`, `StockSampler`, `DeltaTracker`, `ChainNormalizer`, `MeasurementSample` |
| `history` | Aggregation: `PointHistory` (3 Stufen), `Bucket`, `HistoryService` |
| `storage` | Dateiformat `HistoryCodec`, Schreiben/Laden `HistoryRepository`, Export `ExportWriter`/`ExportService` (Befehle) |
| `network` | Pakete (`HistoryRequestPayload`, `HistoryResponsePayload`, `SetModePayload`), `QueryService`, `AnalyticsNetwork` |
| `api` | Website-Schnittstelle: `ApiServer`, `WebhookPusher`, `ApiSnapshot`, `Hmac` |
| `client` | Taste/Hooks (`ClientEvents`, `ClientHooks`), Button im Ledger-Fenster (`LedgerScreenIntegration`), gemerkte Auswahl (`UiState`) |
| `client.charts` | `ChartModel` (Mathematik), `ChartWidget` (Zeichnen mit `GuiGraphics`) |
| `client.gui` | `AnalyticsScreen` (6 Tabs) |

## Entscheidungen und Begründung
- **Keine Mixins, nur öffentliche Signaturen.** Factory Ledger ist *All Rights Reserved* und liefert keinen Quellcode. Das Addon kompiliert nur gegen die öffentlichen Klassen (`LedgerBlockEntity.getViews()`, `LinkView`), liest die Links und misst selbst.
- **Eigene Messung statt Rohdaten des Originals.** Das Original hält nur 300 Sekunden im RAM und gerundete Raten. Für Langzeit-Historie und eine Trennung von Bestandsänderung und Durchsatz ist eigene Messung nötig.
- **Ledger-Erkennung über Events.** Kein Scannen: `ChunkEvent.Load` liefert auch noch nicht deserialisierte BlockEntities (`getBlockEntitiesPos`), `EntityPlaceEvent` neue Ledger.
- **Bestandsänderung ehrlich benennen.** Inventar-Messungen sind `STOCK_DELTA`. Gleichzeitiges Ein-/Auslagern im selben Intervall hebt sich auf. Das steht in API-Daten, README und Oberfläche.
- **Messlücken statt Null.** Ein Punkt ohne Messung bekommt eine neue Basislinie; die Zeit wird als `gapMs` gespeichert. Auch nach Neustart erkennt `PointHistory` Lücken an der Zeit seit der letzten Messung.
- **Compacting Drawers:** Die Slots eines Compacting Drawers sind Ansichten **desselben Pools**. Deshalb zählt `NORMALIZED` eine Kette einmal (Maximum über die Slots), bei normalen Inventaren wird summiert. Raten kommen aus `CompTierRegistry` und, falls dort nichts steht, aus den echten Rezepten (`CompactingHelper`). Die Regel nach oben (für Anzeigeeinheiten) gilt nur, wenn sie in beide Richtungen übereinstimmt.
- **Speichern: eigene Dateien statt `SavedData` oder Datenbank.** `SavedData` serialisiert auf dem Server-Thread und wächst mit der Historie; eine eingebettete Datenbank wäre für Zeitreihen dieser Größe unnötig schwer. Eine Datei pro Messpunkt, atomar geschrieben, CRC32, `.bak`, Quarantäne bei Beschädigung, versionierter Header (unbekannte neuere Version wird nicht gelesen und nicht überschrieben).
- **Aggregation:** 1-min-Werte (6 h) → 10-min (7 Tage) → 1-h (bis zur Aufbewahrungsdauer). Summen bleiben beim Zusammenfassen erhalten (getestet).
- **Netzwerk:** Optionaler Kanal (Clients ohne Addon können beitreten). Jede Anfrage wird serverseitig neu geprüft (Berechtigung, Zeitraum, Größe); Antworten sind auf 600 Zeitabschnitte, 48 Items und 256 Messpunkte begrenzt, beim Lesen gelten dieselben Grenzen.
- **Mehrere Messpunkte in einem Diagramm:** `HistoryRequestPayload.pointId` darf bis zu 8 IDs mit `|` enthalten (IDs enthalten selbst `,` und `@`, aber nie `|`). `QueryService.queryMulti` fragt jeden Punkt einzeln ab und führt die Zeitabschnitte per Startzeit zusammen (`mergePoints`): gleiche Items werden addiert, verschiedene bleiben getrennt, Abdeckung/Lücke = Maximum. Live-Zeitabschnitte werden auf das Messraster gerundet, damit Punkte übereinanderliegen. Der Server prüft jede ID gegen die bekannten Punkte und begrenzt die Anzahl.
- **Anzeigeeinheit „all“:** rein clientseitig (`AnalyticsScreen.expandUnits`): jede Stufe einer Kette wird zu einer eigenen virtuellen Reihe mit eigenem Teiler. Es wird nichts zusätzlich gemessen oder gespeichert.
- **Gemerkte Auswahl:** `UiState` (nur Client) speichert Quellen, ausgeblendete Items, Zeitbereich, Typ, Größe, Einheit und Panels in `config/factoryledgeranalytics-ui.properties` beim Schließen des Fensters. Fehlt die Datei oder ist sie defekt, gelten Standardwerte. Beim Öffnen mit `K` wird die gemerkte Quellenauswahl direkt angefragt.
- **Button im Ledger-Fenster:** Per Screen-Events (`ScreenEvent.Init/Render/MouseButtonPressed`), ohne Mixin. Sucht den Button „Links“, Ausweichlösung: zweiter Button der linken Reihe; Ergebnis wird einmal geloggt.
- **Oberfläche:** Eigener Bildschirm, weil das Original-Fenster feste Tabs hat. Ein Button wird über Bildschirm-Events neben "Links" eingeblendet, ohne etwas im Original zu ändern.
- **Diagramme ohne Fremdbibliothek:** Linien, Balken und Raster mit `GuiGraphics.fill`.
- **Website:** Push (HMAC-SHA256) für Hoster ohne zusätzlichen Port, Pull-Endpunkt mit Bearer-Token; beides standardmäßig aus.

## Threads
Server-Thread: Messung, Registry, Antworten auf Netzwerkanfragen (Rate-Limit pro Spieler: 120 ms, nur angenommene Anfragen zählen). Messpunkte, die ~5 Minuten nicht gesehen wurden, werden vergessen. Eigene Daemon-Threads: Historie-Schreiber (`fla-history-writer`), Export (`fla-export`), HTTP (`fla-api`), Push (`fla-push`). Gemeinsame Daten (`recent`, `PointHistory`) sind synchronisiert bzw. werden als Kopie weitergegeben.

## Tests
34 Unit-Tests in `src/test`: Normalisierung (inkl. Nicht-9:1 und Zyklen), Drawer-Modi, Delta/Lücken, Aggregation und Retention, Speicherformat (Round-Trip, Beschädigung, unbekannte Version, Fallback auf `.bak`), Abfrage, Diagramm-Mathematik, Export, Zusammenführen mehrerer Messpunkte, API-Snapshot, HMAC.
