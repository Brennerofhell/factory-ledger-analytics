# Changelog

Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach [SemVer](https://semver.org/lang/de/) (siehe `docs/VERSIONING.md`).

## [Unreleased]
### Website (`website/`)
- Dashboard-Statuszeile wird rot, wenn länger als 5 Minuten kein Push ankam (bzw. noch nie einer).
- Dashboard: runde Achsenwerte (1, 2, 5 × 10ⁿ) mit der Nulllinie auf einer Rasterlinie; Tabelle unter dem Diagramm (Letzter Wert, Min, Ø, Max, gemessene Minuten je Item); Knopf **CSV herunterladen** (Formelzeichen am Zellenanfang werden entschärft).
- `website/tools/seed-demo.js`: lokale Demo mit künstlichen Daten (3 Quellen, Ausfall von 20 Minuten) zum Ansehen des Dashboards ohne Minecraft.
- Im Browser mit der Demo geprüft: mehrere Quellen im selben Diagramm, gleiche Items addiert, Ausfall erscheint als Lücke, Achsen und Tabelle.
### Dokumentation
- Neu: `docs/WEBSITE-BETRIEB.md` (Aufbau, Serverobjekte, Aktualisieren, Sichern, Fehlersuche, Sicherheit). README, Architektur, Bedienung und Konfiguration verweisen darauf; Teststand und Website-Abschnitt aktualisiert.

## [1.1.6] - 2026-11-03
### Behoben (Code-Review)
- #2 Compacting-Regeln werden bei `/reload` bzw. Datapack-Sync neu gelesen; beim Stoppen werden die Normalisierer (die das `ServerLevel` halten) freigegeben.
- #4 Eine Historie-Datei, die eine **neuere** Mod-Version geschrieben hat, wird nie überschrieben: Neue Daten dieses Punktes bleiben nur im Speicher, eine Warnung steht im Log.
- #5 Live-Daten (`recent`) werden nach Alter (6 Minuten) begrenzt statt nach fester Anzahl; Obergrenze 20.000 Messungen. Bei vielen Messpunkten decken Live-Ansicht und API jetzt wirklich die letzten Minuten ab.
- #6 `retentionDays` gilt als Höchstalter für **alle** Stufen; das Größenlimit kürzt auch 10-Minuten- und 1-Minuten-Werte, wenn es noch keine Stundenwerte gibt.
### Hinzugefügt
- 2 Tests (Aufbewahrung über alle Stufen, Kürzen auf feinere Stufen).
### Hinzugefügt
- `website/`: Empfänger und Dashboard für den Push der Mod (Node, ohne Abhängigkeiten, SQLite, Login, Diagramme mit Lücken statt Nullwerten), mit 6 Tests und README. Ende-zu-Ende mit dem echten Java-Code der Mod geprüft.

## [1.1.5] - 2026-11-03
### Geändert
- Website-Schnittstelle: HTTP-Logik in `ApiHandler` (Pull) und `PushHttp` (Push) ausgelagert, Verhalten unverändert. Dadurch testbar ohne Minecraft.
### Hinzugefügt
- 7 Tests mit echten lokalen HTTP-Servern: Pull (401 ohne/mit falschem Token, 200, Health, 405, Rate-Limit 429, leeres Token, CORS) und Push (UTF-8-Body unverändert, Signatur und Zeitstempel von der Empfängerseite nachrechenbar, nur https bzw. http auf localhost).
### Dokumentation
- Bedienung, Architektur, Konfiguration, README und Bekannte Probleme auf den Stand 1.1.4 gebracht (Quellenauswahl, Einheit „all“, Panels, gemerkte Auswahl, Fehlersuche).

## [1.1.4] - 2026-11-03
### Geändert
- Button **Analytics** im Factory-Ledger-Fenster: robustere Erkennung der Tab-Leiste (Ausweichlösung: zweiter Button der linken Reihe); die gefundenen Buttons werden einmal ins Log geschrieben (`Factory Ledger screen buttons: ...`), bei Misserfolg steht eine Warnung im Log.

## [1.1.3] - 2026-11-03
### Behoben (Code-Review)
- #1 Rate-Limit: abgelehnte Anfragen verlängern die Sperre nicht mehr (Diagramm fror beim Zoomen/Ziehen ein); Mindestabstand 120 ms; Einträge werden aufgeräumt.
- #3 Messpunkte, die ~5 Minuten nicht gesehen wurden, werden vergessen (kein Blockieren des 256er-Limits durch verwaiste Punkte).
- #7 Push: ungültige URL beendet die Schleife nicht mehr still; Backoff verkürzt den eingestellten Abstand nie.
- #9 Punkteliste wird erst sortiert, dann auf 256 gekürzt.
- #10 Das Fenster öffnet sich nur noch für eine Anfrage der letzten 5 Sekunden (kein unerwartetes Öffnen nach abgelehnter Anfrage).

## [1.1.2] - 2026-11-03
### Behoben
- Auswahl (Quellen, ausgeblendete Items, Zeitbereich, Typ, Einheit) bleibt jetzt erhalten: nach Schließen des Fensters, Verlassen der Welt und Neustart (`config/factoryledgeranalytics-ui.properties`, nur Client).
- Diagramm zu klein: Quellen- und Legendenchips sind kompakter (Overworld-Quellen ohne Dimensionsname), und der neue Knopf **Panels: on/off** blendet beide Zeilen für ein großes Diagramm aus. Mindesthöhe des Diagramms 80.
### Dokumentation
- `LICENSE` (MIT) hinzugefügt, Lizenzhinweis im README.

## [1.1.1] - 2026-11-03
### Geändert
- Quellenauswahl direkt im Tab Charts: Zeile **Sources** mit anklickbaren Kästchen je Messpunkt (grün = im Diagramm). Kein Wechsel in den Tab Links mehr nötig.

## [1.1.0] - 2026-11-03
### Hinzugefügt
- **Mehrere Messpunkte in einem Diagramm:** Im Tab Links lassen sich bis zu 8 Quellen per Klick an-/abwählen (z. B. Gold aus Quelle A und Holz aus Quelle B). Gleiche Items aus verschiedenen Quellen werden addiert, verschiedene bleiben getrennte Linien. Live-Daten nur, wenn jeder Punkt welche hat. Protokoll: `pointId` darf mehrere IDs mit `|` enthalten (Server prüft und begrenzt).
- **Einheit "all"** im Tab Charts: Nugget, Barren und Block eines Compacting Drawers gleichzeitig als eigene Linien (reine Anzeige, es wird nichts doppelt gemessen).

## [1.0.4] - 2026-11-03
### Geändert
- Item-Auswahl im Diagramm: Legende als Kästchen mit Rahmen und Hover, Knöpfe **All** und **None**, Rechtsklick zeigt nur dieses Item. Die Legende überlappt nicht mehr mit der Hinweiszeile.

## [1.0.3] - 2026-11-03
### Behoben
- Oberfläche: Die Leiste mit Einheit, Diagrammtyp und Metrik ragte in kleinen Fenstern über den rechten Rand hinaus. Sie liegt jetzt in einer eigenen Zeile unter den Zeitbereichen; der Inhalt rückt entsprechend nach unten.

## [1.0.2] - 2026-11-03
### Geändert
- Findet die Mod für ein Item keine größere Einheit (Barren/Block), schreibt sie einmalig den Grund ins Log (`No larger unit found for ...`). Für die Fehlersuche bei der Anzeigeeinheit.
### Dokumentation
- Neue Dokumente: Bedienung, Architektur, Konfiguration, Bekannte Probleme; README aktualisiert.

## [1.0.1] - 2026-11-03
### Hinzugefügt
- Diagnose-Befehl `/flanalytics rules <item>` zeigt, welche Compacting-Regeln Storage Drawers für ein Item meldet (Fehlersuche bei der Anzeigeeinheit Nugget/Barren/Block).

## [1.0.0] - 2026-11-03  (Tag `v1.0.0`, Commit a16c24f)
Erste lauffähige Version (Messung im Spiel bestätigt: Diagramm, Datenanzeige).

### Hinzugefügt
- Serverseitige Messung der in Factory Ledger verlinkten Inventare (Messpunkte werden automatisch übernommen), auch ohne Spieler online; Messlücken werden erfasst und nie als Produktion gezählt.
- Compacting-Drawers-Modus `OFF` / `DEDUPLICATE` / `NORMALIZED` mit den echten Umrechnungsregeln aus Storage Drawers; Anzeigeeinheit (kleinste / mittlere / größte Stufe) umschaltbar.
- Dauerhafte Historie im Weltordner (1-min / 10-min / 1-h-Stufen, CRC32, atomares Schreiben, `.bak`, Quarantäne bei Beschädigung, versioniertes Format).
- Eigener Analytics-Bildschirm (Taste `K` und Button im Factory-Ledger-Fenster): Overview, Links, Charts, History, Statistics, Settings; Linien-, Balken-, Vergleichs-, kumulierte und Ein/Aus-Diagramme, Zoom, Verschieben, Tooltips, Legende.
- Netzwerkpakete mit Größenlimits, Berechtigungsprüfung und Rate-Limit.
- Export `/flanalytics export <csv|json> [Stunden]` (asynchron, nur Operatoren).
- Optionale Website-Schnittstelle: HMAC-signierter Push und lesender HTTP-Endpunkt mit Token (`docs/API.md`).
- Konfiguration, Unit-Tests (33), Dokumentation.

### Bekannte Einschränkungen
- Werte sind Bestandsänderungen (`STOCK_DELTA`), keine Brutto-Produktion.
- Create-Förderband-Links werden nicht gemessen (Create-JAR fehlte beim Bauen).
- Nicht im Spiel verifiziert: Mehrspieler-Betrieb, Dimensionen, Chunk-Unloading, Serverneustart.

## Entwicklungsstufen vor 1.0.0 (nicht veröffentlicht, keine Tags)
`8a7b10a` Projektgerüst · `3adfe24`, `8085a83` Analysebericht · `5fbb0fd` Gradle/NeoForge-Projekt · `1523e27` Messung + Website-API · `562e385` Historie · `e595737` Netzwerk + GUI · `f5c2d01` Export + README · `f0a8eed` Beleg Dreifachzählung.
