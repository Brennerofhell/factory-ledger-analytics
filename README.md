# Factory Ledger Analytics

NeoForge-Addon für **Create: Factory Ledger** (Minecraft 1.21.1, NeoForge 21.1.x, Java 21):
Langzeit-Historie, Diagramme, Offline-Aufzeichnung, Export und eine optionale Web-Schnittstelle.
Factory Ledger wird weder ersetzt noch verändert; das Addon ist eine eigene JAR.

## Was es tut
- Übernimmt automatisch die **Inventar-Links** deiner Factory Ledger (keine zweite Einrichtung).
- Misst **serverseitig** in einem einstellbaren Intervall, auch **ohne Spieler online**, solange die Chunks laufen.
- Speichert die Historie **dauerhaft** im Weltordner (`factoryledgeranalytics/`), asynchron, mit Prüfsumme und Backup.
- Eigener Bildschirm (Taste `K`): Overview, Links, Charts, History, Statistics, Settings; Linien-, Balken-, Vergleichs-, kumulierte und Ein/Aus-Diagramme, Zoom, Verschieben, Tooltips, Items ein-/ausblenden.
- **Messlücken** (Server aus, Chunk nicht geladen, Moduswechsel) sind in den Diagrammen rot markiert und werden nie als Produktion gezählt.
- **Compacting Drawers:** Modus `OFF` / `DEDUPLICATE` / `NORMALIZED` (Config `compacting.mode`, im Settings-Tab umschaltbar), mit den echten Umrechnungsregeln aus Storage Drawers (kein pauschales 9:1).
- Export: `/flanalytics export <csv|json> [Stunden]` (nur Operatoren, läuft im Hintergrund).
- Optional: Push an deine Website oder lesender HTTP-Endpunkt, siehe [docs/API.md](docs/API.md).

## Wichtige Einschränkungen (bitte lesen)
- **Messwerte sind Bestandsänderungen (`STOCK_DELTA`), keine Brutto-Produktion.** Wird im selben Messintervall gleich viel ein- und ausgelagert, ist das unsichtbar. Das gilt für alle Inventar-/Drawer-Links.
- **Förderband-Links** (BELT) von Factory Ledger werden noch **nicht** gemessen (Create-JAR zum Kompilieren fehlte).
- Das Verhalten von Compacting Drawers (welche Slots Factory Ledger bzw. dieses Addon sieht) ist aus dem Code abgeleitet und **nicht im Spiel verifiziert**.
- Factory Ledger ist **All Rights Reserved**. Dieses Addon nutzt es nur als Kompilier-Abhängigkeit und liefert es nicht mit. Es verwendet keine Mixins.

## Installation
1. `factory-ledger-analytics-1.0.0.jar` in den `mods`-Ordner von **Server und Clients** legen (Clients ohne Addon können beitreten, haben aber keine Oberfläche).
2. Factory Ledger, Create 6.x und NeoForge 21.1.219+ müssen vorhanden sein. Storage Drawers ist optional.
3. Server einmal starten, danach `config/factoryledgeranalytics-common.toml` anpassen (Messintervall, Aufbewahrung, Berechtigungen, API).
4. Im Spiel: Taste `K` (unter *Factory Ledger Analytics* umbelegbar).

## Konfiguration (Auszug)
| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `monitoringEnabled` | true | Messung an/aus |
| `sampleIntervalTicks` | 20 | Messintervall |
| `saveIntervalSeconds` | 60 | Speicherintervall |
| `retentionDays` | 90 | Aufbewahrung |
| `maxMeasurementPoints` | 256 | Obergrenze Messpunkte |
| `maxHistorySizeMb` | 256 | Obergrenze Historie |
| `compacting.mode` | NORMALIZED | Drawer-Zählmodus |
| `permissions.viewLevel` / `modeLevel` | 0 / 2 | Wer Daten sieht / den Modus ändert |
| `api.*`, `push.*` | aus | Website-Anbindung |

## Speicherung
1-Minuten-Werte (6 h) → 10-Minuten-Werte (7 Tage) → Stundenwerte (bis zur Aufbewahrungsdauer). Ältere Werte werden zusammengefasst, Summen bleiben erhalten. Dateien: pro Messpunkt eine `.fla`-Datei (CRC32, GZIP, Versionsnummer), atomar geschrieben, vorherige Version als `.bak`, defekte Dateien werden als `.corrupt-…` zur Seite gelegt.

## Getestete Versionen (ehrlich)
| Was | Stand |
|---|---|
| Kompiliert gegen | Minecraft 1.21.1, NeoForge 21.1.219, Factory Ledger JAR `factoryledger-1.5.0.jar` (Metadaten melden `1.0.0`), Storage Drawers 13.11.4 |
| Create | **nicht** eingebunden/getestet (JAR fehlte) |
| Gradle-Build + 32 Unit-Tests | grün (Java 21, Gradle 9.2.1) |
| **Laufzeit im Spiel (Client/Server)** | **nicht getestet** |

Die Unit-Tests decken die reine Logik ab (Normalisierung, Delta/Lücken, Aggregation, Speicherformat inkl. Beschädigung und unbekannter Version, Abfragen, Diagramm-Mathematik, Export). Nicht automatisiert getestet: echte Förderbänder, Chunk-Unloading, Dimensionen, Serverneustart im laufenden Spiel, Mehrspieler-Netzwerk.

## Bauen
```
./gradlew build      # Ergebnis: build/libs/factory-ledger-analytics-1.0.0.jar
```
Die Fremd-JARs (Factory Ledger, Storage Drawers, später Create) gehören nach `libs/` und werden nicht eingecheckt.

## Weitere Doku
[docs/ANALYSE.md](docs/ANALYSE.md) (technischer Analysebericht), [docs/API.md](docs/API.md) (Website-Schnittstelle).
