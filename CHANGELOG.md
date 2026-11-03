# Changelog

Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach [SemVer](https://semver.org/lang/de/) (siehe `docs/VERSIONING.md`).

## [Unreleased]

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
