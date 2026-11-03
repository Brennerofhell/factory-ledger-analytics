package dev.flanalytics.storage;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.flanalytics.FactoryLedgerAnalytics;
import dev.flanalytics.config.AnalyticsConfig;
import dev.flanalytics.history.PointHistory;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /flanalytics export <csv|json> [hours]  - operators only; the file is written on a background thread. */
public final class ExportService {
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fla-export");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean();

    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onCommands);
    }

    private void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("flanalytics").requires(s -> s.hasPermission(2))
                .then(Commands.literal("rules")
                        .then(Commands.argument("item", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .executes(c -> {
                                    if (!net.neoforged.fml.ModList.get().isLoaded("storagedrawers")) {
                                        c.getSource().sendFailure(Component.literal("Storage Drawers is not loaded."));
                                        return 0;
                                    }
                                    String id = net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "item").toString();
                                    String text = new dev.flanalytics.integration.McRuleSource(c.getSource().getLevel()).explain(id);
                                    c.getSource().sendSuccess(() -> Component.literal(text), false);
                                    return 1;
                                })))
                .then(Commands.literal("export")
                        .then(Commands.argument("format", StringArgumentType.word())
                                .executes(c -> run(c.getSource(), StringArgumentType.getString(c, "format"), 24))
                                .then(Commands.argument("hours", IntegerArgumentType.integer(1, 24 * 400))
                                        .executes(c -> run(c.getSource(), StringArgumentType.getString(c, "format"), IntegerArgumentType.getInteger(c, "hours")))))));
    }

    private int run(CommandSourceStack src, String format, int hours) {
        if (!AnalyticsConfig.EXPORT_ENABLED.get()) {
            src.sendFailure(Component.literal("Export is disabled in the config (exportEnabled=false)."));
            return 0;
        }
        boolean csv = format.equalsIgnoreCase("csv");
        if (!csv && !format.equalsIgnoreCase("json")) {
            src.sendFailure(Component.literal("Format must be csv or json."));
            return 0;
        }
        if (!running.compareAndSet(false, true)) {
            src.sendFailure(Component.literal("An export is already running."));
            return 0;
        }
        MinecraftServer server = src.getServer();
        long to = System.currentTimeMillis(), from = to - hours * 3_600_000L;
        List<PointHistory> snaps = FactoryLedgerAnalytics.HISTORY.snapshots(); // quick in-memory copy on the server thread
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("factoryledgeranalytics").resolve("exports");
        String name = "export-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + (csv ? ".csv" : ".json");
        src.sendSuccess(() -> Component.literal("Export started (" + hours + " h, " + snaps.size() + " points)..."), false);
        POOL.execute(() -> {
            String msg;
            try {
                Files.createDirectories(dir);
                Path out = dir.resolve(name);
                try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                    if (csv) ExportWriter.writeCsv(w, snaps, from, to);
                    else ExportWriter.writeJson(w, snaps, from, to, to);
                }
                msg = "Export written: " + out.toAbsolutePath();
            } catch (IOException | RuntimeException ex) {
                msg = "Export failed: " + ex;
            } finally {
                running.set(false);
            }
            String finalMsg = msg;
            server.execute(() -> src.sendSuccess(() -> Component.literal(finalMsg), false));
        });
        return 1;
    }
}
