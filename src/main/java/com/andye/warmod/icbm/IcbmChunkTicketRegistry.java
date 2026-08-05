package com.andye.warmod.icbm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Shared reference counts for every ICBM ticket owner. */
public final class IcbmChunkTicketRegistry {
    /*
     * Minecraft's radius argument controls ticket level: radius 0 produces a
     * level-33 accessible chunk, while radius 2 produces level 31 so entities
     * and block entities actually tick. Final-approach air defences and the
     * IncomingWarheadEntity therefore require radius 2 tickets.
     */
    private static final int SIMULATION_TICKET_RADIUS = 2;

    private static final Map<ServerLevel, Map<ChunkPos, Integer>> REFERENCES =
        new WeakHashMap<>();

    private IcbmChunkTicketRegistry() {
    }

    public static synchronized void acquire(
        final ServerLevel level,
        final ChunkPos position
    ) {
        Map<ChunkPos, Integer> references = REFERENCES.computeIfAbsent(
            level,
            ignored -> new HashMap<>()
        );
        int count = references.getOrDefault(position, 0);

        if (count == 0) {
            level.getChunkSource().addTicketWithRadius(
                IcbmChunkTicketType.ICBM,
                position,
                SIMULATION_TICKET_RADIUS
            );
        }

        references.put(position, count + 1);
    }

    public static synchronized void release(
        final ServerLevel level,
        final ChunkPos position
    ) {
        Map<ChunkPos, Integer> references = REFERENCES.get(level);

        if (references == null) {
            return;
        }

        int count = references.getOrDefault(position, 0);

        if (count <= 1) {
            references.remove(position);
            level.getChunkSource().removeTicketWithRadius(
                IcbmChunkTicketType.ICBM,
                position,
                SIMULATION_TICKET_RADIUS
            );
        } else {
            references.put(position, count - 1);
        }

        if (references.isEmpty()) {
            REFERENCES.remove(level);
        }
    }

    public static void acquireAll(
        final ServerLevel level,
        final Set<ChunkPos> positions
    ) {
        for (ChunkPos position : positions) {
            acquire(level, position);
        }
    }

    public static void releaseAll(
        final ServerLevel level,
        final Set<ChunkPos> positions
    ) {
        for (ChunkPos position : positions) {
            release(level, position);
        }
    }

    public static ChunkPos chunk(final Vec3 position) {
        return new ChunkPos(
            (int)Math.floor(position.x) >> 4,
            (int)Math.floor(position.z) >> 4
        );
    }

    public static Set<ChunkPos> window(
        final ChunkPos center,
        final int radius
    ) {
        Set<ChunkPos> positions = new HashSet<>();
        addWindow(positions, center, radius);
        return positions;
    }

    public static void addWindow(
        final Set<ChunkPos> positions,
        final ChunkPos center,
        final int radius
    ) {
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                positions.add(new ChunkPos(x, z));
            }
        }
    }

    /**
     * True only once every requested chunk is loaded and entity-ticking. Merely
     * being present at level 33 is insufficient: missiles would stop ticking
     * and block-entity air defences would remain asleep until a player arrived.
     */
    public static boolean allLoaded(
        final ServerLevel level,
        final Set<ChunkPos> chunks
    ) {
        for (ChunkPos chunk : chunks) {
            if (!level.getChunkSource().hasChunk(chunk.x(), chunk.z())
                || !level.areEntitiesActuallyLoadedAndTicking(chunk)) {
                return false;
            }
        }

        return true;
    }

    public static void addSegmentWindow(
        final Set<ChunkPos> output,
        final Vec3 from,
        final Vec3 to,
        final int radius,
        final double spacing
    ) {
        if (output == null
            || from == null
            || to == null
            || !from.isFinite()
            || !to.isFinite()
            || radius < 0
            || !Double.isFinite(spacing)
            || spacing <= 0.0) {
            throw new IllegalArgumentException("Invalid rolling ticket segment");
        }

        double deltaX = to.x - from.x;
        double deltaZ = to.z - from.z;
        int steps = Math.max(
            1,
            (int)Math.ceil(
                Math.max(Math.abs(deltaX), Math.abs(deltaZ)) / spacing
            )
        );

        for (int index = 0; index <= steps; index++) {
            double fraction = index / (double)steps;
            addWindow(
                output,
                chunk(new Vec3(
                    from.x + deltaX * fraction,
                    from.y,
                    from.z + deltaZ * fraction
                )),
                radius
            );
        }
    }
}
