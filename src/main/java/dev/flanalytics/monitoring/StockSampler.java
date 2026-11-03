package dev.flanalytics.monitoring;

import dev.flanalytics.config.CompactingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Turns the visible slots of one inventory into a stock snapshot (itemId -> amount). */
public final class StockSampler {
    public record SlotStack(String itemId, long count) {}

    private StockSampler() {}

    /**
     * @param compactingBlock true if the inventory is a compacting drawer: its slots are different views of
     *                        the SAME pool, so a chain must be counted once (max), not summed.
     */
    public static Map<String, Long> snapshot(List<SlotStack> slots, CompactingMode mode,
                                             boolean compactingBlock, ChainNormalizer norm) {
        Map<String, Long> out = new HashMap<>();
        if (mode == CompactingMode.OFF || !compactingBlock && mode == CompactingMode.DEDUPLICATE) {
            for (SlotStack s : slots) {
                if (s.count() > 0) out.merge(s.itemId(), s.count(), Long::sum);
            }
            return out;
        }
        if (mode == CompactingMode.NORMALIZED) {
            for (SlotStack s : slots) {
                if (s.count() <= 0) continue;
                ChainNormalizer.Unit u = norm.resolve(s.itemId());
                long units = safeMul(s.count(), u.perItem());
                if (compactingBlock) out.merge(u.baseId(), units, Math::max);
                else out.merge(u.baseId(), units, Long::sum);
            }
            return out;
        }
        // DEDUPLICATE on a compacting block: keep, per chain, the slot holding the most base units.
        Map<String, Long> bestUnits = new HashMap<>();
        Map<String, SlotStack> bestSlot = new HashMap<>();
        for (SlotStack s : slots) {
            if (s.count() <= 0) continue;
            ChainNormalizer.Unit u = norm.resolve(s.itemId());
            long units = safeMul(s.count(), u.perItem());
            Long prev = bestUnits.get(u.baseId());
            if (prev == null || units > prev) {
                bestUnits.put(u.baseId(), units);
                bestSlot.put(u.baseId(), s);
            }
        }
        for (SlotStack s : bestSlot.values()) out.merge(s.itemId(), s.count(), Long::sum);
        return out;
    }

    private static long safeMul(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
