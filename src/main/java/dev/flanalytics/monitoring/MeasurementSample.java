package dev.flanalytics.monitoring;

import dev.flanalytics.config.CompactingMode;
import java.util.Map;

/** One server-side measurement of one measurement point (an inventory linked in a Factory Ledger). */
public record MeasurementSample(
        String pointId, String dimension, int x, int y, int z, int ledgerX, int ledgerY, int ledgerZ,
        long gameTime, long wallMillis, int intervalTicks, CompactingMode mode, boolean compactingBlock,
        boolean gapBefore, boolean baseline, Map<String, Long> stock, Map<String, DeltaTracker.Delta> deltas) {
    /** Always STOCK_DELTA for inventory links: changes of stored amounts, not gross production. */
    public String kind() { return "STOCK_DELTA"; }
}
