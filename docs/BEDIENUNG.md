# Bedienung

## Installation
**Einzelspieler:** JAR in den `mods`-Ordner der Instanz (Twitch/CurseForge-App → Modpack → "Ordner öffnen" → `mods`). Alte Version überschreiben, Spiel vorher schließen.
**Server:** JAR in `mods/` des Servers **und** in `mods/` jedes Clients. Clients ohne das Addon können beitreten, haben aber keine Oberfläche.
Voraussetzungen: NeoForge 21.1.219+, Factory Ledger, Create 6.x. Storage Drawers ist optional.

## Messpunkte
Es gibt nichts einzurichten. Das Addon liest die Inventar-Links deiner Factory Ledger. Verlinke einen Drawer wie gewohnt, nach etwa 20 bis 60 Sekunden gibt es Werte. Ein Inventar, das mehrere Ledger verlinken, wird nur einmal gemessen.

## Fenster öffnen
- Taste **K** (änderbar unter Optionen → Steuerung → *Factory Ledger Analytics*), oder
- Button **Analytics** neben "Links" im Factory-Ledger-Fenster.

Das Fenster öffnet sich, sobald der Server geantwortet und die Berechtigung bestätigt hat.

## Obere Leiste
- **Tabs:** Overview, Links, Charts, History, Statistics, Settings.
- **Zeiträume:** Live, 1m, 5m, 15m, 1h, 6h, 24h, 7d, 30d, All. *Live* und kurze Zeiträume nutzen die Rohmessungen der letzten Minuten, längere die gespeicherte Historie.
- **Charts:** Knopf links = Diagrammtyp (LINE, BAR, COMPARE, CUMULATIVE, IN_OUT), Knopf rechts = Größe (NET, IN, OUT).
- **Unit** (Overview, Charts, Statistics): Anzeigeeinheit smallest / middle / largest, z. B. Nugget / Barren / Block. Gilt nur im Modus `NORMALIZED` und nur für Ketten, bei denen Storage Drawers die Regeln liefert.

## Tabs
| Tab | Inhalt |
|---|---|
| Overview | Pro Item: Bestand, In/min, Out/min, Net/min, Net/h, Net/Tag, Gesamt im sichtbaren Zeitraum |
| Links | Alle Messpunkte (Dimension, Position, Modus, letzte Messung). Klick wählt den Messpunkt |
| Charts | Diagramm mit Achsen, Tooltips, Legende |
| History | Tabelle der Zeitabschnitte (Abdeckung, Lücke, Ein/Aus der drei aktivsten Items) |
| Statistics | Minimum, Durchschnitt, Maximum, Summe Ein/Aus, abgedeckte Zeit, Lücken |
| Settings | Einheiten-Info, Drawer-Modus umschalten (nur Operatoren) |

## Diagramm
- **Mausrad** zoomt um die Mausposition, **linke Maustaste ziehen** verschiebt. Danach pausiert die Live-Verfolgung; ein Klick auf einen Zeitraum-Knopf setzt sie zurück.
- **Legende** unten: Klick blendet ein Item aus oder ein.
- **Tooltip:** Zeit, Wert pro Einheit, `+ein / -aus`, Bestand.
- **Rote Flächen** = Messlücke (nicht gemessen). Dort wird nichts gezählt und nichts gezeichnet.
- Raten werden auf die **tatsächlich gemessene Zeit** bezogen, nicht auf die ganze Zeitspanne.

## Drawer-Modi
| Modus | Wirkung |
|---|---|
| `OFF` | Wie Factory Ledger: jede Form (Nugget/Barren/Block) einzeln, mit Doppelzählung |
| `DEDUPLICATE` | Eine Kette wird einmal gezählt, in der Form mit dem größten Wert |
| `NORMALIZED` (Standard) | Die Kette wird in die kleinste Einheit umgerechnet, Anzeigeeinheit wählbar |
Beim Moduswechsel beginnt eine neue Basislinie; alte und neue Werte werden nicht vermischt.

## Befehle (Operatoren; im Einzelspieler müssen Cheats an sein)
| Befehl | Wirkung |
|---|---|
| `/flanalytics export csv [Stunden]` | CSV-Export nach `<Welt>/factoryledgeranalytics/exports/` (Standard 24 h) |
| `/flanalytics export json [Stunden]` | dasselbe als JSON |
| `/flanalytics rules <item>` | Zeigt, welche Compacting-Regeln Storage Drawers für das Item meldet (Fehlersuche) |

## Speicherorte
- Historie: `<Welt>/factoryledgeranalytics/*.fla` (+ `.bak`, bei Defekt `.corrupt-…`)
- Exporte: `<Welt>/factoryledgeranalytics/exports/`
- Config: `config/factoryledgeranalytics-common.toml` (siehe [KONFIGURATION.md](KONFIGURATION.md))

## Fehlersuche
| Problem | Prüfen |
|---|---|
| `K` bewirkt nichts | JAR auf dem **Client** vorhanden? Richtige Instanz? `logs/latest.log` nach `factoryledgeranalytics` durchsuchen |
| "No measurement points yet" | Inventar in einer Factory Ledger verlinkt? 20–60 s warten, Chunks müssen geladen sein |
| Nur eine Form (z. B. Nuggets) sichtbar | Modus `NORMALIZED` zeigt eine Kette einmal; Einheit per *Unit*-Knopf wechseln oder Modus `OFF` wählen. Findet `/flanalytics rules` keine höhere Stufe, bleibt es bei der kleinsten |
| Diagramm ruckelt beim Zoomen | Bekannter Fehler, siehe [BEKANNTE-PROBLEME.md](BEKANNTE-PROBLEME.md) |
| Rote Fläche | Es wurde nicht gemessen (Spiel/Server aus, Chunk nicht geladen, Moduswechsel) |
