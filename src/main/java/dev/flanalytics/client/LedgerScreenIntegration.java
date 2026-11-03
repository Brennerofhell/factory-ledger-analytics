package dev.flanalytics.client;

import dev.flanalytics.FactoryLedgerAnalytics;
import dk.draaby.factoryledger.client.LedgerScreen;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Adds an "Analytics" button next to the "Links" tab of the Factory Ledger screen using screen events only
 * (no mixin, nothing in the original mod is changed). If no "Links" button is found by name, the second button of
 * the left-most row is used; the buttons found are logged once so a changed layout can be diagnosed.
 */
@EventBusSubscriber(modid = FactoryLedgerAnalytics.MOD_ID, value = Dist.CLIENT)
public final class LedgerScreenIntegration {
    private static Button button;
    private static boolean logged;

    private LedgerScreenIntegration() {}

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post e) {
        button = null;
        if (!(e.getScreen() instanceof LedgerScreen)) return;
        List<Button> buttons = new ArrayList<>();
        for (var l : e.getListenersList()) if (l instanceof Button b) buttons.add(b);
        if (!logged) {
            logged = true;
            StringBuilder sb = new StringBuilder();
            for (Button b : buttons) sb.append('[').append(b.getMessage().getString()).append(" @").append(b.getX()).append(',').append(b.getY()).append(']');
            FactoryLedgerAnalytics.LOGGER.info("Factory Ledger screen buttons: {}", sb);
        }
        Button links = null;
        for (Button b : buttons) if (b.getMessage().getString().trim().equalsIgnoreCase("Links")) links = b;
        if (links == null && buttons.size() >= 2) {
            // fallback: the two tab buttons are the left-most ones on the same row
            buttons.sort(Comparator.comparingInt(Button::getY).thenComparingInt(Button::getX));
            Button first = buttons.get(0);
            for (Button b : buttons) if (b.getY() == first.getY() && b.getX() > first.getX()) { links = b; break; }
        }
        if (links == null) {
            FactoryLedgerAnalytics.LOGGER.warn("Analytics button not added: no tab button found on the Factory Ledger screen.");
            return;
        }
        button = Button.builder(Component.literal("Analytics"), x -> ClientHooks.requestOpen())
                .bounds(links.getX() + links.getWidth() + 4, links.getY(), 62, links.getHeight()).build();
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
