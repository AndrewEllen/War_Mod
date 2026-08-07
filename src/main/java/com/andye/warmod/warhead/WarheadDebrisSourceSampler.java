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
 * patches inside the inner crater. This prevents unrelated terrain at the foot
 * of cliffs from being selected merely because it shares a height-map column.
 */
public final class WarheadDebrisSourceSampler {
    private static final int HARD_SAMPLE_LIMIT = 512;

    private WarheadDebrisSourceSampler() { }

    public static List<WarheadExplosionDropContext.DestroyedBlock> sample(
        final ServerLevel level,
        final Vec3 center,
        final WarheadYield yield,
        final long seed
    ) {
        if (level == null || center == null || yield == null) throw new NullPointerException();
        if (!center.isFinite()) throw new IllegalArgumentException("center must be finite");
        StrategicExplosionProfile profile = StrategicExplosionProfiles.get(yield);
        int target = Math.min(HARD_SAMPLE_LIMIT, yield.maximumDebris());
        if (target <= 0) return List.of();

        ArrayList<WarheadExplosionDropContext.DestroyedBlock> result = new ArrayList<>(target);
        LongOpenHashSet sampled = new LongOpenHashSet(target * 3);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int centerX = Mth.floor(center.x);
        int centerY = Mth.floor(center.y);
        int centerZ = Mth.floor(center.z);

        /*
         * First preserve the structure or terrain directly struck by the
         * missile. The compact scan includes floating blocks and cliff faces;
         * it does not redirect the source to the terrain far below them.
         */
        int contactRadius = Mth.clamp(
            Mth.ceil(profile.horizontalRadius() * 0.24), 3, 9);
        int contactUp = Mth.clamp(Mth.ceil(profile.upwardRadius() * 0.42), 3, 10);
        int contactDown = Mth.clamp(Mth.ceil(profile.downwardRadius() * 0.34), 3, 10);
        for (int y = centerY + contactUp; y >= centerY - contactDown
            && result.size() < target; y--) {
            for (int ring = 0; ring <= contactRadius && result.size() < target; ring++) {
                for (int dz = -ring; dz <= ring && result.size() < target; dz++) {
                    for (int dx = -ring; dx <= ring && result.size() < target; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                        cursor.set(centerX + dx, y, centerZ + dz);
                        addIfDestroyed(level, center, profile, cursor, sampled, result, target);
                    }
                }
            }
        }

        /*
         * Fill the remaining budget with connected patches from the inner
         * crater only. Each block retains its real world position and state,
         * so the client launches the material that is about to be removed.
         */
        SplittableRandom random = new SplittableRandom(seed ^ 0x4445425249535F37L);
        int attempts = Math.max(48, target * 5);
        double sourceRadius = profile.horizontalRadius() * 0.68;
        for (int attempt = 0; attempt < attempts && result.size() < target; attempt++) {
            double angle = random.nextDouble(0.0, Math.PI * 2.0);
            double radial = Math.sqrt(random.nextDouble()) * sourceRadius;
            int rootX = Mth.floor(center.x + Math.cos(angle) * radial);
            int rootZ = Mth.floor(center.z + Math.sin(angle) * radial);
            if (!level.getChunkSource().hasChunk(rootX >> 4, rootZ >> 4)) continue;
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, rootX, rootZ) - 1;
            int patchRadius = yield.nuclear() || yield == WarheadYield.HEAVY_CONVENTIONAL ? 2 : 1;
            for (int dy = 2; dy >= -3 && result.size() < target; dy--) {
                for (int dz = -patchRadius; dz <= patchRadius && result.size() < target; dz++) {
                    for (int dx = -patchRadius; dx <= patchRadius && result.size() < target; dx++) {
                        if (Math.abs(dx) + Math.abs(dz) > patchRadius + 1) continue;
                        cursor.set(rootX + dx, surfaceY + dy, rootZ + dz);
                        addIfDestroyed(level, center, profile, cursor, sampled, result, target);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static void addIfDestroyed(
        final ServerLevel level,
        final Vec3 center,
        final StrategicExplosionProfile profile,
        final BlockPos.MutableBlockPos cursor,
        final LongOpenHashSet sampled,
        final ArrayList<WarheadExplosionDropContext.DestroyedBlock> result,
        final int target
    ) {
        if (result.size() >= target || !level.isInWorldBounds(cursor)) return;
        if (!level.getChunkSource().hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4)) return;
        long packed = cursor.asLong();
        if (!sampled.add(packed)) return;
        BlockState state = level.getBlockState(cursor);
        FluidState fluid = level.getFluidState(cursor);
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

        float resistance = Math.max(state.getBlock().getExplosionResistance(),
            fluid.getExplosionResistance());
        float threshold = profile.maximumDestroyResistance()
            * (float) Math.max(0.08, 1.0 - normalized * profile.edgeResistanceScale());
        if (normalized > profile.guaranteedVoidScale() && resistance > threshold) return;
        result.add(new WarheadExplosionDropContext.DestroyedBlock(
            BlockPos.of(packed), state));
    }
}
