package com.andye.warmod.silo;

import com.andye.warmod.block.MissileSiloStructure;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class MissileSiloChunkTicketManager {
    private static final Map<ServerLevel, Map<ChunkPos, Integer>> REFERENCES = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<UUID, Set<ChunkPos>>> SILOS = new WeakHashMap<>();

    private MissileSiloChunkTicketManager() {
    }

    public static synchronized boolean registerSilo(final ServerLevel level, final UUID siloId,
        final net.minecraft.core.BlockPos centre, final Direction facing) {
        Map<UUID, Set<ChunkPos>> silos = SILOS.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (!silos.containsKey(siloId) && silos.size() >= MissileSiloConstants.MAX_FORCE_LOADED_SILOS_PER_LEVEL) return false;
        if (silos.containsKey(siloId)) return true;
        Set<ChunkPos> chunks = MissileSiloStructure.footprintChunks(centre, facing);
        acquireFootprint(level, chunks);
        silos.put(siloId, chunks);
        return true;
    }

    public static synchronized void unregisterSilo(final ServerLevel level, final UUID siloId) {
        Map<UUID, Set<ChunkPos>> silos = SILOS.get(level);
        if (silos == null) return;
        Set<ChunkPos> chunks = silos.remove(siloId);
        if (chunks != null) releaseFootprint(level, chunks);
        if (silos.isEmpty()) SILOS.remove(level);
    }

    public static synchronized void acquireFootprint(final ServerLevel level, final Set<ChunkPos> chunks) {
        Map<ChunkPos, Integer> references = REFERENCES.computeIfAbsent(level, ignored -> new HashMap<>());
        for (ChunkPos chunk : chunks) {
            int count = references.getOrDefault(chunk, 0);
            if (count == 0) level.getChunkSource().addTicketWithRadius(MissileSiloChunkTicketType.SILO, chunk, 0);
            references.put(chunk, count + 1);
        }
    }

    public static synchronized void releaseFootprint(final ServerLevel level, final Set<ChunkPos> chunks) {
        Map<ChunkPos, Integer> references = REFERENCES.get(level);
        if (references == null) return;
        for (ChunkPos chunk : chunks) {
            int count = references.getOrDefault(chunk, 0);
            if (count <= 1) {
                references.remove(chunk);
                level.getChunkSource().removeTicketWithRadius(MissileSiloChunkTicketType.SILO, chunk, 0);
            } else references.put(chunk, count - 1);
        }
        if (references.isEmpty()) REFERENCES.remove(level);
    }

    public static synchronized void restoreSavedSilos(final ServerLevel level) {
        for (MissileSiloRecord record : MissileSiloSavedData.get(level).records())
            registerSilo(level, record.siloId(), record.centre(), Direction.NORTH);
    }

    public static synchronized void validateLoadedSilos(final ServerLevel level) {
        for (MissileSiloRecord record : java.util.List.copyOf(MissileSiloSavedData.get(level).records())) {
            ChunkPos centreChunk = new ChunkPos(record.centre().getX() >> 4, record.centre().getZ() >> 4);
            if (!level.getChunkSource().hasChunk(centreChunk.x(), centreChunk.z())) continue;
            boolean valid = level.getBlockEntity(record.centre()) instanceof com.andye.warmod.block.entity.MissileSiloBlockEntity silo
                && silo.siloId().equals(record.siloId())
                && level.getBlockState(record.centre()).is(com.andye.warmod.block.ModBlocks.MISSILE_SILO);
            if (!valid) {
                unregisterSilo(level, record.siloId());
                MissileSiloSavedData.get(level).remove(record.siloId());
            }
        }
    }
    public static synchronized void clearRuntimeState() {
        for (Map.Entry<ServerLevel, Map<ChunkPos, Integer>> entry : REFERENCES.entrySet())
            for (ChunkPos chunk : entry.getValue().keySet())
                entry.getKey().getChunkSource().removeTicketWithRadius(MissileSiloChunkTicketType.SILO, chunk, 0);
        REFERENCES.clear();
        SILOS.clear();
    }

    public static synchronized int registeredCount(final ServerLevel level) {
        Map<UUID, Set<ChunkPos>> silos = SILOS.get(level);
        return silos == null ? 0 : silos.size();
    }
}
