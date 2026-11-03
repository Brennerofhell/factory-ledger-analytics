package dev.flanalytics.storage;

import static org.junit.jupiter.api.Assertions.*;

import dev.flanalytics.config.CompactingMode;
import dev.flanalytics.history.PointHistory;
import dev.flanalytics.monitoring.DeltaTracker.Delta;
import dev.flanalytics.monitoring.MeasurementSample;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExportWriterTest {
    private static final long T0 = 1_700_000_000_000L;

    private static PointHistory history() {
        PointHistory p = new PointHistory("minecraft:overworld@1,2,3");
        p.add(s(T0, true, 0));
        p.add(s(T0 + 1000, false, 5));
        p.add(s(T0 + 7_200_000L, true, 0)); // 2 h outage -> gap
        return p;
    }

    private static MeasurementSample s(long wall, boolean baseline, long in) {
        return new MeasurementSample("minecraft:overworld@1,2,3", "minecraft:overworld", 1, 2, 3, 0, 0, 0, 0, wall, 20,
                CompactingMode.NORMALIZED, true, false, baseline, Map.of("m:=tuff,\"x\"", 10L),
                baseline || in == 0 ? Map.of() : Map.of("m:=tuff,\"x\"", new Delta(in, 0)));
    }

    @Test
    void csvHasHeaderAllRowsEscapingAndGap() throws Exception {
        StringWriter w = new StringWriter();
        ExportWriter.writeCsv(w, List.of(history()), 0, Long.MAX_VALUE / 2);
        String[] lines = w.toString().split("\n");
        assertEquals(ExportWriter.CSV_HEADER, lines[0]);
        assertTrue(lines.length >= 3);
        assertTrue(w.toString().contains("\"m:=tuff,\"\"x\"\"\"")); // quoted, quotes doubled
        long gapSum = 0;
        for (int i = 1; i < lines.length; i++) {
            String[] cols = lines[i].split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            assertEquals(15, cols.length);
            gapSum += Long.parseLong(cols[14]);
        }
        assertTrue(gapSum > 7_000_000L); // outage is exported as gap_ms, not as production
    }

    @Test
    void csvNeutralisesFormulaInjection() {
        assertEquals("'=SUM(A1)", ExportWriter.csv("=SUM(A1)"));
        assertEquals("'@x", ExportWriter.csv("@x"));
    }

    @Test
    void jsonIsBalancedAndEscaped() throws Exception {
        StringWriter w = new StringWriter();
        ExportWriter.writeJson(w, List.of(history()), 0, Long.MAX_VALUE / 2, T0);
        String j = w.toString();
        assertTrue(j.contains("\"m:=tuff,\\\"x\\\"\""));
        int depth = 0;
        boolean inStr = false, esc = false;
        for (char c : j.toCharArray()) {
            if (inStr) { if (esc) esc = false; else if (c == '\\') esc = true; else if (c == '"') inStr = false; continue; }
            if (c == '"') inStr = true;
            else if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            assertTrue(depth >= 0);
        }
        assertEquals(0, depth);
        assertFalse(inStr);
    }

    @Test
    void rangeFilterExcludesOldBuckets() throws Exception {
        StringWriter w = new StringWriter();
        ExportWriter.writeCsv(w, List.of(history()), T0 + 3_600_000L, Long.MAX_VALUE / 2);
        assertEquals(2, w.toString().split("\n").length); // header + the bucket after the outage
    }
}
