# Factory Ledger Analytics

[![Download](https://img.shields.io/github/v/release/Brennerofhell/factory-ledger-analytics?label=Download&style=for-the-badge&color=2ea44f)](https://github.com/Brennerofhell/factory-ledger-analytics/releases/latest)
[![Alle Versionen](https://img.shields.io/badge/Alle_Versionen-Releases-blue?style=for-the-badge)](https://github.com/Brennerofhell/factory-ledger-analytics/releases)
[![Lizenz: MIT](https://img.shields.io/badge/Lizenz-MIT-yellow?style=for-the-badge)](LICENSE)
![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62b47a?style=for-the-badge)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-e68c1e?style=for-the-badge)

NeoForge-Addon für **Create: Factory Ledger** (Minecraft 1.21.1, NeoForge 21.1.x, Java 21):
Langzeit-Historie, Diagramme, Offline-Aufzeichnung, Export und eine optionale Web-Schnittstelle.
Factory Ledger wird weder ersetzt noch verändert; das Addon ist eine eigene JAR ohne Mixins.

**Aktuelle Version: 1.1.5** (siehe [CHANGELOG.md](CHANGELOG.md), Versionsschema in [docs/VERSIONING.md](docs/VERSIONING.md)).

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
- **Mehrere Quellen in einem Diagramm** (bis zu 8, gleiche Items werden addiert) und **gemerkte Auswahl** über Neustarts.
- **Compacting Drawers:** Modus `OFF` / `DEDUPLICATE` / `NORMALIZED` und Anzeigeeinheit (kleinste / mittlere / größte Stufe oder alle gleichzeitig, z. B. Nugget / Barren / Block) mit den echten Umrechnungsregeln aus Storage Drawers.
- Export: `/flanalytics export <csv|json> [Stunden]`, Diagnose: `/flanalytics rules <item>`.
- Optional: Push an deine Website oder lesender HTTP-Endpunkt ([docs/API.md](docs/API.md)).

## Wichtige Einschränkungen
- **Messwerte sind Bestandsänderungen (`STOCK_DELTA`), keine Brutto-Produktion.** Wird im selben Messintervall gleich viel ein- und ausgelagert, ist das unsichtbar.
- **Förderband-Links** von Factory Ledger werden noch **nicht** gemessen (Create-JAR zum Kompilieren fehlte).
- Compacting Drawers: Dass Factory Ledger sie dreifach zählt (Nugget/Barren/Block), ist per Spiel-Screenshot belegt. Die Anzeigeeinheit Barren/Block hängt von den Regeln aus Storage Drawers ab (Eisen im Spiel bestätigt, andere Ketten prüfen: `/flanalytics rules`).
- Factory Ledger ist **All Rights Reserved**: Das Addon nutzt es nur als Kompilier-Abhängigkeit, liefert es nicht mit und verwendet keine Mixins.
- Bekannte Fehler aus dem Code-Review: [docs/BEKANNTE-PROBLEME.md](docs/BEKANNTE-PROBLEME.md).

## Installation (Kurzfassung)
1. **[JAR herunterladen](https://github.com/Brennerofhell/factory-ledger-analytics/releases/latest)** (Datei `factory-ledger-analytics-<Version>.jar` unter *Assets*).
2. `factory-ledger-analytics-<Version>.jar` in den `mods`-Ordner (Einzelspieler: Instanz; Server: Server **und** Clients).
3. Factory Ledger, Create 6.x und NeoForge 21.1.219+ müssen vorhanden sein, Storage Drawers ist optional.
4. Spiel/Server starten, im Spiel `K` drücken. Details: [docs/BEDIENUNG.md](docs/BEDIENUNG.md).

## Getestete Versionen (ehrlich)
| Was | Stand |
|---|---|
| Kompiliert gegen | Minecraft 1.21.1, NeoForge 21.1.219, Factory Ledger JAR `factoryledger-1.5.0.jar` (Metadaten melden `1.0.0`), Storage Drawers 13.11.4 |
| Create | **nicht** eingebunden/getestet (JAR fehlte) |
| Gradle-Build + 41 Unit-Tests | grün (Java 21, Gradle 9.2.1) |
| Im Spiel bestätigt | Mod lädt (Einzelspieler und Mehrspieler-Server), Taste `K`, Diagramm mit Live-Daten, Messung eines Compacting Drawers, Einheit Barren, mehrere Quellen in einem Diagramm |
| **Nicht im Spiel getestet** | Mehrere Dimensionen, Chunk-Unloading, Server-Neustart, Export, Website-Schnittstelle, Button im Ledger-Fenster (fehlte auf der Testinstanz, siehe Bekannte Probleme), gemerkte Auswahl über Neustart |

Die Unit-Tests decken die reine Logik ab (Normalisierung, Delta/Lücken, Aggregation, Speicherformat inkl. Beschädigung und unbekannter Version, Abfragen, Diagramm-Mathematik, Export).

## Bauen
```
./gradlew build      # Ergebnis: build/libs/factory-ledger-analytics-<Version>.jar
```
Die Fremd-JARs (Factory Ledger, Storage Drawers, später Create) gehören nach `libs/` und werden nicht eingecheckt. Fertige JARs jeder Version liegen lokal in `releases/` (nicht im Git).

## Lizenz
[MIT](LICENSE) für den Code dieses Addons. Die Fremd-Mods (Create: Factory Ledger – *All Rights Reserved*, Storage Drawers, Create) sind **nicht** Teil dieses Repositories und stehen unter ihren eigenen Lizenzen; sie liegen nur lokal in `libs/` zum Kompilieren und werden nicht weitergegeben.
