package dev.flanalytics.history;

import static org.junit.jupiter.api.Assertions.*;

import dev.flanalytics.config.CompactingMode;
import dev.flanalytics.monitoring.DeltaTracker.Delta;
import dev.flanalytics.monitoring.MeasurementSample;
import dev.flanalytics.storage.HistoryCodec;
import dev.flanalytics.storage.HistoryRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryTest {
    private static final long T0 = 1_700_000_000_000L;
    private static final String ID = "minecraft:overworld@1,2,3";

    private static MeasurementSample sample(long wall, boolean baseline, long in, long stock) {
        Map<String, Delta> d = baseline || in == 0 ? Map.of() : Map.of("m:tuff", new Delta(in, 0));
        return new MeasurementSample(ID, "minecraft:overworld", 1, 2, 3, 0, 0, 0, 0, wall, 20,
                CompactingMode.NORMALIZED, true, false, baseline, Map.of("m:tuff", stock), d);
    }

    private static long totalIn(PointHistory p) {
        long sum = 0;
        for (Bucket b : p.query(0, Long.MAX_VALUE / 2, 0)) sum += b.items.getOrDefault("m:tuff", new long[3])[0];
        return sum;
    }

    @Test
    void aggregatesIntoMinuteBuckets() {
        PointHistory p = new PointHistory(ID);
        p.add(sample(T0, true, 0, 100));
        for (int i = 1; i <= 120; i++) p.add(sample(T0 + i * 1000L, false, 2, 100 + i * 2));
        assertEquals(240, totalIn(p));
        assertTrue(p.query(0, Long.MAX_VALUE / 2, 0).size() <= 3); // 2 minutes of data, 1-min buckets
    }

    @Test
    void compactionKeepsTotalsAndBoundsSize() {
        PointHistory p = new PointHistory(ID);
        p.add(sample(T0, true, 0, 0));
        long total = 0;
        for (int i = 1; i <= 3 * 24 * 60; i++) { // 3 days, one sample per minute
            p.add(sample(T0 + i * 60_000L, false, 3, i * 3L));
            total += 3;
        }
        int before = p.bucketCount();
        p.compact(T0 + 3 * 86_400_000L, 90);
        assertTrue(p.bucketCount() < before);
        assertEquals(total, totalIn(p));
    }

    @Test
    void retentionDropsOldHourlyData() {
        PointHistory p = new PointHistory(ID);
        p.add(sample(T0, true, 0, 0));
        p.add(sample(T0 + 1000, false, 5, 5));
        p.compact(T0 + 400L * 86_400_000L, 30); // 400 days later, retention 30 d
        assertEquals(0, p.bucketCount());
    }

    @Test
    void restartCreatesGapNotProduction() {
        PointHistory p = new PointHistory(ID);
        p.add(sample(T0, true, 0, 100));
        p.add(sample(T0 + 1000, false, 1, 101));
        // server was down for 2 h; fresh tracker => baseline sample with a big stock jump
        p.add(sample(T0 + 7_200_000L, true, 0, 9000));
        assertEquals(1, totalIn(p)); // the +8899 while down is NOT counted as production
        long gap = p.query(0, Long.MAX_VALUE / 2, 0).stream().mapToLong(b -> b.gapMs).sum();
        assertTrue(gap > 7_000_000L);
    }

    @Test
    void codecRoundTrip() throws Exception {
        PointHistory p = new PointHistory(ID);
        p.add(sample(T0, true, 0, 10));
        for (int i = 1; i < 50; i++) p.add(sample(T0 + i * 1000L, false, 1, 10 + i));
        PointHistory q = HistoryCodec.decode(HistoryCodec.encode(p));
        assertEquals(p.id, q.id);
        assertEquals(totalIn(p), totalIn(q));
        assertEquals(p.bucketCount(), q.bucketCount());
    }

    @Test
    void corruptedFileIsRejected() throws Exception {
        PointHistory p = new PointHistory(ID);
        p.add(sample(T0, true, 0, 10));
        byte[] data = HistoryCodec.encode(p);
        data[data.length - 3] ^= 0x55;
        assertThrows(HistoryCodec.FormatException.class, () -> HistoryCodec.decode(data));
        assertThrows(HistoryCodec.FormatException.class, () -> HistoryCodec.decode(new byte[]{1, 2, 3}));
        byte[] truncated = java.util.Arrays.copyOf(HistoryCodec.encode(p), 20);
        assertThrows(HistoryCodec.FormatException.class, () -> HistoryCodec.decode(truncated));
    }

    @Test
    void newerVersionIsNotMisread() throws Exception {
        PointHistory p = new PointHistory(ID);
        p.add(sample(T0, true, 0, 10));
        byte[] data = HistoryCodec.encode(p);
        data[7] = 99; // version field (big endian int at offset 4..7)
        assertThrows(HistoryCodec.UnsupportedVersionException.class, () -> HistoryCodec.decode(data));
    }

    @Test
    void repositorySurvivesRestartAndCorruption(@TempDir Path dir) throws Exception {
        List<String> warnings = new ArrayList<>();
        HistoryRepository repo = new HistoryRepository(dir, warnings::add);
        PointHistory p = new PointHistory(ID);
        p.add(sample(T0, true, 0, 10));
        p.add(sample(T0 + 1000, false, 7, 17));
        repo.saveAsync(p);
        p.add(sample(T0 + 2000, false, 3, 20));
        repo.saveAsync(p); // second save creates a .bak of the first
        repo.close(10);

        HistoryRepository again = new HistoryRepository(dir, warnings::add); // "server restart"
        PointHistory loaded = again.loadAll().get(ID);
        assertEquals(10, totalIn(loaded));
        again.close(5);

        // corrupt the main file: the .bak (first save) must take over, the broken file is quarantined
        Path main = dir.resolve(HistoryRepository.fileName(ID));
        Files.write(main, new byte[]{9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9});
        HistoryRepository third = new HistoryRepository(dir, warnings::add);
        PointHistory fallback = third.loadAll().get(ID);
        assertNotNull(fallback);
        assertEquals(7, totalIn(fallback));
        assertFalse(warnings.isEmpty());
        third.close(5);
    }

    @Test
    void downsamplingKeepsTotals() {
        PointHistory p = new PointHistory(ID);
        p.add(sample(T0, true, 0, 0));
        for (int i = 1; i <= 600; i++) p.add(sample(T0 + i * 60_000L, false, 1, i));
        List<Bucket> q = p.query(T0, T0 + 600 * 60_000L, 50);
        assertTrue(q.size() <= 51);
        assertEquals(600, q.stream().mapToLong(b -> b.items.get("m:tuff")[0]).sum());
    }
}
