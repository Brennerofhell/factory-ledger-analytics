# Graph Report - Factory Ledger Analytics enhanced  (2026-11-03)

## Corpus Check
- Corpus is ~24,266 words - fits in a single context window. You may not need a graph.

## Summary
- 556 nodes · 1324 edges · 22 communities (19 shown, 3 thin omitted)
- Extraction: 90% EXTRACTED · 10% INFERRED · 0% AMBIGUOUS · INFERRED: 127 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- History Buckets & Storage I/O
- Chart Math & Rendering
- API Snapshot & Tests
- Ledger Registry & Drawers Compat
- Config, Commands & HTTP
- Client Events & Hooks
- Network Payloads
- Network, Screen & UI State
- Services & Known Problems
- API Docs & History Tiers
- Design Decisions & Release 1.0.0
- Releases 1.0.1-1.1.2
- Architecture & Release Process
- Analysis & No-Mixin Decision
- Compacting Drawers & Modes
- Normalization & Tests
- Persistence & Export
- Screen Tabs
- Gradle Wrapper
- Build Docs

## God Nodes (most connected - your core abstractions)
1. `AnalyticsScreen` - 46 edges
2. `PointHistory` - 42 edges
3. `HistoryResponsePayload` - 34 edges
4. `MeasurementSample` - 27 edges
5. `MonitoringService` - 21 edges
6. `FactoryLedgerAnalytics` - 20 edges
7. `Bucket` - 19 edges
8. `ChartModel` - 18 edges
9. `AnalyticsConfig` - 16 edges
10. `Delta` - 16 edges

## Surprising Connections (you probably didn't know these)
- `Release 1.1.4 (robust Analytics button detection)` --references--> `LedgerScreenIntegration`  [INFERRED]
  CHANGELOG.md → docs/ARCHITEKTUR.md
- `Release 1.0.0 (first working version, tag v1.0.0)` --references--> `Own files instead of SavedData or database`  [INFERRED]
  CHANGELOG.md → docs/ARCHITEKTUR.md
- `Release 1.1.4 (robust Analytics button detection)` --references--> `Open / unclear items (unit Barren/Block, Analytics button, untested areas, single-player gaps)`  [INFERRED]
  CHANGELOG.md → docs/BEKANNTE-PROBLEME.md
- `Config key sampleIntervalTicks` --conceptually_related_to--> `MonitoringService`  [INFERRED]
  docs/KONFIGURATION.md → docs/ARCHITEKTUR.md
- `Config key saveIntervalSeconds` --conceptually_related_to--> `HistoryRepository`  [INFERRED]
  docs/KONFIGURATION.md → docs/ARCHITEKTUR.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Server-side measurement pipeline** — docs_architektur_monitoringservice, docs_architektur_stocksampler, docs_architektur_deltatracker, docs_architektur_chainnormalizer, docs_architektur_historyservice [EXTRACTED 1.00]
- **License-driven addon design (ARR license, no Mixins, own screen, own measurement)** — docs_analyse_license_arr, docs_architektur_no_mixins, docs_architektur_own_screen, docs_architektur_own_measurement, docs_architektur_ledger_button_via_screen_events [INFERRED 0.85]
- **Persistent history storage (own files, codec, repository, aggregation)** — docs_architektur_own_files_not_saveddata, docs_architektur_historyrepository, docs_architektur_historycodec, docs_architektur_pointhistory, docs_architektur_aggregation_tiers [EXTRACTED 1.00]

## Communities (22 total, 3 thin omitted)

### Community 0 - "History Buckets & Storage I/O"
Cohesion: 0.05
Nodes (15): java.io.DataInputStream, net.neoforged.neoforge.event.server.ServerAboutToStartEvent, Bucket, HistoryService, Post, PointHistory, QueryService, Result (+7 more)

### Community 1 - "Chart Math & Rendering"
Cohesion: 0.06
Nodes (22): net.minecraft.client.gui.Font, net.minecraft.client.gui.GuiGraphics, net.minecraft.client.gui.screens.Screen, ChartModel, Metric, IN, NET, OUT (+14 more)

