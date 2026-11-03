package dev.flanalytics.api;

import static org.junit.jupiter.api.Assertions.*;

import dev.flanalytics.config.CompactingMode;
import dev.flanalytics.monitoring.DeltaTracker.Delta;
import dev.flanalytics.monitoring.MeasurementSample;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiSnapshotTest {
    private static MeasurementSample s(long wall, boolean baseline, boolean gap, Map<String, Delta> d) {
        return new MeasurementSample("overworld@1,2,3", "minecraft:overworld", 1, 2, 3, 0, 0, 0, 0, wall, 20,
                CompactingMode.NORMALIZED, true, gap, baseline, Map.of("m:tuff", 500L), d);
    }

    @SuppressWarnings("unchecked")
    @Test
    void ratesIgnoreBaselineAndGaps() {
        long now = 1_000_000;
        List<MeasurementSample> list = List.of(
                s(now - 3000, true, false, Map.of()),
                s(now - 2000, false, false, Map.of("m:tuff", new Delta(30, 0))),
                s(now - 1000, false, false, Map.of("m:tuff", new Delta(30, 0))));
        Map<String, Object> snap = ApiSnapshot.build(list, now, 300_000);
        Map<String, Object> point = (Map<String, Object>) ((List<Object>) snap.get("points")).get(0);
        Map<String, Object> tuff = (Map<String, Object>) ((Map<String, Object>) point.get("items")).get("m:tuff");
        assertEquals(60L, tuff.get("inTotal"));
        assertEquals(1800.0, (double) tuff.get("inPerMinute"), 0.01); // 60 items in 2 s covered
        assertEquals("STOCK_DELTA", snap.get("kind"));
    }

    @Test
    void signatureIsStable() throws Exception {
        // RFC 4231 test case 2
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                Hmac.sign("Jefe", "what do ya want for nothing?"));
    }
}
