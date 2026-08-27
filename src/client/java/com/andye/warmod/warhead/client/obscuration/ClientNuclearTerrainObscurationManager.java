package com.andye.warmod.warhead.client.obscuration;

import com.andye.warmod.warhead.obscuration.network.ClientboundNuclearTerrainObscurationPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Stable world-cell owner for mutation-driven nuclear terrain obscuration. */
public final class ClientNuclearTerrainObscurationManager {
    public static final ClientNuclearTerrainObscurationManager INSTANCE =
        new ClientNuclearTerrainObscurationManager();
    private static final int MAX_IMPACTS = 8;
    private static final int MAX_CELLS_PER_IMPACT = 8_192;
    private static final int[] CELL_SIZES = {8, 16, 32};
    private final Map<UUID, Impact> impacts = new LinkedHashMap<>();
    private ClientLevel activeLevel;

    private ClientNuclearTerrainObscurationManager() { }

    public synchronized void accept(
        final ClientboundNuclearTerrainObscurationPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (payload == null || !payload.isWellFormed() || !ensureLevel(level)) return;
        Impact impact = impacts.get(payload.impactId());
        if (impact == null) {
            while (impacts.size() >= MAX_IMPACTS) {
                Iterator<UUID> iterator = impacts.keySet().iterator();
                if (!iterator.hasNext()) break;
                iterator.next();
                iterator.remove();
            }
            impact = new Impact(payload.impactId(), new Vec3(payload.centerX(),
                payload.centerY(), payload.centerZ()), payload.visualSeed(),
                payload.visualScale(), payload.destructionRadius());
            impacts.put(payload.impactId(), impact);
        }
        impact.accept(level, payload);
    }

    public synchronized void tick(final Minecraft client) {
        if (!ensureLevel(client.level)) return;
        long now = client.level.getGameTime();
        impacts.values().removeIf(impact -> impact.expire(now));
    }

    public synchronized List<ObscurationImpactView> snapshot(final ClientLevel level) {
        if (level == null || activeLevel != level) return List.of();
        return impacts.values().stream().map(Impact::snapshot).toList();
    }

    public synchronized void clear() {
        impacts.clear();
        activeLevel = null;
    }

    private boolean ensureLevel(final ClientLevel level) {
        if (level == null) {
            clear();
            return false;
        }
        if (activeLevel != level) {
            impacts.clear();
            activeLevel = level;
        }
        return true;
    }

    public record ObscurationImpactView(UUID id, Vec3 center, float visualScale,
        float destructionRadius, float currentMutationRadius,
        float completedInteriorRadius, long completionGameTime,
        List<DustCell> cells) { }

    public record DustCell(long id, int cellX, int cellZ, int cellSize,
        Vec3 groundPosition, Vec3 groundNormal, float radialDistance,
        long spawnGameTime, long revealGameTime, float density, long seed) { }

    private static final class Impact {
        private final UUID id;
        private final Vec3 center;
        private final long visualSeed;
        private final float visualScale;
        private final float destructionRadius;
        private final LinkedHashMap<Long, DustCell> cells = new LinkedHashMap<>();
        private long lastServerTick = Long.MIN_VALUE;
        private float currentMutationRadius;
        private float completedInteriorRadius;
        private long completionGameTime = Long.MIN_VALUE;

        private Impact(final UUID id, final Vec3 center, final long visualSeed,
            final float visualScale, final float destructionRadius) {
            this.id = id;
            this.center = center;
            this.visualSeed = visualSeed;
            this.visualScale = visualScale;
            this.destructionRadius = destructionRadius;
        }

        private void accept(final ClientLevel level,
            final ClientboundNuclearTerrainObscurationPayload payload) {
            if (payload.serverGameTime() < lastServerTick
                || payload.currentMutationRadius() + 0.01F < currentMutationRadius
                || payload.completedInteriorRadius() + 0.01F < completedInteriorRadius) return;
            float from = Math.max(currentMutationRadius, payload.previousMutationRadius());
            float to = Math.min(destructionRadius,
                Math.max(from, payload.currentMutationRadius()));
            addCells(level, from, to, payload.serverGameTime());
            currentMutationRadius = to;
            completedInteriorRadius = Math.min(to,
                Math.max(completedInteriorRadius, payload.completedInteriorRadius()));
            revealCompletedCells(payload.serverGameTime(), payload.finalBand());
            if (payload.finalBand()) completionGameTime = payload.serverGameTime();
            lastServerTick = payload.serverGameTime();
        }

