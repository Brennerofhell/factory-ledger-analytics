package dev.flanalytics.integration;

import com.jaquadro.minecraft.storagedrawers.config.CompTierRegistry;
import com.jaquadro.minecraft.storagedrawers.util.CompactingHelper;
import dev.flanalytics.monitoring.ChainNormalizer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Compacting rules straight from Storage Drawers: first its registry (config rules), then the real
 * crafting recipes of the loaded world. Only touch this class if storagedrawers is loaded.
 */
public final class McRuleSource implements ChainNormalizer.RuleSource {
    private final Level level;

    public McRuleSource(Level level) {
        this.level = level;
    }

    @Override
    public ChainNormalizer.Rule lowerOf(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl));
        CompTierRegistry.Record rec = CompTierRegistry.INSTANCE.findLowerTier(stack);
        if (rec != null && rec.convRate > 0) return new ChainNormalizer.Rule(idOf(rec.lower), rec.convRate);
        CompactingHelper.Result res = new CompactingHelper(level).findLowerTier(stack);
        if (res != null && res.getStack() != null && !res.getStack().isEmpty() && res.getSize() > 0) {
            return new ChainNormalizer.Rule(idOf(res.getStack()), res.getSize());
        }
        return null;
    }

    @Override
    public ChainNormalizer.Up upperOf(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl));
        String upper = null;
        int rate = 0;
        CompTierRegistry.Record rec = CompTierRegistry.INSTANCE.findHigherTier(stack);
        if (rec != null && rec.convRate > 0) {
            upper = idOf(rec.upper);
            rate = rec.convRate;
        } else {
            CompactingHelper.Result res = new CompactingHelper(level).findHigherTier(stack);
            if (res != null && res.getStack() != null && !res.getStack().isEmpty()) {
                upper = idOf(res.getStack());
                rate = res.getSize();
            }
        }
        if (upper == null || rate < 2) return null;
        // trust only rules that are consistent in both directions
        ChainNormalizer.Rule back = lowerOf(upper);
        return back != null && back.lowerId().equals(itemId) && back.rate() == rate ? new ChainNormalizer.Up(upper, rate) : null;
    }

    static String idOf(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }
}
