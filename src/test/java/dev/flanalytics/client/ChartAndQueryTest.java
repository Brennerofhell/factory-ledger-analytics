package dev.flanalytics.client;

import static org.junit.jupiter.api.Assertions.*;

import dev.flanalytics.client.charts.ChartModel;
import dev.flanalytics.client.charts.ChartModel.Metric;
import dev.flanalytics.client.charts.ChartModel.Unit;
import dev.flanalytics.config.CompactingMode;
import dev.flanalytics.history.PointHistory;
import dev.flanalytics.monitoring.DeltaTracker.Delta;
import dev.flanalytics.monitoring.MeasurementSample;
import dev.flanalytics.network.ChartBucket;
import dev.flanalytics.network.QueryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChartAndQueryTest {
    private static ChartBucket bucket(long start, long covered, long gap, long in, long out) {
        return new ChartBucket(start, 60_000, covered, gap, new int[]{0}, new long[]{in}, new long[]{out}, new long[]{50});
    }

    @Test
    void ratesUseCoveredTimeAndGapsAreNaN() {
        List<ChartBucket> bs = List.of(bucket(0, 60_000, 0, 120, 0), bucket(60_000, 0, 60_000, 0, 0), bucket(120_000, 30_000, 30_000, 30, 0));
        var s = ChartModel.rates(bs, List.of("m:tuff"), 0, Metric.NET, Unit.PER_MINUTE);
        assertEquals(120.0, s.v()[0], 1e-9);
        assertTrue(Double.isNaN(s.v()[1]));             // gap is NOT drawn as zero
        assertEquals(60.0, s.v()[2], 1e-9);             // 30 items in 30 s covered = 60/min, gap excluded
        assertEquals(7200.0, ChartModel.rates(bs, List.of("m:tuff"), 0, Metric.NET, Unit.PER_HOUR).v()[0], 1e-9);
    }

    @Test
    void cumulativeIgnoresGaps() {
        List<ChartBucket> bs = List.of(bucket(0, 60_000, 0, 10, 0), bucket(60_000, 0, 60_000, 0, 0), bucket(120_000, 60_000, 0, 5, 2));
        var s = ChartModel.cumulative(bs, List.of("m:tuff"), 0);
        assertArrayEquals(new double[]{10, 10, 13}, s.v(), 1e-9);
    }

    @Test
    void statsMinMaxAvg() {
        List<ChartBucket> bs = List.of(bucket(0, 60_000, 0, 60, 0), bucket(60_000, 60_000, 0, 120, 0), bucket(120_000, 0, 60_000, 0, 0));
        var st = ChartModel.stats(bs, 0, Metric.NET, Unit.PER_MINUTE);
        assertEquals(60, st.min(), 1e-9);
        assertEquals(120, st.max(), 1e-9);
        assertEquals(90, st.avg(), 1e-9);
        assertEquals(60_000, st.gapMs());
    }

    @Test
    void zoomAndPanStayInBounds() {
        long now = 1_000_000;
        long[] z = ChartModel.zoom(now - 100_000, now, 1.0, 0.5, 20_000, 10_000_000, now);
        assertEquals(50_000, z[1] - z[0]);
        assertTrue(z[1] <= now);
        long[] p = ChartModel.pan(z[0], z[1], 999_999, now);
        assertEquals(now, p[1]);
        long[] tiny = ChartModel.zoom(0, 30_000, 0.5, 0.1, 20_000, 10_000_000, now);
        assertEquals(20_000, tiny[1] - tiny[0]);
    }

    @Test
    void niceTicksCoverRange() {
        double[] t = ChartModel.niceTicks(0, 97, 5);
        assertTrue(t[0] <= 0 && t[t.length - 1] >= 97);
        assertTrue(t.length <= 12);
    }

    private static MeasurementSample sample(String id, long wall, boolean baseline, long in) {
        return new MeasurementSample(id, "d", 0, 0, 0, 0, 0, 0, 0, wall, 20, CompactingMode.NORMALIZED, true, false, baseline,
                Map.of("m:tuff", 100L), baseline || in == 0 ? Map.of() : Map.of("m:tuff", new Delta(in, 0)));
    }

    @Test
    void queryClampsRangeAndLimitsBuckets() {
        PointHistory h = new PointHistory("p");
        long now = 1_700_000_000_000L;
        h.add(sample("p", now - 5000, true, 0));
        for (int i = 0; i < 3000; i++) h.add(sample("p", now - 2_000_000 + i * 600L, false, 1));
        var r = QueryService.query(h, List.of(), "p", 0, now + 10_000_000, 10_000, now);
        assertTrue(r.to() <= now);
        assertTrue(r.buckets().size() <= QueryService.MAX_BUCKETS + 1);
        assertFalse(r.live());
    }

    @Test
    void shortRangeUsesLiveSamplesAndMarksGaps() {
        long now = 1_700_000_000_000L;
        List<MeasurementSample> recent = new ArrayList<>();
        recent.add(sample("p", now - 50_000, true, 0));
        recent.add(sample("p", now - 49_000, false, 4));
        recent.add(sample("p", now - 20_000, true, 0)); // 29 s without samples, then new baseline
        recent.add(sample("p", now - 19_000, false, 4));
        var r = QueryService.query(null, recent, "p", now - 60_000, now, 100, now);
        assertTrue(r.live());
        long gap = r.buckets().stream().mapToLong(ChartBucket::gapMs).sum();
        assertTrue(gap > 25_000);
    }
}
