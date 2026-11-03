package dev.flanalytics.client;

import dev.flanalytics.FactoryLedgerAnalytics;
import dk.draaby.factoryledger.client.LedgerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Adds an "Analytics" button next to the "Links" tab of the Factory Ledger screen using screen events only
 * (no mixin, nothing in the original mod is changed). If the layout is not recognised, nothing is added.
 */
@EventBusSubscriber(modid = FactoryLedgerAnalytics.MOD_ID, value = Dist.CLIENT)
public final class LedgerScreenIntegration {
    private static Button button;

    private LedgerScreenIntegration() {}

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post e) {
        button = null;
        if (!(e.getScreen() instanceof LedgerScreen)) return;
        for (var l : e.getListenersList()) {
            if (l instanceof Button b && b.getMessage().getString().equals("Links")) {
                button = Button.builder(Component.literal("Analytics"), x -> ClientHooks.requestOpen())
                        .bounds(b.getX() + b.getWidth() + 4, b.getY(), 62, b.getHeight()).build();
                return;
            }
        }
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post e) {
        if (button != null && e.getScreen() instanceof LedgerScreen) {
            button.render(e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void click(ScreenEvent.MouseButtonPressed.Pre e) {
        if (button != null && e.getScreen() instanceof LedgerScreen && e.getButton() == 0
                && button.mouseClicked(e.getMouseX(), e.getMouseY(), 0)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void closed(ScreenEvent.Closing e) {
        if (e.getScreen() instanceof LedgerScreen) button = null;
    }
}
