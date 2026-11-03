package dev.flanalytics.storage;

import dev.flanalytics.history.Bucket;
import dev.flanalytics.history.PointHistory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * File format: magic "FLAH", int version, int payloadLength, int crc32(payload), payload (gzip).
 * New versions get a new case in {@link #readPayload}; older files are migrated on read, newer
 * (unknown) versions are rejected and must NOT be overwritten by the caller.
 */
public final class HistoryCodec {
    public static final int MAGIC = 0x464C4148;
    public static final int VERSION = 1;
    private static final int MAX_PAYLOAD = 64 * 1024 * 1024;
    private static final int MAX_ITEMS = 1 << 16;

    public static class FormatException extends IOException {
        public FormatException(String m) { super(m); }
    }

    public static final class UnsupportedVersionException extends FormatException {
        public UnsupportedVersionException(int v) { super("unsupported history version " + v); }
    }

    private HistoryCodec() {}

    public static byte[] encode(PointHistory p) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
            Map<String, Integer> dict = new LinkedHashMap<>();
            for (var t : p.tiersView()) for (Bucket b : t) for (String id : b.items.keySet()) dict.putIfAbsent(id, dict.size());
            out.writeUTF(p.id);
            out.writeUTF(p.dimension);
            out.writeInt(p.x); out.writeInt(p.y); out.writeInt(p.z);
            out.writeUTF(p.mode);
            out.writeLong(p.lastSampleMillis);
            out.writeInt(dict.size());
            for (String id : dict.keySet()) out.writeUTF(id);
            for (var tier : p.tiersView()) {
                out.writeInt(tier.size());
                for (Bucket b : tier) {
                    out.writeLong(b.start); out.writeLong(b.width); out.writeLong(b.coveredMs);
                    out.writeLong(b.gapMs); out.writeInt(b.samples);
                    out.writeInt(b.items.size());
                    for (Map.Entry<String, long[]> e : b.items.entrySet()) {
                        out.writeInt(dict.get(e.getKey()));
                        out.writeLong(e.getValue()[0]); out.writeLong(e.getValue()[1]); out.writeLong(e.getValue()[2]);
                    }
                }
            }
        }
        byte[] payload = raw.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(payload);
        ByteArrayOutputStream file = new ByteArrayOutputStream(payload.length + 16);
        try (DataOutputStream out = new DataOutputStream(file)) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(payload.length); out.writeInt((int) crc.getValue());
            out.write(payload);
        }
        return file.toByteArray();
    }

    public static PointHistory decode(byte[] file) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(file))) {
            if (in.readInt() != MAGIC) throw new FormatException("bad magic");
            int version = in.readInt();
            int len = in.readInt();
            int crcStored = in.readInt();
            if (version > VERSION || version < 1) throw new UnsupportedVersionException(version);
            if (len < 0 || len > MAX_PAYLOAD || len != file.length - 16) throw new FormatException("bad length");
            byte[] payload = in.readNBytes(len);
            CRC32 crc = new CRC32();
            crc.update(payload);
            if ((int) crc.getValue() != crcStored) throw new FormatException("checksum mismatch");
            try (DataInputStream p = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(payload)))) {
                return readPayload(version, p);
            }
        } catch (java.io.EOFException | java.util.zip.ZipException | NegativeArraySizeException e) {
            throw new FormatException("truncated or corrupt: " + e.getMessage());
        }
    }

    private static PointHistory readPayload(int version, DataInputStream in) throws IOException {
        return switch (version) {
            case 1 -> readV1(in);
            default -> throw new UnsupportedVersionException(version);
        };
    }

    private static PointHistory readV1(DataInputStream in) throws IOException {
        PointHistory p = new PointHistory(in.readUTF());
        p.dimension = in.readUTF();
        p.x = in.readInt(); p.y = in.readInt(); p.z = in.readInt();
        p.mode = in.readUTF();
        p.lastSampleMillis = in.readLong();
        int n = in.readInt();
        if (n < 0 || n > MAX_ITEMS) throw new FormatException("dictionary size");
        List<String> dict = new ArrayList<>(n);
        for (int i = 0; i < n; i++) dict.add(in.readUTF());
        for (int t = 0; t < 3; t++) {
            int count = in.readInt();
            if (count < 0 || count > 5_000_000) throw new FormatException("bucket count");
            for (int i = 0; i < count; i++) {
                Bucket b = new Bucket(in.readLong(), in.readLong());
                b.coveredMs = in.readLong(); b.gapMs = in.readLong(); b.samples = in.readInt();
                int items = in.readInt();
                if (items < 0 || items > MAX_ITEMS) throw new FormatException("item count");
                for (int k = 0; k < items; k++) {
                    int idx = in.readInt();
                    if (idx < 0 || idx >= dict.size()) throw new FormatException("dictionary index");
                    long[] a = b.item(dict.get(idx));
                    a[0] = in.readLong(); a[1] = in.readLong(); a[2] = in.readLong();
                }
                p.putBucket(t, b);
            }
        }
        return p;
    }
}
