package com.andye.warmod.phalanx;

import com.andye.warmod.WarMod;
import com.andye.warmod.block.PhalanxPart;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Owns persistent runtime chunk tickets for placed Phalanx structures.
 *
 * A 2x2 footprint may touch one, two, or four unique chunks. Multiple structure
 * blocks commonly resolve to the same ChunkPos, so this class must always
 * deduplicate positions before acquiring tickets.
 */
public final class PhalanxChunkTicketManager {
    private static final Map<ServerLevel, LevelState> ACTIVE =
        Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private PhalanxChunkTicketManager() {
    }

    /**
     * Registers all unique chunks touched by one turret.
     *
     * Registration is idempotent when the same UUID requests the same chunk set.
     */
    public static synchronized boolean register(
        final ServerLevel level,
        final UUID turretId,
        final BlockPos controller
    ) {
        LevelState state = ACTIVE.computeIfAbsent(level, ignored -> new LevelState());
        Set<ChunkPos> requested = touchedChunks(controller);

        if (requested.isEmpty() || requested.size() > 4) {
            WarMod.LOGGER.error(
                "Invalid Phalanx chunk footprint: turret={}, controller={}, chunks={}",
                turretId,
                controller,
                requested
            );
            return false;
        }

        Set<ChunkPos> existing = state.chunksByTurret.get(turretId);
        if (existing != null) {
            // Exact repeat: already registered successfully.
            if (existing.equals(requested)) {
                return true;
            }

            WarMod.LOGGER.error(
                "Phalanx {} attempted conflicting chunk registration: existing={}, requested={}",
                turretId,
                existing,
                requested
            );
            return false;
        }

        if (state.chunksByTurret.size() >= PhalanxConstants.MAX_TURRETS_PER_LEVEL) {
            return false;
        }

        List<ChunkPos> incremented = new ArrayList<>(requested.size());

        try {
            for (ChunkPos chunk : requested) {
                int previousReferences = state.chunkReferences.getOrDefault(chunk, 0);

                if (previousReferences == 0) {
                    level.getChunkSource().addTicketWithRadius(
                        PhalanxChunkTicketType.TURRET,
                        chunk,
                        0
                    );
                }

                state.chunkReferences.put(chunk, previousReferences + 1);
                incremented.add(chunk);
            }

            state.chunksByTurret.put(turretId, requested);

            if (SharedConstants.IS_RUNNING_IN_IDE) {
                WarMod.LOGGER.info(
                    "Phalanx {} registered {} unique chunk ticket(s): {}",
                    turretId,
                    requested.size(),
                    requested
                );
            }

            return true;
        } catch (RuntimeException exception) {
            // Roll back only references acquired by this attempt.
            for (int index = incremented.size() - 1; index >= 0; index--) {
                decrementReference(level, state, incremented.get(index));
            }

            state.chunksByTurret.remove(turretId);

            if (state.empty()) {
                ACTIVE.remove(level);
            }

            throw exception;
        }
    }

    /**
     * Releases exactly the unique chunk set stored at registration time.
     */
    public static synchronized void unregister(
        final ServerLevel level,
        final UUID turretId
    ) {
        LevelState state = ACTIVE.get(level);
        if (state == null) {
            return;
        }

        Set<ChunkPos> chunks = state.chunksByTurret.remove(turretId);
        if (chunks == null) {
            return;
        }

        for (ChunkPos chunk : chunks) {
            decrementReference(level, state, chunk);
        }

        if (state.empty()) {
            ACTIVE.remove(level);
        }
    }

    /**
     * Removes every actual ticket once, irrespective of how many turrets shared
     * the chunk.
     */
    public static synchronized void clear() {
        for (Map.Entry<ServerLevel, LevelState> levelEntry : ACTIVE.entrySet()) {
            ServerLevel level = levelEntry.getKey();
            LevelState state = levelEntry.getValue();

            for (ChunkPos chunk : List.copyOf(state.chunkReferences.keySet())) {
                level.getChunkSource().removeTicketWithRadius(
                    PhalanxChunkTicketType.TURRET,
                    chunk,
                    0
                );
            }
        }

        ACTIVE.clear();
    }

    /**
     * Calculates the unique chunks touched by all eight structure parts.
     */
    static Set<ChunkPos> touchedChunks(final BlockPos controller) {
        LinkedHashSet<ChunkPos> chunks = new LinkedHashSet<>();

        for (PhalanxPart part : PhalanxPart.values()) {
            BlockPos partPosition = controller.offset(part.offset());

            chunks.add(new ChunkPos(
                partPosition.getX() >> 4,
                partPosition.getZ() >> 4
            ));
        }

        return Set.copyOf(chunks);
    }

    private static void decrementReference(
        final ServerLevel level,
        final LevelState state,
        final ChunkPos chunk
    ) {
        Integer references = state.chunkReferences.get(chunk);
        if (references == null || references <= 0) {
            return;
        }

        if (references == 1) {
            state.chunkReferences.remove(chunk);

            level.getChunkSource().removeTicketWithRadius(
                PhalanxChunkTicketType.TURRET,
                chunk,
                0
            );
        } else {
            state.chunkReferences.put(chunk, references - 1);
        }
    }

    private static final class LevelState {
        private final Map<UUID, Set<ChunkPos>> chunksByTurret =
            new LinkedHashMap<>();

        private final Map<ChunkPos, Integer> chunkReferences =
            new HashMap<>();

        private boolean empty() {
            return chunksByTurret.isEmpty() && chunkReferences.isEmpty();
        }
    }
}