        private void addCells(final ClientLevel level, final float from, final float to,
            final long gameTime) {
            if (to <= 0.0F || cells.size() >= MAX_CELLS_PER_IMPACT) return;
            for (int cellSize : CELL_SIZES) {
                double diagonal = cellSize * Math.sqrt(2.0) * 0.5;
                int minimumCellX = Mth.floor((center.x - to - diagonal) / cellSize);
                int maximumCellX = Mth.floor((center.x + to + diagonal) / cellSize);
                int minimumCellZ = Mth.floor((center.z - to - diagonal) / cellSize);
                int maximumCellZ = Mth.floor((center.z + to + diagonal) / cellSize);
                for (int cellX = minimumCellX; cellX <= maximumCellX
                    && cells.size() < MAX_CELLS_PER_IMPACT; cellX++) {
                    for (int cellZ = minimumCellZ; cellZ <= maximumCellZ
                        && cells.size() < MAX_CELLS_PER_IMPACT; cellZ++) {
                        double worldX = (cellX + 0.5) * cellSize;
                        double worldZ = (cellZ + 0.5) * cellSize;
                        double dx = worldX - center.x;
                        double dz = worldZ - center.z;
                        double radialDistance = Math.sqrt(dx * dx + dz * dz);
                        if (radialDistance > to + diagonal
                            || radialDistance < Math.max(0.0, from - diagonal)) continue;
                        float bandWeight = cellBandWeight(cellSize, radialDistance);
                        if (bandWeight <= 0.015F) continue;
                        long seed = mix(visualSeed ^ (long) cellSize * 0x9E3779B97F4A7C15L
                            ^ (long) cellX * 0x632BE59BD9B4E019L
                            ^ (long) cellZ * 0x94D049BB133111EBL);
                        long cellId = seed & Long.MAX_VALUE;
                        if (cellId == 0L) cellId = 1L;
                        if (cells.containsKey(cellId)) continue;
                        NuclearTerrainObscurationWorldSampler.GroundSample ground =
                            NuclearTerrainObscurationWorldSampler.sample(level, center,
                                worldX, worldZ);
                        float density = bandWeight
                            * (0.78F + (float) unit(seed, 1) * 0.34F);
                        long reveal = radialDistance <= completedInteriorRadius
                            ? gameTime : Long.MAX_VALUE;
                        cells.put(cellId, new DustCell(cellId, cellX, cellZ, cellSize,
                            ground.position(), ground.normal(), (float) radialDistance,
                            gameTime, reveal, density, seed));
                    }
                }
            }
        }

        private void revealCompletedCells(final long gameTime, final boolean finalBand) {
            for (Map.Entry<Long, DustCell> entry : cells.entrySet()) {
                DustCell cell = entry.getValue();
                if (cell.revealGameTime() != Long.MAX_VALUE) continue;
                if (cell.radialDistance() <= completedInteriorRadius + cell.cellSize() * 0.5F
                    || (finalBand && cell.radialDistance() <= currentMutationRadius + 0.01F)) {
                    entry.setValue(new DustCell(cell.id(), cell.cellX(), cell.cellZ(),
                        cell.cellSize(), cell.groundPosition(), cell.groundNormal(),
                        cell.radialDistance(), cell.spawnGameTime(), gameTime,
                        cell.density(), cell.seed()));
                }
            }
        }

        private boolean expire(final long now) {
            cells.values().removeIf(cell -> {
                if (cell.revealGameTime() == Long.MAX_VALUE) return false;
                long persistence = 300L + Math.round(unit(cell.seed(), 2) * 240.0);
                return now - cell.revealGameTime() > persistence;
            });
            return (completionGameTime != Long.MIN_VALUE && cells.isEmpty())
                || (lastServerTick != Long.MIN_VALUE && now - lastServerTick > 1_200L);
        }

        private ObscurationImpactView snapshot() {
            return new ObscurationImpactView(id, center, visualScale,
                destructionRadius, currentMutationRadius, completedInteriorRadius,
                completionGameTime, List.copyOf(new ArrayList<>(cells.values())));
        }
    }

    static float cellBandWeight(final int cellSize, final double radius) {
        return switch (cellSize) {
            case 8 -> 1.0F - smoothStep(176.0, 208.0, radius);
            case 16 -> smoothStep(176.0, 208.0, radius)
                * (1.0F - smoothStep(480.0, 544.0, radius));
            case 32 -> smoothStep(480.0, 544.0, radius);
            default -> 0.0F;
        };
    }

    private static float smoothStep(final double edge0, final double edge1,
        final double value) {
        double t = Mth.clamp((value - edge0) / Math.max(1.0E-6, edge1 - edge0),
            0.0, 1.0);
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double unit(final long value, final int lane) {
        return (mix(value + lane * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}
