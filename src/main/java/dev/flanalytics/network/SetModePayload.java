package dev.flanalytics.network;

import dev.flanalytics.FactoryLedgerAnalytics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Request to switch the compacting-drawer counting mode (OFF / DEDUPLICATE / NORMALIZED). Needs operator rights. */
public record SetModePayload(String mode) implements CustomPacketPayload {
    public static final Type<SetModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryLedgerAnalytics.MOD_ID, "set_mode"));
    public static final StreamCodec<FriendlyByteBuf, SetModePayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.mode, 32), buf -> new SetModePayload(buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
