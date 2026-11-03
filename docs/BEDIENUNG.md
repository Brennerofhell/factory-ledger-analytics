# Bedienung

Stand: Version 1.1.4.

## Installation
**Einzelspieler:** JAR in den `mods`-Ordner der Instanz (Twitch/CurseForge-App → Modpack → "Ordner öffnen" → `mods`). Alte Version löschen bzw. überschreiben (nie zwei Versionen gleichzeitig), Spiel vorher schließen.
**Server:** JAR in `mods/` des Servers **und** in `mods/` jedes Clients, **in derselben Version**. Clients ohne das Addon können beitreten, haben aber keine Oberfläche.
Voraussetzungen: NeoForge 21.1.219+, Factory Ledger, Create 6.x. Storage Drawers ist optional.
Download: [GitHub Releases](https://github.com/Brennerofhell/factory-ledger-analytics/releases/latest).

## Messpunkte
Es gibt nichts einzurichten. Das Addon liest die Inventar-Links deiner Factory Ledger. Verlinke einen Drawer wie gewohnt, nach etwa 20 bis 60 Sekunden gibt es Werte. Ein Inventar, das mehrere Ledger verlinken, wird nur einmal gemessen. Jedes verlinkte Inventar ist ein eigener **Messpunkt** (auch „Quelle“ genannt).

## Fenster öffnen
- Taste **K** (änderbar unter Optionen → Steuerung → *Factory Ledger Analytics*), oder
- Button **Analytics** neben "Links" im Factory-Ledger-Fenster (falls er fehlt: [Fehlersuche](#fehlersuche), `K` geht immer).

Das Fenster öffnet sich, sobald der Server geantwortet und die Berechtigung bestätigt hat.

## Obere Leiste
- **Tabs:** Overview, Links, Charts, History, Statistics, Settings.
- **Zeiträume:** Live, 1m, 5m, 15m, 1h, 6h, 24h, 7d, 30d, All. *Live* und kurze Zeiträume nutzen die Rohmessungen der letzten Minuten, längere die gespeicherte Historie.
- **Zweite Zeile** (Overview, Charts, Statistics):
  - **Unit:** Anzeigeeinheit `smallest` / `middle` / `largest`, z. B. Nugget / Barren / Block. In **Charts** gibt es zusätzlich `all`: alle Einheiten gleichzeitig als eigene Linien (reine Anzeige, nichts wird doppelt gemessen). Gilt nur im Modus `NORMALIZED` und nur für Ketten, bei denen Storage Drawers die Regeln liefert.
  - Nur Charts: **Diagrammtyp** (LINE, BAR, COMPARE, CUMULATIVE, IN_OUT), **Panels: on/off**, **Größe** (NET, IN, OUT).

## Tabs
| Tab | Inhalt |
|---|---|
| Overview | Pro Item: Bestand, In/min, Out/min, Net/min, Net/h, Net/Tag, Gesamt im sichtbaren Zeitraum |
| Links | Alle Messpunkte (Dimension, Position, Modus, letzte Messung). Klick wählt einen Messpunkt an oder ab (`[x]` / `[ ]`) |
| Charts | Diagramm mit Achsen, Tooltips, Quellen- und Item-Auswahl |
| History | Tabelle der Zeitabschnitte (Abdeckung, Lücke, Ein/Aus der drei aktivsten Items) |
| Statistics | Minimum, Durchschnitt, Maximum, Summe Ein/Aus, abgedeckte Zeit, Lücken |
| Settings | Einheiten-Info, Drawer-Modus umschalten (nur Operatoren) |

## Auswahl im Diagramm
Es gibt zwei Ebenen: **Quellen** (welche Inventare) und **Items** (welche Linien).

**Quellen** (Zeile *Sources* im Tab Charts, oder Tab Links)
- Jede Quelle ist ein Kästchen mit den Koordinaten (Overworld ohne Dimensionsnamen). Grün = im Diagramm.
- Ein Klick wählt an oder ab. Bis zu **8 Quellen** gleichzeitig; eine bleibt immer ausgewählt.
- Mehrere Quellen teilen sich ein Diagramm (z. B. Gold aus Quelle A und Holz aus Quelle B). **Gleiche Items aus verschiedenen Quellen werden addiert**, verschiedene Items bleiben getrennte Linien. Die Kopfzeile zeigt „N points combined“.
- Live-Daten werden nur genutzt, wenn jede gewählte Quelle welche hat, sonst die gespeicherte Historie.

**Items** (Legende unter dem Diagramm)
- Klick blendet ein Item aus oder ein, **Rechtsklick** zeigt nur dieses Item, **All** / **None** blenden alle ein bzw. aus.

**Panels: off** blendet Quellen- und Legendenzeilen aus, das Diagramm bekommt fast das ganze Fenster. Zum Auswählen wieder auf `on`.

**Gemerkt wird alles:** Quellen, ausgeblendete Items, Zeitbereich, Typ, Größe, Einheit und Panels bleiben nach Schließen des Fensters, Verlassen der Welt und Spielneustart erhalten (nur Client, siehe [Speicherorte](#speicherorte)). Ist eine gemerkte Quelle nicht mehr vorhanden, wird sie ignoriert.

## Diagramm
- **Mausrad** zoomt um die Mausposition, **linke Maustaste ziehen** verschiebt. Danach pausiert die Live-Verfolgung; ein Klick auf einen Zeitraum-Knopf setzt sie zurück.
- **Tooltip:** Zeit, Wert pro Einheit, `+ein / -aus`, Bestand.
- **Rote Flächen** = Messlücke (nicht gemessen). Dort wird nichts gezählt und nichts gezeichnet.
- Raten werden auf die **tatsächlich gemessene Zeit** bezogen, nicht auf die ganze Zeitspanne.
- Messwerte sind **Bestandsänderungen**, keine Brutto-Produktion (siehe [BEKANNTE-PROBLEME.md](BEKANNTE-PROBLEME.md)).

## Drawer-Modi
| Modus | Wirkung |
|---|---|
| `OFF` | Wie Factory Ledger: jede Form (Nugget/Barren/Block) einzeln, mit Doppelzählung |
| `DEDUPLICATE` | Eine Kette wird einmal gezählt, in der Form mit dem größten Wert |
| `NORMALIZED` (Standard) | Die Kette wird in die kleinste Einheit umgerechnet, Anzeigeeinheit wählbar |
Beim Moduswechsel beginnt eine neue Basislinie; alte und neue Werte werden nicht vermischt.

## Website (optional)
Die Mod kann ihre Messwerte an eine eigene Website senden (Push); ein fertiger Empfänger mit Dashboard liegt in `website/`. Einrichtung und Betrieb: [WEBSITE-BETRIEB.md](WEBSITE-BETRIEB.md), Datenformat: [API.md](API.md). Aus dem Spiel heraus musst du dafür nichts tun; die Einstellungen stehen in der Server-Config im Abschnitt `[push]`. Das Dashboard bietet dieselbe Auswahl wie das Spielfenster (Quellen, Items, Zeiträume) und zeigt Lücken als "nicht gemessen".

## Befehle (Operatoren; im Einzelspieler müssen Cheats an sein)
| Befehl | Wirkung |
|---|---|
| `/flanalytics export csv [Stunden]` | CSV-Export nach `<Welt>/factoryledgeranalytics/exports/` (Standard 24 h) |
| `/flanalytics export json [Stunden]` | dasselbe als JSON |
| `/flanalytics rules <item>` | Zeigt, welche Compacting-Regeln Storage Drawers für das Item meldet (Fehlersuche) |

## Speicherorte
- Historie: `<Welt>/factoryledgeranalytics/*.fla` (+ `.bak`, bei Defekt `.corrupt-…`) — auf dem Server bzw. in der Einzelspieler-Welt
- Exporte: `<Welt>/factoryledgeranalytics/exports/`
- Config: `config/factoryledgeranalytics-common.toml` (siehe [KONFIGURATION.md](KONFIGURATION.md))
- Fensterzustand (Auswahl): `config/factoryledgeranalytics-ui.properties` — nur auf dem Client; Datei löschen setzt die Auswahl zurück

## Fehlersuche
| Problem | Prüfen |
|---|---|
| `K` bewirkt nichts | JAR auf dem **Client** vorhanden? Richtige Instanz? `logs/latest.log` nach `factoryledgeranalytics` durchsuchen |
| Fenster zeigt nichts / keine Daten auf einem Server | Mod muss auf Server **und** Client in derselben Version liegen; Berechtigung `permissions.viewLevel` prüfen |
| Button **Analytics** fehlt im Factory-Ledger-Fenster | In `logs/latest.log` nach `Factory Ledger screen buttons` bzw. `Analytics button not added` suchen; bis dahin Taste `K` nutzen |
| "No measurement points yet" | Inventar in einer Factory Ledger verlinkt? 20–60 s warten, Chunks müssen geladen sein |
| Nur eine Form (z. B. Nuggets) sichtbar | `Unit` auf `middle`/`largest`/`all` stellen. Modus `NORMALIZED` zeigt eine Kette einmal, `OFF` zeigt alle Formen getrennt (mit Doppelzählung). Findet `/flanalytics rules` keine höhere Stufe, bleibt es bei der kleinsten; das Log enthält dann `No larger unit found` |
| Obere Zeile abgeschnitten / Diagramm zu klein | `Panels: off` oder kleinere GUI-Skalierung (Optionen → Videoeinstellungen) |
| Auswahl ist weg | `config/factoryledgeranalytics-ui.properties` nicht beschreibbar oder gelöscht (Log: `Could not save UI state`) |
| Rote Fläche | Es wurde nicht gemessen (Spiel/Server aus, Chunk nicht geladen, Moduswechsel) |
