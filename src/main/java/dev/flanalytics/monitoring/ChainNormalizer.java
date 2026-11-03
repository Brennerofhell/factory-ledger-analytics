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

    public interface RuleSource {
        /** @return the next-lower tier rule for this item, or null if the item is a chain base / unknown. */
        Rule lowerOf(String itemId);
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

    /** Must be called when recipes or compacting rules change. */
    public void invalidate() {
        cache.clear();
    }
}
