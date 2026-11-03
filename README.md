# Factory Ledger Analytics

NeoForge-Addon für **Create: Factory Ledger** (Minecraft 1.21.1, NeoForge 21.1.x, Java 21):
Langzeit-Historie, Diagramme, Offline-Aufzeichnung, Export und eine optionale Web-Schnittstelle.
Factory Ledger wird weder ersetzt noch verändert; das Addon ist eine eigene JAR ohne Mixins.

**Aktuelle Version: 1.1.1** (siehe [CHANGELOG.md](CHANGELOG.md), Versionsschema in [docs/VERSIONING.md](docs/VERSIONING.md)).

## Dokumentation
| Dokument | Inhalt |
|---|---|
| [docs/BEDIENUNG.md](docs/BEDIENUNG.md) | Installation, Bedienung im Spiel, Befehle, Fehlersuche |
| [docs/ARCHITEKTUR.md](docs/ARCHITEKTUR.md) | Aufbau, Datenfluss, Entscheidungen und deren Begründung |
| [docs/KONFIGURATION.md](docs/KONFIGURATION.md) | Alle Config-Einträge |
| [docs/API.md](docs/API.md) | Website-Schnittstelle (Push / HTTP) |
| [docs/ANALYSE.md](docs/ANALYSE.md) | Technischer Analysebericht zu Factory Ledger und Storage Drawers |
| [docs/BEKANNTE-PROBLEME.md](docs/BEKANNTE-PROBLEME.md) | Offene Fehler und Grenzen (Ergebnis des Code-Reviews) |
| [docs/VERSIONING.md](docs/VERSIONING.md) | Versionierung und Release-Ablauf |

## Was es tut
- Übernimmt automatisch die **Inventar-Links** deiner Factory Ledger (keine zweite Einrichtung).
- Misst **serverseitig** im einstellbaren Intervall, auch **ohne Spieler online**, solange die Chunks laufen.
- Speichert die Historie **dauerhaft** im Weltordner (`factoryledgeranalytics/`), asynchron, mit Prüfsumme und Backup.
- Eigener Bildschirm (Taste `K` oder Button **Analytics** im Factory-Ledger-Fenster): Overview, Links, Charts, History, Statistics, Settings. Diagramme: Linie, Balken, Vergleich, kumuliert, Ein/Aus; Zoom, Verschieben, Tooltips, Items ein-/ausblenden.
- **Messlücken** (Server aus, Chunk nicht geladen, Moduswechsel) sind rot markiert und werden nie als Produktion gezählt.
- **Compacting Drawers:** Modus `OFF` / `DEDUPLICATE` / `NORMALIZED` und Anzeigeeinheit (kleinste / mittlere / größte Stufe, z. B. Nugget / Barren / Block) mit den echten Umrechnungsregeln aus Storage Drawers.
- Export: `/flanalytics export <csv|json> [Stunden]`, Diagnose: `/flanalytics rules <item>`.
- Optional: Push an deine Website oder lesender HTTP-Endpunkt ([docs/API.md](docs/API.md)).

## Wichtige Einschränkungen
- **Messwerte sind Bestandsänderungen (`STOCK_DELTA`), keine Brutto-Produktion.** Wird im selben Messintervall gleich viel ein- und ausgelagert, ist das unsichtbar.
- **Förderband-Links** von Factory Ledger werden noch **nicht** gemessen (Create-JAR zum Kompilieren fehlte).
- Compacting Drawers: Dass Factory Ledger sie dreifach zählt (Nugget/Barren/Block), ist per Spiel-Screenshot belegt. Die Anzeigeeinheit Barren/Block hängt von den gefundenen Regeln ab und wird noch geprüft (`/flanalytics rules`).
- Factory Ledger ist **All Rights Reserved**: Das Addon nutzt es nur als Kompilier-Abhängigkeit, liefert es nicht mit und verwendet keine Mixins.
- Bekannte Fehler aus dem Code-Review: [docs/BEKANNTE-PROBLEME.md](docs/BEKANNTE-PROBLEME.md).

## Installation (Kurzfassung)
1. `factory-ledger-analytics-<Version>.jar` in den `mods`-Ordner (Einzelspieler: Instanz; Server: Server **und** Clients).
2. Factory Ledger, Create 6.x und NeoForge 21.1.219+ müssen vorhanden sein, Storage Drawers ist optional.
3. Spiel/Server starten, im Spiel `K` drücken. Details: [docs/BEDIENUNG.md](docs/BEDIENUNG.md).

## Getestete Versionen (ehrlich)
| Was | Stand |
|---|---|
| Kompiliert gegen | Minecraft 1.21.1, NeoForge 21.1.219, Factory Ledger JAR `factoryledger-1.5.0.jar` (Metadaten melden `1.0.0`), Storage Drawers 13.11.4 |
| Create | **nicht** eingebunden/getestet (JAR fehlte) |
| Gradle-Build + 33 Unit-Tests | grün (Java 21, Gradle 9.2.1) |
| Im Spiel bestätigt (Einzelspieler) | Mod lädt, Taste `K`, Diagramm mit Live-Daten, Messung eines Compacting Drawers |
| **Nicht im Spiel getestet** | Mehrspieler/Dedicated Server, mehrere Dimensionen, Chunk-Unloading, Server-Neustart, Export, Website-Schnittstelle, Anzeigeeinheit Barren/Block |

Die Unit-Tests decken die reine Logik ab (Normalisierung, Delta/Lücken, Aggregation, Speicherformat inkl. Beschädigung und unbekannter Version, Abfragen, Diagramm-Mathematik, Export).

## Bauen
```
./gradlew build      # Ergebnis: build/libs/factory-ledger-analytics-<Version>.jar
```
Die Fremd-JARs (Factory Ledger, Storage Drawers, später Create) gehören nach `libs/` und werden nicht eingecheckt. Fertige JARs jeder Version liegen lokal in `releases/` (nicht im Git).

## Lizenz
[MIT](LICENSE) für den Code dieses Addons. Die Fremd-Mods (Create: Factory Ledger – *All Rights Reserved*, Storage Drawers, Create) sind **nicht** Teil dieses Repositories und stehen unter ihren eigenen Lizenzen; sie liegen nur lokal in `libs/` zum Kompilieren und werden nicht weitergegeben.