### Community 2 - "API Snapshot & Tests"
Cohesion: 0.07
Nodes (17): org.junit.jupiter.api.Test, ApiSnapshot, Hmac, CompactingMode, DEDUPLICATE, NORMALIZED, OFF, Delta (+9 more)

### Community 3 - "Ledger Registry & Drawers Compat"
Cohesion: 0.07
Nodes (24): EntityPlaceEvent, Load, net.minecraft.core.BlockPos, net.minecraft.resources.ResourceKey, net.minecraft.server.level.ServerLevel, net.minecraft.world.item.ItemStack, net.minecraft.world.level.block.entity.BlockEntity, net.minecraft.world.level.Level (+16 more)

### Community 4 - "Config, Commands & HTTP"
Cohesion: 0.08
Nodes (21): BooleanValue, com.google.gson.Gson, com.sun.net.httpserver.HttpExchange, com.sun.net.httpserver.HttpServer, ConfigValue, EnumValue, IntValue, java.net.http.HttpClient (+13 more)

### Community 5 - "Client Events & Hooks"
Cohesion: 0.11
Nodes (14): Closing, net.minecraft.client.gui.components.Button, net.minecraft.client.KeyMapping, net.neoforged.bus.api.SubscribeEvent, net.neoforged.fml.common.EventBusSubscriber, net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent, Pre, ClientEvents (+6 more)

### Community 6 - "Network Payloads"
Cohesion: 0.12
Nodes (17): net.minecraft.network.codec.StreamCodec, net.minecraft.network.FriendlyByteBuf, net.minecraft.network.protocol.common.custom.CustomPacketPayload, net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent, net.neoforged.neoforge.network.handling.IPayloadContext, Type, BAR, COMPARE (+9 more)

### Community 7 - "Network, Screen & UI State"
Cohesion: 0.15
Nodes (19): AnalyticsNetwork, AnalyticsScreen, Multiple measurement points in one chart (pointId with |, queryMulti, mergePoints), Optional network channel with server-side re-validation and limits, QueryService, Remembered UI selection (ui.properties), UiState, Display unit 'all' (client-side expandUnits) (+11 more)

### Community 8 - "Services & Known Problems"
Cohesion: 0.19
Nodes (17): Release 1.1.3 (code review fixes 1,3,7,9,10), ApiServer, ApiSnapshot, HistoryService, MonitoringService, Thread model (server thread + daemon threads), WebhookPusher, Website: Push (HMAC) and Pull (Bearer token), default off (+9 more)

### Community 9 - "API Docs & History Tiers"
Cohesion: 0.16
Nodes (15): API.md (website interface), HMAC signature and timestamp verification (replay protection), Pull mode (GET /api/v1/snapshot, /health, Bearer token), Push mode (POST, HMAC-SHA256 headers), No public exposure without TLS (reverse proxy or Push), Aggregation tiers 1min / 10min / 1h, PointHistory, Review #6 retentionDays/size limit only on hourly values (open) (+7 more)

### Community 10 - "Design Decisions & Release 1.0.0"
Cohesion: 0.22
Nodes (14): Development stages before 1.0.0 (unreleased commits), Release 1.0.0 (first working version, tag v1.0.0), Provisional strategy (hybrid, own files, gap entries), Response format version 1, ChartModel, Charts without third-party library (GuiGraphics.fill), ChartWidget, DeltaTracker (+6 more)

### Community 11 - "Releases 1.0.1-1.1.2"
Cohesion: 0.15
Nodes (13): Release 1.0.1 (/flanalytics rules command), Release 1.0.2 (log reason for missing larger unit), Release 1.0.3 (UI bar overflow fix), Release 1.0.4 (legend All/None, right-click isolate), Release 1.1.0 (multi-point charts, unit 'all'), Release 1.1.1 (Sources row in Charts tab), Release 1.1.2 (persisted selection, Panels toggle, LICENSE), DrawersCompat (+5 more)

