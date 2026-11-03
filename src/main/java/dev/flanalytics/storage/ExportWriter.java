package dev.flanalytics.storage;

import dev.flanalytics.history.Bucket;
import dev.flanalytics.history.PointHistory;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CSV / JSON export of stored history. Pure functions over snapshots; no Minecraft types, no blocking of the server. */
public final class ExportWriter {
    public static final String CSV_HEADER = "timestamp_iso,timestamp_ms,bucket_ms,point_id,dimension,x,y,z,item_id,in,out,net,stock,covered_ms,gap_ms";

    private ExportWriter() {}

    /** Buckets of one point inside [from,to], oldest first, merged tiers. */
    private static List<Bucket> buckets(PointHistory p, long from, long to) {
        List<Bucket> all = new ArrayList<>();
        for (var tier : p.tiersView()) {
            for (Bucket b : tier) if (b.start + b.width > from && b.start <= to) all.add(b);
        }
        all.sort((a, b) -> Long.compare(a.start, b.start));
        return all;
    }

    public static void writeCsv(Writer w, List<PointHistory> points, long from, long to) throws IOException {
        w.write(CSV_HEADER);
        w.write('\n');
        for (PointHistory p : points) {
            for (Bucket b : buckets(p, from, to)) {
                if (b.items.isEmpty()) {
                    row(w, p, b, "", 0, 0, 0);
                } else {
                    for (Map.Entry<String, long[]> e : b.items.entrySet()) {
                        long[] a = e.getValue();
                        row(w, p, b, e.getKey(), a[0], a[1], a[2]);
                    }
                }
            }
        }
    }

    private static void row(Writer w, PointHistory p, Bucket b, String item, long in, long out, long stock) throws IOException {
        w.write(Instant.ofEpochMilli(b.start).toString()); w.write(',');
        w.write(Long.toString(b.start)); w.write(',');
        w.write(Long.toString(b.width)); w.write(',');
        w.write(csv(p.id)); w.write(',');
        w.write(csv(p.dimension)); w.write(',');
        w.write(p.x + "," + p.y + "," + p.z + ',');
        w.write(csv(item)); w.write(',');
        w.write(in + "," + out + "," + (in - out) + "," + stock + "," + b.coveredMs + "," + b.gapMs);
        w.write('\n');
    }

    /** RFC 4180 quoting; also neutralises spreadsheet formula injection from item/point names. */
    static String csv(String s) {
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    public static void writeJson(Writer w, List<PointHistory> points, long from, long to, long generatedAt) throws IOException {
        w.write("{\"version\":1,\"kind\":\"STOCK_DELTA\",\"generatedAtMillis\":" + generatedAt
                + ",\"fromMillis\":" + from + ",\"toMillis\":" + to + ",\"points\":[");
        boolean firstPoint = true;
        for (PointHistory p : points) {
            if (!firstPoint) w.write(',');
            firstPoint = false;
            w.write("{\"id\":" + json(p.id) + ",\"dimension\":" + json(p.dimension) + ",\"x\":" + p.x + ",\"y\":" + p.y
                    + ",\"z\":" + p.z + ",\"mode\":" + json(p.mode) + ",\"buckets\":[");
            boolean firstB = true;
            for (Bucket b : buckets(p, from, to)) {
                if (!firstB) w.write(',');
                firstB = false;
                w.write("{\"timestampMillis\":" + b.start + ",\"timestamp\":" + json(Instant.ofEpochMilli(b.start).toString())
                        + ",\"bucketMillis\":" + b.width + ",\"coveredMillis\":" + b.coveredMs + ",\"gapMillis\":" + b.gapMs + ",\"items\":{");
                boolean firstI = true;
                for (Map.Entry<String, long[]> e : b.items.entrySet()) {
                    if (!firstI) w.write(',');
                    firstI = false;
                    long[] a = e.getValue();
                    w.write(json(e.getKey()) + ":{\"in\":" + a[0] + ",\"out\":" + a[1] + ",\"net\":" + (a[0] - a[1]) + ",\"stock\":" + a[2] + "}");
                }
                w.write("}}");
            }
            w.write("]}");
        }
        w.write("]}\n");
    }

    static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
