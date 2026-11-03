package dev.flanalytics.network;

public record PointMeta(String id, String dimension, int x, int y, int z, String mode, long lastSampleMillis) {}
