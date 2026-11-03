package dev.flanalytics.monitoring;

import static org.junit.jupiter.api.Assertions.*;

import dev.flanalytics.config.CompactingMode;
import dev.flanalytics.monitoring.ChainNormalizer.Rule;
import dev.flanalytics.monitoring.StockSampler.SlotStack;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonitoringCoreTest {
    // iron: block = 9 ingots, ingot = 9 nuggets. zinc: block = 4 ingots (non-9 rate on purpose).
    private final ChainNormalizer norm = new ChainNormalizer(id -> switch (id) {
        case "m:iron_block" -> new Rule("m:iron_ingot", 9);
        case "m:iron_ingot" -> new Rule("m:iron_nugget", 9);
        case "m:zinc_block" -> new Rule("m:zinc_ingot", 4);
        case "m:loop_a" -> new Rule("m:loop_b", 2);
        case "m:loop_b" -> new Rule("m:loop_a", 2);
        default -> null;
    });

    @Test
    void chainUsesRealRates() {
        assertEquals(new ChainNormalizer.Unit("m:iron_nugget", 81), norm.resolve("m:iron_block"));
        assertEquals(new ChainNormalizer.Unit("m:zinc_ingot", 4), norm.resolve("m:zinc_block"));
        assertEquals(1, norm.resolve("m:gold_nugget").perItem());
    }

    @Test
    void cyclesTerminate() {
        assertNotNull(norm.resolve("m:loop_a"));
    }

    // compacting drawer holding 100 nuggets shows 100 nuggets, 11 ingots, 1 block (same pool)
    private final List<SlotStack> drawer = List.of(
            new SlotStack("m:iron_nugget", 100), new SlotStack("m:iron_ingot", 11), new SlotStack("m:iron_block", 1));

    @Test
    void offModeDoubleCounts() {
        assertEquals(3, StockSampler.snapshot(drawer, CompactingMode.OFF, true, norm).size());
    }

    @Test
    void normalizedCountsPoolOnce() {
        assertEquals(Map.of("m:iron_nugget", 100L), StockSampler.snapshot(drawer, CompactingMode.NORMALIZED, true, norm));
    }

    @Test
    void normalizedSumsPlainInventory() {
        Map<String, Long> s = StockSampler.snapshot(drawer, CompactingMode.NORMALIZED, false, norm);
        assertEquals(100L + 99 + 81, s.get("m:iron_nugget"));
    }

    @Test
    void deduplicateKeepsOneForm() {
        assertEquals(Map.of("m:iron_nugget", 100L), StockSampler.snapshot(drawer, CompactingMode.DEDUPLICATE, true, norm));
    }

    @Test
    void insertingOneIngotIsOneDeltaNotThree() {
        DeltaTracker t = new DeltaTracker();
        t.update(StockSampler.snapshot(drawer, CompactingMode.NORMALIZED, true, norm));
        List<SlotStack> after = List.of(
                new SlotStack("m:iron_nugget", 109), new SlotStack("m:iron_ingot", 12), new SlotStack("m:iron_block", 1));
        DeltaTracker.Result r = t.update(StockSampler.snapshot(after, CompactingMode.NORMALIZED, true, norm));
        assertEquals(Map.of("m:iron_nugget", new DeltaTracker.Delta(9, 0)), r.deltas());
    }

    @Test
    void stockChangeIsNotGrossProduction() {
        DeltaTracker t = new DeltaTracker();
        t.update(Map.of("m:tuff", 100L));
        // 40 produced and 40 removed within one interval -> invisible; documented STOCK_DELTA limitation
        assertTrue(t.update(Map.of("m:tuff", 100L)).deltas().isEmpty());
    }

    @Test
    void gapPreventsInventedProduction() {
        DeltaTracker t = new DeltaTracker();
        t.update(Map.of("m:tuff", 100L));
        t.markGap();
        DeltaTracker.Result r = t.update(Map.of("m:tuff", 5000L)); // chunk was unloaded, drawer filled meanwhile
        assertTrue(r.deltas().isEmpty());
        assertTrue(r.gapBefore());
        assertTrue(r.baseline());
        assertEquals(new DeltaTracker.Delta(10, 0), t.update(Map.of("m:tuff", 5010L)).deltas().get("m:tuff"));
    }

    @Test
    void outflowAndRemovedItems() {
        DeltaTracker t = new DeltaTracker();
        t.update(Map.of("m:a", 10L, "m:b", 5L));
        DeltaTracker.Result r = t.update(Map.of("m:a", 4L));
        assertEquals(new DeltaTracker.Delta(0, 6), r.deltas().get("m:a"));
        assertEquals(new DeltaTracker.Delta(0, 5), r.deltas().get("m:b"));
    }
}
