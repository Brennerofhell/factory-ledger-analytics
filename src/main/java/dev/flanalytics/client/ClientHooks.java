package dev.flanalytics.client;

import dev.flanalytics.client.gui.AnalyticsScreen;
import dev.flanalytics.network.HistoryResponsePayload;
import net.minecraft.client.Minecraft;

/** Client-only entry points. Never referenced from server code paths except inside a lambda that runs on the client. */
public final class ClientHooks {
    /** Latest answer from the server; read by the screen every frame. */
    public static volatile HistoryResponsePayload latest;
    public static volatile boolean openRequested;

    private ClientHooks() {}

    public static void onResponse(HistoryResponsePayload p) {
        latest = p;
        Minecraft mc = Minecraft.getInstance();
        if (openRequested) {
            openRequested = false;
            mc.setScreen(new AnalyticsScreen());
        }
    }
}
