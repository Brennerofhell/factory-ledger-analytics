package dev.flanalytics.network;

/** One display unit of a chain: {@code perItem} base units make up one {@code itemId}. */
public record TierDto(String itemId, long perItem) {}
