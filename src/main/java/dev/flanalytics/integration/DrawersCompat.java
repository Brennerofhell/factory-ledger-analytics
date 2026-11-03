package dev.flanalytics.integration;

import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawersComp;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Isolated so Storage Drawers classes are only loaded when that mod is present. */
public final class DrawersCompat {
    private DrawersCompat() {}

    public static boolean isCompacting(BlockEntity be) {
        return be instanceof BlockEntityDrawersComp;
    }
}
