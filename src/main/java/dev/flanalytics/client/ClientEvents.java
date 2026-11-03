package dev.flanalytics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.flanalytics.FactoryLedgerAnalytics;
import dev.flanalytics.network.HistoryRequestPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    public static final KeyMapping OPEN = new KeyMapping("key.factoryledgeranalytics.open",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.factoryledgeranalytics");

    private ClientEvents() {}

    @EventBusSubscriber(modid = FactoryLedgerAnalytics.MOD_ID, value = Dist.CLIENT)
    public static final class Mod {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(OPEN);
        }
    }

    @EventBusSubscriber(modid = FactoryLedgerAnalytics.MOD_ID, value = Dist.CLIENT)
    public static final class Game {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (mc.player == null || mc.screen != null) continue;
                ClientHooks.openRequested = true; // screen opens once the server has answered (and allowed it)
                long now = System.currentTimeMillis();
                PacketDistributor.sendToServer(new HistoryRequestPayload("", now - 3_600_000L, now, 300, true));
            }
        }
    }
}
