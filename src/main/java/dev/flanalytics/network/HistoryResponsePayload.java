package dev.flanalytics.network;

import dev.flanalytics.FactoryLedgerAnalytics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded server answer: at most 256 points, 48 items and 600 buckets. Decoding enforces the same limits. */
public record HistoryResponsePayload(boolean hasPoints, List<PointMeta> points, String selected, boolean live,
                                     long from, long to, String compactingMode, List<String> items,
                                     List<List<TierDto>> ladders, List<ChartBucket> buckets) implements CustomPacketPayload {
    public static final Type<HistoryResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryLedgerAnalytics.MOD_ID, "history_response"));
    public static final StreamCodec<FriendlyByteBuf, HistoryResponsePayload> CODEC = StreamCodec.of(HistoryResponsePayload::write, HistoryResponsePayload::read);

    private static void write(FriendlyByteBuf buf, HistoryResponsePayload p) {
        buf.writeBoolean(p.hasPoints);
        buf.writeVarInt(p.points.size());
        for (PointMeta m : p.points) {
            buf.writeUtf(m.id(), 256); buf.writeUtf(m.dimension(), 128);
            buf.writeInt(m.x()); buf.writeInt(m.y()); buf.writeInt(m.z());
            buf.writeUtf(m.mode(), 32); buf.writeLong(m.lastSampleMillis());
        }
        buf.writeUtf(p.selected, 256);
        buf.writeBoolean(p.live);
        buf.writeLong(p.from); buf.writeLong(p.to);
        buf.writeUtf(p.compactingMode, 32);
        buf.writeVarInt(p.items.size());
        for (String s : p.items) buf.writeUtf(s, 128);
        for (int i = 0; i < p.items.size(); i++) {
            List<TierDto> l = i < p.ladders.size() ? p.ladders.get(i) : List.of();
            buf.writeVarInt(l.size());
            for (TierDto t : l) { buf.writeUtf(t.itemId(), 128); buf.writeVarLong(t.perItem()); }
        }
        buf.writeVarInt(p.buckets.size());
        for (ChartBucket b : p.buckets) {
            buf.writeLong(b.start()); buf.writeVarLong(b.width()); buf.writeVarLong(b.coveredMs()); buf.writeVarLong(b.gapMs());
            buf.writeVarInt(b.item().length);
            for (int i = 0; i < b.item().length; i++) {
                buf.writeVarInt(b.item()[i]);
                buf.writeVarLong(b.in()[i]); buf.writeVarLong(b.out()[i]); buf.writeVarLong(b.stock()[i]);
            }
        }
    }

    private static HistoryResponsePayload read(FriendlyByteBuf buf) {
        boolean hasPoints = buf.readBoolean();
        int np = bounded(buf.readVarInt(), 256);
        List<PointMeta> points = new ArrayList<>(np);
        for (int i = 0; i < np; i++) {
            points.add(new PointMeta(buf.readUtf(256), buf.readUtf(128), buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf(32), buf.readLong()));
        }
        String selected = buf.readUtf(256);
        boolean live = buf.readBoolean();
        long from = buf.readLong(), to = buf.readLong();
        String mode = buf.readUtf(32);
        int ni = bounded(buf.readVarInt(), QueryService.MAX_ITEMS);
        List<String> items = new ArrayList<>(ni);
        for (int i = 0; i < ni; i++) items.add(buf.readUtf(128));
        List<List<TierDto>> ladders = new ArrayList<>(ni);
        for (int i = 0; i < ni; i++) {
            int nt = bounded(buf.readVarInt(), 4);
            List<TierDto> l = new ArrayList<>(nt);
            for (int k = 0; k < nt; k++) l.add(new TierDto(buf.readUtf(128), buf.readVarLong()));
            ladders.add(l);
        }
        int nb = bounded(buf.readVarInt(), QueryService.MAX_BUCKETS);
        List<ChartBucket> buckets = new ArrayList<>(nb);
        for (int i = 0; i < nb; i++) {
            long start = buf.readLong(), width = buf.readVarLong(), covered = buf.readVarLong(), gap = buf.readVarLong();
            int n = bounded(buf.readVarInt(), QueryService.MAX_ITEMS);
            int[] it = new int[n];
            long[] in = new long[n], out = new long[n], st = new long[n];
            for (int k = 0; k < n; k++) {
                it[k] = bounded(buf.readVarInt(), QueryService.MAX_ITEMS);
                in[k] = buf.readVarLong(); out[k] = buf.readVarLong(); st[k] = buf.readVarLong();
            }
            buckets.add(new ChartBucket(start, width, covered, gap, it, in, out, st));
        }
        return new HistoryResponsePayload(hasPoints, points, selected, live, from, to, mode, items, ladders, buckets);
    }

    private static int bounded(int v, int max) {
        if (v < 0 || v > max) throw new IllegalArgumentException("size out of range: " + v);
        return v;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
