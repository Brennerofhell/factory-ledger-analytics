package dev.flanalytics.integration;

import dk.draaby.factoryledger.block.LedgerBlock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Knows where Factory Ledger blocks are, without scanning: filled by chunk-load and place events. */
public final class LedgerRegistry {
    private final Map<ResourceKey<Level>, Set<BlockPos>> byDimension = new HashMap<>();

    public synchronized Set<BlockPos> positions(ResourceKey<Level> dim) {
        return new HashSet<>(byDimension.getOrDefault(dim, Set.of()));
    }

    public synchronized void add(ResourceKey<Level> dim, BlockPos pos) {
        byDimension.computeIfAbsent(dim, k -> new HashSet<>()).add(pos.immutable());
    }

    public synchronized void remove(ResourceKey<Level> dim, BlockPos pos) {
        Set<BlockPos> s = byDimension.get(dim);
        if (s != null) s.remove(pos);
    }

    public synchronized void clear() {
        byDimension.clear();
    }

    public void onChunkLoad(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        ChunkAccess chunk = e.getChunk();
        // getBlockEntitiesPos() also contains block entities that are still pending deserialisation
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            if (chunk.getBlockState(pos).getBlock() instanceof LedgerBlock) add(level.dimension(), pos);
        }
    }

    public synchronized void onChunkUnload(ChunkEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        ChunkPos cp = e.getChunk().getPos();
        Set<BlockPos> s = byDimension.get(level.dimension());
        if (s != null) s.removeIf(p -> new ChunkPos(p).equals(cp));
    }

    public void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getLevel() instanceof ServerLevel level && e.getPlacedBlock().getBlock() instanceof LedgerBlock) {
            add(level.dimension(), e.getPos());
        }
    }
}