### Community 12 - "Architecture & Release Process"
Cohesion: 0.20
Nodes (8): Unreleased (docs update to 1.1.4), Release 1.1.4 (robust Analytics button detection), Datenfluss (Ledger -> Sampler -> History -> API), Ledger detection via events (ChunkEvent.Load, EntityPlaceEvent), LedgerRegistry, Measurement points (Messpunkte / sources), Release process (changelog, mod_version, build, tag, releases/), SemVer scheme MAJOR.MINOR.PATCH with tag vX.Y.Z

### Community 13 - "Analysis & No-Mixin Decision"
Cohesion: 0.22
Nodes (11): ANALYSE.md (technical analysis report), Original Factory Ledger architecture (LedgerBlockEntity, Link, FlowLog, InventoryTracker, BeltTracker, LedgerScreen), Integration options (public API, own measurement, Mixin, separate screen), Factory Ledger license All Rights Reserved, Original measurement behaviour (2-tick sampling, 300 s RAM window, player-independent), Ledger-window button via ScreenEvents without Mixin, LedgerScreenIntegration, Design decision: no Mixins, only public signatures (+3 more)

### Community 14 - "Compacting Drawers & Modes"
Cohesion: 0.25
Nodes (11): CompactingHelper, CompTierRegistry, Triple counting of Compacting Drawers in Factory Ledger, In-game evidence (Iron 2640/300/30 per min screenshot), Storage Drawers 13.11.4 findings (MIT, virtual slots), Compacting modes OFF / DEDUPLICATE / NORMALIZED, Commands (/flanalytics export, rules), Drawer modes OFF / DEDUPLICATE / NORMALIZED (+3 more)

### Community 15 - "Normalization & Tests"
Cohesion: 0.33
Nodes (7): Normalization design (base unit, lazy chain, cache invalidation), Factory Ledger version discrepancy (1.5.0 file vs 1.0.0 metadata), ChainNormalizer, StockSampler, 34 unit tests (pure logic separated from Minecraft), Review #2 ChainNormalizer never cleared / invalidateRules unused (open), Getestete Versionen (tested versions table)

### Community 16 - "Persistence & Export"
Cohesion: 0.38
Nodes (7): ExportService, HistoryCodec, HistoryRepository, Own files instead of SavedData or database, Review #4 newer-version file may be overwritten (open), Review #8 ExportService copies all data on server thread (open), Compatibility rules (storage format number, network protocol registrar 1)

### Community 17 - "Screen Tabs"
Cohesion: 0.29
Nodes (7): Tab, CHARTS, HISTORY, LINKS, OVERVIEW, SETTINGS, STATISTICS

### Community 18 - "Gradle Wrapper"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **26 isolated node(s):** `NET`, `IN`, `OUT`, `PER_MINUTE`, `PER_HOUR` (+21 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 79 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `PointHistory` connect `History Buckets & Storage I/O` to `API Snapshot & Tests`, `Config, Commands & HTTP`?**
  _High betweenness centrality (0.097) - this node is a cross-community bridge._
- **Why does `AnalyticsScreen` connect `Chart Math & Rendering` to `Screen Tabs`, `Network Payloads`?**
  _High betweenness centrality (0.081) - this node is a cross-community bridge._
- **Why does `MeasurementSample` connect `API Snapshot & Tests` to `History Buckets & Storage I/O`, `Ledger Registry & Drawers Compat`, `Config, Commands & HTTP`?**
  _High betweenness centrality (0.075) - this node is a cross-community bridge._
- **What connects `NET`, `IN`, `OUT` to the rest of the system?**
  _26 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `History Buckets & Storage I/O` be split into smaller, more focused modules?**
  _Cohesion score 0.05396825396825397 - nodes in this community are weakly interconnected._
- **Should `Chart Math & Rendering` be split into smaller, more focused modules?**
  _Cohesion score 0.06491885143570537 - nodes in this community are weakly interconnected._
- **Should `API Snapshot & Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.06628621597892889 - nodes in this community are weakly interconnected._