package dev.flanalytics.client;

import dev.flanalytics.FactoryLedgerAnalytics;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Remembers the chart selection (sources, hidden items, range, type ...) across closing the window, leaving the
 * world and restarting the game. Stored client-side in config/factoryledgeranalytics-ui.properties; a missing or
 * broken file just means defaults.
 */
public final class UiState {
    public static String selected = "";
    public static String hidden = ""; // item ids separated by newline
    public static int range = 4;
    public static String type = "LINE", metric = "NET", unit = "PER_MINUTE";
    public static int displayTier;
    public static boolean panels = true;
    private static boolean loaded;

    private UiState() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("factoryledgeranalytics-ui.properties");
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.isRegularFile(f)) return;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(r);
            selected = p.getProperty("selected", "");
            hidden = p.getProperty("hidden", "").replace("\\n", "\n");
            range = Math.max(0, Math.min(9, Integer.parseInt(p.getProperty("range", "4"))));
            type = p.getProperty("type", "LINE");
            metric = p.getProperty("metric", "NET");
            unit = p.getProperty("unit", "PER_MINUTE");
            displayTier = Math.max(0, Math.min(3, Integer.parseInt(p.getProperty("displayTier", "0"))));
            panels = Boolean.parseBoolean(p.getProperty("panels", "true"));
        } catch (IOException | RuntimeException e) {
            FactoryLedgerAnalytics.LOGGER.warn("Could not read UI state, using defaults: {}", e.toString());
        }
    }

    public static synchronized void save() {
        Properties p = new Properties();
        p.setProperty("selected", selected);
        p.setProperty("hidden", hidden.replace("\n", "\\n"));
        p.setProperty("range", Integer.toString(range));
        p.setProperty("type", type);
        p.setProperty("metric", metric);
        p.setProperty("unit", unit);
        p.setProperty("displayTier", Integer.toString(displayTier));
        p.setProperty("panels", Boolean.toString(panels));
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            p.store(w, "Factory Ledger Analytics - window state");
        } catch (IOException e) {
            FactoryLedgerAnalytics.LOGGER.warn("Could not save UI state: {}", e.toString());
        }
    }
}
