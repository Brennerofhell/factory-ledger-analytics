package dev.flanalytics.config;

/** How compacting-drawer item chains (nugget/ingot/block) are counted. */
public enum CompactingMode {
    /** Count every slot as the original Factory Ledger does (duplicates possible). */
    OFF,
    /** Count the chain once, keep the item form that actually changed. */
    DEDUPLICATE,
    /** Convert the whole chain to one base unit and report in a selectable display unit. */
    NORMALIZED
}
