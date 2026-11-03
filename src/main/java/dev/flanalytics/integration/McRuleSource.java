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

    /** Human-readable diagnosis of what Storage Drawers reports for an item (used by /flanalytics rules). */
    public String explain(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return "unknown item " + itemId;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl));
        StringBuilder sb = new StringBuilder(itemId).append('\n');
        var regLow = CompTierRegistry.INSTANCE.findLowerTier(stack);
        var regUp = CompTierRegistry.INSTANCE.findHigherTier(stack);
        sb.append(" registry lower: ").append(regLow == null ? "-" : idOf(regLow.lower) + " x" + regLow.convRate).append('\n');
        sb.append(" registry upper: ").append(regUp == null ? "-" : idOf(regUp.upper) + " x" + regUp.convRate).append('\n');
        var hLow = new CompactingHelper(level).findLowerTier(stack);
        var hUp = new CompactingHelper(level).findHigherTier(stack);
        sb.append(" recipes lower:  ").append(hLow == null || hLow.getStack().isEmpty() ? "-" : idOf(hLow.getStack()) + " x" + hLow.getSize()).append('\n');
        sb.append(" recipes upper:  ").append(hUp == null || hUp.getStack().isEmpty() ? "-" : idOf(hUp.getStack()) + " x" + hUp.getSize()).append('\n');
        var low = lowerOf(itemId);
        var up = upperOf(itemId);
        sb.append(" => used lower: ").append(low == null ? "-" : low.lowerId() + " x" + low.rate()).append('\n');
        sb.append(" => used upper: ").append(up == null ? "- (no consistent higher tier)" : up.upperId() + " x" + up.rate());
        return sb.toString();
    }

    static String idOf(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }
}
