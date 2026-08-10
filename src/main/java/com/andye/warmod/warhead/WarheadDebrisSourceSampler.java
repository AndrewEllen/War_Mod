package com.andye.warmod.warhead;

import com.andye.warmod.testtool.WarheadExplosionDropContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Captures debris from blocks that actually intersect the impending crater.
 * The contact area is sampled first, followed by small connected surface
 * patches inside the inner crater. Sampling can be advanced incrementally so
 * terminal-warhead flight time can absorb the read-only world work.
 */
public final class WarheadDebrisSourceSampler {
    private static final int HARD_SAMPLE_LIMIT = 512;

    private WarheadDebrisSourceSampler() { }

    /** Optional shared read-through cache used only by terminal-flight preparation. */
    interface TerrainReadCache {
        BlockState blockState(ServerLevel level, BlockPos position);
    }

    public static IncrementalSample begin(
        final Vec3 center,
        final WarheadYield yield,
        final long seed
    ) {
        return begin(center, yield, seed, null);
    }

    static IncrementalSample begin(
        final Vec3 center,
        final WarheadYield yield,
        final long seed,
        final TerrainReadCache terrainCache
    ) {
        if (center == null || yield == null) throw new NullPointerException();
        if (!center.isFinite()) throw new IllegalArgumentException("center must be finite");
        return new IncrementalSample(center, yield, seed, terrainCache);
    }

    public static List<WarheadExplosionDropContext.DestroyedBlock> sample(
        final ServerLevel level,
        final Vec3 center,
        final WarheadYield yield,
        final long seed
    ) {
        if (level == null) throw new NullPointerException();
        IncrementalSample sample = begin(center, yield, seed);
        while (!sample.complete()) sample.advance(level, Integer.MAX_VALUE);
        return sample.result();
    }

    /**
     * Captures as much representative debris as fits inside a fixed world-read
     * budget. Strategic impacts use this only when no terminal-flight
     * preparation exists, so an unprepared or intercepted launch can never
     * turn the impact tick into an unbounded terrain scan.
     */
    public static List<WarheadExplosionDropContext.DestroyedBlock> sampleBounded(
        final ServerLevel level,
        final Vec3 center,
        final WarheadYield yield,
        final long seed,
        final int maximumChecks
    ) {
        if (level == null) throw new NullPointerException();
        if (maximumChecks <= 0) return List.of();
        IncrementalSample sample = begin(center, yield, seed);
        sample.advance(level, maximumChecks);
        return sample.partialResult();
    }

    public static final class IncrementalSample {
        private final Vec3 center;
        private final StrategicExplosionProfile profile;
        private final int target;
        private final ArrayList<WarheadExplosionDropContext.DestroyedBlock> result;
        private final LongOpenHashSet sampled;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private final SplittableRandom random;
        private final TerrainReadCache terrainCache;
        private final int centerX;
        private final int centerZ;
        private final int contactRadius;
        private final int minimumContactY;
        private final int attempts;
        private final double sourceRadius;
        private final int patchRadius;

        private int contactY;
        private int contactRing;
        private int contactDz;
        private int contactDx;
        private boolean contactComplete;

        private int attempt;
        private boolean patchActive;
        private int rootX;
        private int rootZ;
        private int surfaceY;
        private int patchDy;
        private int patchDz;
        private int patchDx;
        private boolean complete;

        private IncrementalSample(
            final Vec3 center,
            final WarheadYield yield,
            final long seed,
            final TerrainReadCache terrainCache
        ) {
            this.center = center;
            this.profile = StrategicExplosionProfiles.get(yield);
            this.target = Math.min(HARD_SAMPLE_LIMIT, yield.maximumDebris());
            this.result = new ArrayList<>(Math.max(0, target));
            this.sampled = new LongOpenHashSet(Math.max(16, target * 3));
            this.random = new SplittableRandom(seed ^ 0x4445425249535F37L);
            this.terrainCache = terrainCache;
            this.centerX = Mth.floor(center.x);
            int centerY = Mth.floor(center.y);
            this.centerZ = Mth.floor(center.z);
            this.contactRadius = Mth.clamp(Mth.ceil(profile.horizontalRadius() * 0.24), 3, 9);
            int contactUp = Mth.clamp(Mth.ceil(profile.upwardRadius() * 0.42), 3, 10);
            int contactDown = Mth.clamp(Mth.ceil(profile.downwardRadius() * 0.34), 3, 10);
            this.contactY = centerY + contactUp;
            this.minimumContactY = centerY - contactDown;
            this.contactRing = 0;
            this.contactDz = 0;
            this.contactDx = 0;
            this.attempts = Math.max(48, target * 5);
            this.sourceRadius = profile.horizontalRadius() * 0.68;
            this.patchRadius = yield.nuclear() || yield == WarheadYield.HEAVY_CONVENTIONAL ? 2 : 1;
            this.complete = target <= 0;
        }

        /** Advances at most {@code maximumChecks} candidate world reads. */
        public int advance(final ServerLevel level, final int maximumChecks) {
            if (level == null) throw new NullPointerException();
            if (complete || maximumChecks <= 0) return 0;
            int checks = 0;
            while (!complete && checks < maximumChecks) {
                if (result.size() >= target) {
                    complete = true;
                    break;
                }
                if (!contactComplete) {
                    if (advanceContact(level)) checks++;
                    continue;
                }
                if (advancePatch(level)) checks++;
            }
            return checks;
        }

