package dev.flanalytics.monitoring;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves compacting chains (nugget -> ingot -> block) to one base unit using real rules.
 * No assumption of 9:1; every step uses the rate reported by the rule source.
 */
public final class ChainNormalizer {
    /** 1 item of the upper tier equals {@code rate} items of {@code lowerId}. */
    public record Rule(String lowerId, int rate) {}

    /** {@code rate} items of the asking tier craft one {@code upperId}. */
    public record Up(String upperId, int rate) {}

    /** One rung of a ladder: {@code perItem} base units make up one {@code itemId}. */
    public record Tier(String itemId, long perItem) {}

    public interface RuleSource {
        /** @return the next-lower tier rule for this item, or null if the item is a chain base / unknown. */
        Rule lowerOf(String itemId);

        /** @return the next-higher tier, or null. Optional: only used to offer ingots/blocks as display units. */
        default Up upperOf(String itemId) { return null; }
    }

    /** {@code perItem} base units make up one item; {@code baseId} is the chain's smallest item. */
    public record Unit(String baseId, long perItem) {}

    private static final int MAX_DEPTH = 16;

    private final RuleSource rules;
    private final Map<String, Unit> cache = new HashMap<>();

    public ChainNormalizer(RuleSource rules) {
        this.rules = rules;
    }

    public Unit resolve(String itemId) {
        Unit cached = cache.get(itemId);
        if (cached != null) return cached;
        String cur = itemId;
        long factor = 1;
        Set<String> seen = new HashSet<>();
        seen.add(cur);
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            Rule r = rules.lowerOf(cur);
            if (r == null || r.rate() <= 0 || r.lowerId() == null || !seen.add(r.lowerId())) break;
            long next;
            try {
                next = Math.multiplyExact(factor, (long) r.rate());
            } catch (ArithmeticException e) {
                break;
            }
            factor = next;
            cur = r.lowerId();
        }
        Unit u = new Unit(cur, factor);
        cache.put(itemId, u);
        return u;
    }

    private final Map<String, java.util.List<Tier>> ladders = new HashMap<>();

    /**
     * For a chain base item: [base x1, next tier xN, ...] using the real rates (max 4 rungs).
     * Empty if the item is not a chain base. Used by the GUI to show values in nuggets, ingots or blocks.
     */
    public java.util.List<Tier> ladder(String itemId) {
        java.util.List<Tier> cached = ladders.get(itemId);
        if (cached != null) return cached;
        java.util.List<Tier> out = new java.util.ArrayList<>();
        if (resolve(itemId).perItem() == 1) {
            out.add(new Tier(itemId, 1));
            String cur = itemId;
            long factor = 1;
            Set<String> seen = new HashSet<>();
            seen.add(cur);
            for (int i = 0; i < 3; i++) {
                Up u = rules.upperOf(cur);
                if (u == null || u.rate() <= 1 || u.upperId() == null || !seen.add(u.upperId())) break;
                try {
                    factor = Math.multiplyExact(factor, (long) u.rate());
                } catch (ArithmeticException e) {
                    break;
                }
                out.add(new Tier(u.upperId(), factor));
                cur = u.upperId();
            }
        }
        if (out.size() < 2) out = java.util.List.of();
        ladders.put(itemId, out);
        return out;
    }

    /** Must be called when recipes or compacting rules change. */
    public void invalidate() {
        cache.clear();
        ladders.clear();
    }
}
