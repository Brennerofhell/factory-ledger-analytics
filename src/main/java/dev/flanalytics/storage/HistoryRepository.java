package dev.flanalytics.storage;

import dev.flanalytics.history.PointHistory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * One file per measurement point inside the world folder. Writes happen on a single background thread,
 * atomically (temp file + move) with the previous version kept as .bak. Corrupt files are quarantined.
 */
public final class HistoryRepository {
    private final Path dir;
    private final Consumer<String> warn;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fla-history-writer");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Long> sizes = new HashMap<>();
    private final AtomicLong totalBytes = new AtomicLong();
    private final java.util.Set<String> locked = java.util.concurrent.ConcurrentHashMap.newKeySet(); // files of a newer version
    private final java.util.Set<String> warnedLocked = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public HistoryRepository(Path dir, Consumer<String> warn) {
        this.dir = dir;
        this.warn = warn;
    }

    public long totalBytes() { return totalBytes.get(); }

    public Map<String, PointHistory> loadAll() throws IOException {
        Files.createDirectories(dir);
        Map<String, PointHistory> out = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.getFileName().toString().endsWith(".fla"))::iterator) {
                PointHistory p = readWithFallback(f);
                if (p != null) {
                    out.put(p.id, p);
                    long sz = f.toFile().length();
                    sizes.put(f.getFileName().toString(), sz);
                    totalBytes.addAndGet(sz);
                }
            }
        }
        return out;
    }

    private PointHistory readWithFallback(Path f) {
        for (Path candidate : new Path[]{f, f.resolveSibling(f.getFileName() + ".bak")}) {
            if (!Files.exists(candidate)) continue;
            try {
                return HistoryCodec.decode(Files.readAllBytes(candidate));
            } catch (HistoryCodec.UnsupportedVersionException e) {
                warn.accept(candidate.getFileName() + ": " + e.getMessage() + " (written by a newer version; left untouched, point ignored)");
                locked.add(f.getFileName().toString()); // saveAsync refuses to write this point
                return null; // never overwrite data we cannot read
            } catch (IOException e) {
                warn.accept(candidate.getFileName() + " unreadable: " + e.getMessage());
                if (candidate == f) quarantine(f);
            }
        }
        return null;
    }

    private void quarantine(Path f) {
        try {
            Files.move(f, f.resolveSibling(f.getFileName() + ".corrupt-" + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    /** Takes a consistent snapshot now and writes it asynchronously. */
    public void saveAsync(PointHistory live) {
        String file = fileName(live.id);
        if (locked.contains(file)) {
            if (warnedLocked.add(file)) warn.accept(file + " was written by a newer version of the mod; new data for this point is kept in memory only and NOT saved, so the newer file is not overwritten");
            live.clearDirty();
            return;
        }
        PointHistory snap = live.snapshotForSave();
        live.clearDirty();
        writer.execute(() -> {
            try {
                write(snap);
            } catch (IOException | RuntimeException e) {
                live.markDirty(); // retry on the next save pass
                warn.accept("saving " + snap.id + " failed: " + e);
            }
        });
    }

    private void write(PointHistory snap) throws IOException {
        Files.createDirectories(dir);
        String name = fileName(snap.id);
        Path target = dir.resolve(name);
        Path tmp = dir.resolve(name + ".tmp");
        byte[] data = HistoryCodec.encode(snap);
        Files.write(tmp, data);
        if (Files.exists(target)) Files.copy(target, dir.resolve(name + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        synchronized (sizes) {
            long old = sizes.getOrDefault(name, 0L);
            sizes.put(name, (long) data.length);
            totalBytes.addAndGet(data.length - old);
        }
    }

    /** Waits for pending writes (server shutdown). */
    public void close(long timeoutSeconds) {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) warn.accept("history writer did not finish in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String fileName(String pointId) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(pointId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h) + ".fla";
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
