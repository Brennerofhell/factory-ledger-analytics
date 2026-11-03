package dev.flanalytics.client;

import dev.flanalytics.client.gui.AnalyticsScreen;
import dev.flanalytics.network.HistoryResponsePayload;
import net.minecraft.client.Minecraft;

/** Client-only entry points. Never referenced from server code paths except inside a lambda that runs on the client. */
public final class ClientHooks {
    /** Latest answer from the server; read by the screen every frame. */
    public static volatile HistoryResponsePayload latest;
    public static volatile boolean openRequested;
    private static volatile long openRequestedAt;

    private ClientHooks() {}

    /** Ask the server for data; the screen opens once the (permission-checked) answer arrives. */
    public static void requestOpen() {
        UiState.load();
        openRequested = true;
        openRequestedAt = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new dev.flanalytics.network.HistoryRequestPayload(UiState.selected, now - 3_600_000L, now, 300, true));
    }

    public static void onResponse(HistoryResponsePayload p) {
        latest = p;
        Minecraft mc = Minecraft.getInstance();
        if (openRequested && System.currentTimeMillis() - openRequestedAt < 5000L) {
            openRequested = false;
            mc.setScreen(new AnalyticsScreen());
        } else if (openRequested) { // server denied / never answered: forget the stale request
            openRequested = false;
        }
    }
}
