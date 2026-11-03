# Technischer Analysebericht – Factory Ledger (Phase A/B)

Quelle: `factoryledger-1.5.0.jar` (76 893 Bytes), analysiert mit `javap -p -c` (Bytecode, keine Dekompilierung).

## Metadaten / Lizenz
- modId `factoryledger`, Autor Marcus Draaby, Package `dk.draaby.factoryledger`
- `neoforge.mods.toml` meldet **version="1.0.0"**, Dateiname sagt 1.5.0 -> Diskrepanz (MANIFEST.MF ohne Versionsangabe). Echte Version ist nicht eindeutig verifizierbar.
- **Lizenz: "All Rights Reserved"**, kein Quellcode in der JAR. Kein Mixin, kein Code-Einbinden/Weitergeben. Das Addon darf nur gegen die öffentlichen Signaturen kompilieren (compileOnly) und die JAR nicht mitliefern. Offizielle Quelle/Erlaubnis: noch zu klären (Autor fragen).
- Abhängigkeiten: NeoForge >= 21.1.219, Minecraft 1.21.1, Create >= 6.0.0.

## Architektur der Originalmod
| Klasse | Zweck |
|---|---|
| `block.LedgerBlock` (BaseEntityBlock), 3 Tiers (`LedgerTier` BASIC/ADVANCED/INDUSTRIAL: maxLinks, range) | Block |
| `block.LedgerBlockEntity` | Tick-Logik, Links, Sync |
| `block.Link` (package-private) mit `FlowLog log`, `InventoryTracker inventory`, `BeltTracker belt`, `LinkStatus` (OK/UNLOADED/MISSING), `LinkKind` (INVENTORY/BELT) | ein Messpunkt |
| `tracking.FlowLog` | je ItemKey zwei `RateCounter` (in/out) |
| `tracking.RateCounter` (package-private) | Ringpuffer `int[301]`, 1 Bucket = 1 Sekunde (Spielzeit/20) |
| `tracking.InventoryTracker.sample(IItemHandler, FlowLog, long)` | Differenz der Slotsummen je ItemKey |
| `compat.BeltTracker.tick(BeltBlockEntity, FlowLog, long)` | verfolgt `TransportedItemStack`-Positionen |
| `tracking.RateLine(icon,in1,out1,in5,out5)` | Items/min über 1 bzw. 5 min |
| `block.LinkView(pos,kind,status,blockNameKey,lines)` | Client-Sicht |
| `network.ResetLedgerPayload` | einziges Paket (Client->Server, Reset) |
| `client.LedgerScreen` (extends Screen, Tabs OVERVIEW/LINKS) | GUI, kein Menu/Container |

## Messverhalten (aus Bytecode)
- `tickServer`: Sampling jeden 2. Tick (`INVENTORY_SAMPLE_TICKS=2`), pro Link; `rebuildViews` + `sync` alle 20 Ticks.
- **Sampling hängt nicht von Spielern ab.** `hasPlayerNearby` (32 Blöcke) gate't nur `sync()` zum Client. Offline-Messung läuft also, solange der BlockEntity tickt (Chunk geladen). Bei `!Level.isLoaded(pos)` -> Status UNLOADED + `resetSampling()`.
- Inventar-Links: `Level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null)`; `InventoryTracker` vergleicht Slotsummen je ItemKey mit dem letzten Stand -> positive Differenz = "in", negative = "out". **Das ist Bestandsänderung, kein Durchsatz.** Gleichzeitiges Ein- und Auslagern im selben 2-Tick-Fenster hebt sich auf.
- Belt-Links: tatsächliche Bewegung von Items auf dem Förderband (näher am echten Durchsatz).
- Die Daten sind **nur im RAM** (300 s Fenster), werden nicht in NBT gespeichert (`saveAdditional/writeLinks` speichern nur Links). Nach Neustart leer.
- Max. 10 Zeilen je Link (`MAX_LINES_PER_LINK`), Sortierung nach Aktivität.
- `LedgerBlockEntity.displayEntries()` und `getViews()` sind öffentlich; `Link`, `FlowLog`-Interna sind nicht öffentlich nutzbar (package-private bzw. private Felder).

## Integrationsmöglichkeiten
1. **Öffentliche API:** `LedgerBlockEntity.getViews()` liefert `LinkView`/`RateLine` (nur Items/min über 1 und 5 min, gerundete Floats, max. 10 Zeilen) - serverseitig lesbar, aber grob; kein Zugriff auf Rohzähler, Link-Kind-Trennung nur per `LinkView.kind()`.
2. **Eigene Messung über dieselben Capabilities:** Addon liest Link-Positionen aus `LinkView.pos()`, ermittelt IItemHandler/BeltBlockEntity selbst. Braucht dann doch eine zweite Messebene, ist aber die einzige Variante für präzise Rohwerte, Gesamtsummen und Brutto/Netto-Trennung ohne Mixins.
3. **Mixin/Accessor** auf `FlowLog.record(ItemKey,int,boolean,long)`: exakte Events, aber invasiv und bei ARR-Lizenz rechtlich fragwürdig. **Nicht empfohlen.**
4. **GUI:** `LedgerScreen` hat feste Tab-Enum und private Methoden. Zusätzliche Tabs nur per Mixin möglich -> fragil. **Empfehlung: separate Analytics-Screen**, geöffnet per Keybind/Befehl, Daten per eigenen Payloads.

## Vorläufige Strategie
- Hybrid: Ledger-Links über `getViews()` automatisch erkennen (Position, Kind, Status); eigene serverseitige Messung pro Link (Belt: ebenfalls Create-API, Inventar: Delta-Messung, **explizit als `STOCK_DELTA` gekennzeichnet**, nicht als Brutto-Produktion). Brutto-Werte nur wo ein echter Event-Strom vorliegt (Belt); bei Inventaren nur Netto + (in/out-Deltas) mit Hinweis.
- Speicherung: eigene Dateien im Weltordner (`<world>/factoryledgeranalytics/`), asynchron geschrieben, Binär/JSON-Segmente mit Prüfsumme und Downsampling (1 min -> 5 min -> 1 h -> 1 d). SavedData scheidet aus wegen Größe/Serverthread-Serialisierung.
- Messlücken: Server-Start/-Stop-Marker und `LinkStatus.UNLOADED` erzeugen explizite Gap-Einträge.
- Compacting Drawers: noch offen, JAR nötig.

## Offene Punkte / benötigt
1. Erlaubnis oder Klarstellung zur Lizenz beim Autor (für compileOnly-Abhängigkeit unkritisch, Mixins kritisch).
2. Drawer-Mod-JAR (Compacting Drawers) + Create-JAR aus dem Modpack.
3. Welche Messpunkte nutzt deine Tuff-Farm (Belt oder Inventar/Drawer)?
