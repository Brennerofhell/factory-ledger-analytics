package dev.flanalytics.network;

import dev.flanalytics.FactoryLedgerAnalytics;
import dev.flanalytics.config.AnalyticsConfig;
import dev.flanalytics.config.CompactingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration and the server-side handlers (permission check, rate limit, validation). */
public final class AnalyticsNetwork {
    private static final long MIN_REQUEST_GAP_MS = 400;
    private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();

    private AnalyticsNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional(): vanilla / client-without-addon can still join, they just have no analytics screen
        PayloadRegistrar r = event.registrar("1").optional();
        r.playToServer(HistoryRequestPayload.TYPE, HistoryRequestPayload.CODEC, AnalyticsNetwork::onRequest);
        r.playToServer(SetModePayload.TYPE, SetModePayload.CODEC, AnalyticsNetwork::onSetMode);
        // lambda (not a method reference) so the client class is only loaded when a packet actually arrives
        r.playToClient(HistoryResponsePayload.TYPE, HistoryResponsePayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.flanalytics.client.ClientHooks.onResponse(p)));
    }

    private static void onRequest(HistoryRequestPayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(AnalyticsConfig.GUI_PERMISSION_LEVEL.get())) return;
        long now = System.currentTimeMillis();
        Long last = LAST_REQUEST.put(player.getUUID(), now);
        if (last != null && now - last < MIN_REQUEST_GAP_MS) return;
        ctx.reply(buildResponse(p, now));
    }

    static HistoryResponsePayload buildResponse(HistoryRequestPayload p, long now) {
        var history = FactoryLedgerAnalytics.HISTORY;
        List<PointMeta> metas = p.wantPoints() ? history.metas() : List.of();
        String selected = p.pointId();
        var point = selected.isEmpty() ? null : history.point(selected);
        QueryService.Result r;
        if (point == null && selected.isEmpty() && !metas.isEmpty()) {
            selected = metas.get(0).id();
            point = history.point(selected);
        }
        if (point == null && selected.isEmpty()) {
            r = new QueryService.Result(List.of(), List.of(), false, p.from(), p.to());
        } else {
            r = QueryService.query(point, FactoryLedgerAnalytics.MONITORING.recentSamples(), selected, p.from(), p.to(), p.maxBuckets(), now);
        }
        List<List<TierDto>> ladders = new java.util.ArrayList<>();
        for (String item : r.items()) {
            List<TierDto> l = new java.util.ArrayList<>();
            if (AnalyticsConfig.COMPACTING_MODE.get() == dev.flanalytics.config.CompactingMode.NORMALIZED) {
                for (var t : FactoryLedgerAnalytics.MONITORING.ladderFor(item)) l.add(new TierDto(t.itemId(), t.perItem()));
            }
            ladders.add(l);
        }
        return new HistoryResponsePayload(p.wantPoints(), metas, selected, r.live(), r.from(), r.to(),
                AnalyticsConfig.COMPACTING_MODE.get().name(), r.items(), ladders, r.buckets());
    }

    private static void onSetMode(SetModePayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(AnalyticsConfig.MODE_PERMISSION_LEVEL.get())) return;
        CompactingMode mode;
        try {
            mode = CompactingMode.valueOf(p.mode());
        } catch (IllegalArgumentException e) {
            return;
        }
        if (mode == AnalyticsConfig.COMPACTING_MODE.get()) return;
        AnalyticsConfig.COMPACTING_MODE.set(mode);
        AnalyticsConfig.SPEC.save();
        // values of different modes are not comparable: start a new baseline everywhere, never mix them
        FactoryLedgerAnalytics.MONITORING.markAllGaps();
        FactoryLedgerAnalytics.LOGGER.info("{} switched compacting mode to {}", player.getGameProfile().getName(), mode);
    }
}
