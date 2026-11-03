package dev.flanalytics.network;

import dev.flanalytics.FactoryLedgerAnalytics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client asks for the point list and/or the chart data of one point. All fields are re-validated on the server. */
public record HistoryRequestPayload(String pointId, long from, long to, int maxBuckets, boolean wantPoints) implements CustomPacketPayload {
    public static final Type<HistoryRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryLedgerAnalytics.MOD_ID, "history_request"));
    public static final StreamCodec<FriendlyByteBuf, HistoryRequestPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.pointId, 256);
                buf.writeLong(p.from);
                buf.writeLong(p.to);
                buf.writeVarInt(p.maxBuckets);
                buf.writeBoolean(p.wantPoints);
            },
            buf -> new HistoryRequestPayload(buf.readUtf(256), buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