        public boolean complete() {
            return complete;
        }

        public List<WarheadExplosionDropContext.DestroyedBlock> result() {
            if (!complete) throw new IllegalStateException("Debris sampling is not complete");
            return List.copyOf(result);
        }

        /** Returns the stable prefix collected so far without forcing completion. */
        public List<WarheadExplosionDropContext.DestroyedBlock> partialResult() {
            return List.copyOf(result);
        }

        private boolean advanceContact(final ServerLevel level) {
            while (contactY >= minimumContactY) {
                if (contactRing > contactRadius) {
                    contactY--;
                    contactRing = 0;
                    contactDz = 0;
                    contactDx = 0;
                    continue;
                }
                if (contactDz > contactRing) {
                    contactRing++;
                    if (contactRing <= contactRadius) {
                        contactDz = -contactRing;
                        contactDx = -contactRing;
                    }
                    continue;
                }
                if (contactDx > contactRing) {
                    contactDz++;
                    contactDx = -contactRing;
                    continue;
                }

                int dx = contactDx++;
                int dz = contactDz;
                if (Math.max(Math.abs(dx), Math.abs(dz)) != contactRing) continue;
                cursor.set(centerX + dx, contactY, centerZ + dz);
                addIfDestroyed(level, center, profile, cursor, sampled, result, target, terrainCache);
                if (result.size() >= target) complete = true;
                return true;
            }
            contactComplete = true;
            return false;
        }

        private boolean advancePatch(final ServerLevel level) {
            while (attempt < attempts) {
                if (!patchActive) {
                    double angle = random.nextDouble(0.0, Math.PI * 2.0);
                    double radial = Math.sqrt(random.nextDouble()) * sourceRadius;
                    rootX = Mth.floor(center.x + Math.cos(angle) * radial);
                    rootZ = Mth.floor(center.z + Math.sin(angle) * radial);
                    attempt++;
                    if (!level.getChunkSource().hasChunk(rootX >> 4, rootZ >> 4)) return true;
                    /*
                     * Height remains live. A heightmap lookup is cheap, and an
                     * earlier crater can therefore move this patch downward
                     * without invalidating unrelated cached depth observations.
                     */
                    surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, rootX, rootZ) - 1;
                    patchDy = 2;
                    patchDz = -patchRadius;
                    patchDx = -patchRadius;
                    patchActive = true;
                    return true;
                }

                while (patchDy >= -3) {
                    if (patchDz > patchRadius) {
                        patchDy--;
                        patchDz = -patchRadius;
                        patchDx = -patchRadius;
                        continue;
                    }
                    if (patchDx > patchRadius) {
                        patchDz++;
                        patchDx = -patchRadius;
                        continue;
                    }
                    int dx = patchDx++;
                    int dz = patchDz;
                    if (Math.abs(dx) + Math.abs(dz) > patchRadius + 1) continue;
                    cursor.set(rootX + dx, surfaceY + patchDy, rootZ + dz);
                    addIfDestroyed(level, center, profile, cursor, sampled, result, target, terrainCache);
                    if (result.size() >= target) complete = true;
                    return true;
                }
                patchActive = false;
            }
            complete = true;
            return false;
        }
    }

    private static void addIfDestroyed(
        final ServerLevel level,
        final Vec3 center,
        final StrategicExplosionProfile profile,
        final BlockPos.MutableBlockPos cursor,
        final LongOpenHashSet sampled,
        final ArrayList<WarheadExplosionDropContext.DestroyedBlock> result,
        final int target,
        final TerrainReadCache terrainCache
    ) {
        if (result.size() >= target || !level.isInWorldBounds(cursor)) return;
        if (!level.getChunkSource().hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4)) return;
        long packed = cursor.asLong();
        if (!sampled.add(packed)) return;
        BlockState state = terrainCache == null
            ? level.getBlockState(cursor)
            : terrainCache.blockState(level, cursor);
        FluidState fluid = state.getFluidState();
        if (state.isAir() && fluid.isEmpty()) return;
        if (state.getDestroySpeed(level, cursor) < 0.0F) return;

        double dx = cursor.getX() + 0.5 - center.x;
        double dy = cursor.getY() + 0.5 - center.y;
        double dz = cursor.getZ() + 0.5 - center.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz)
            / Math.max(1.0, profile.horizontalRadius());
        double verticalRadius = dy < 0.0 ? profile.downwardRadius() : profile.upwardRadius();
        double vertical = Math.abs(dy) / Math.max(1.0, verticalRadius);
        double normalized = Math.sqrt(horizontal * horizontal + vertical * vertical);
        if (normalized > 1.0) return;

        if (normalized > profile.guaranteedVoidScale()) {
            float resistance = Math.max(state.getBlock().getExplosionResistance(),
                fluid.getExplosionResistance());
            float threshold = profile.maximumDestroyResistance()
                * (float) Math.max(0.08, 1.0 - normalized * profile.edgeResistanceScale());
            if (resistance > threshold) return;
        }
        result.add(new WarheadExplosionDropContext.DestroyedBlock(BlockPos.of(packed), state));
    }
}
