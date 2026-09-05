package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Deterministic compiler-only acceptance fixture; it performs no live-world mutation. */
final class WarheadFlatFixtureScenarioTest {
    private static final Vec3 CENTER = new Vec3(8.0, 64.0, 8.0);
    static { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void compilesEveryRequiredChunkForAllProductionNuclearYields() {
        scenario(WarheadYield.TACTICAL_NUCLEAR, 1_033);
        scenario(WarheadYield.STRATEGIC_NUCLEAR, 1_497);
        scenario(WarheadYield.HEAVY_NUCLEAR, 2_269);
    }

    private static void scenario(final WarheadYield yield, final int expectedChunks) {
        PreparedImpactSpec impact = new PreparedImpactSpec(
            UUID.nameUUIDFromBytes(("flat-fixture-" + yield.getSerializedName()).getBytes()),
            CENTER, WarheadPayloadType.NUCLEAR, yield,
            0x4E55434C454152L ^ yield.ordinal(), false);
        WarheadFootprint footprint = WarheadFootprintCalculator.calculate(
            impact.payload(), yield, CENTER);
        WarheadStatePalette palette = WarheadStatePalette.capture();
        PlanStatistics total = PlanStatistics.empty();
        long snapshotBytes = 0L;
        long hash = 0xCBF29CE484222325L;
        long started = System.nanoTime();
        int maximumActivation = 0;
        long[] orderedChunks = footprint.requiredChunks().toLongArray();
        Arrays.sort(orderedChunks);
        for (long packed : orderedChunks) {
            ChunkPos chunk = ChunkPos.unpack(packed);
            WarheadChunkSnapshot snapshot = flatSnapshot(chunk, impact);
            PreparedChunkPlan plan = WarheadPlanCompiler.compile(impact, footprint,
                snapshot, palette);
            total = total.add(WarheadPlanCompiler.statistics(plan));
            snapshotBytes += snapshot.estimatedBytes();
            maximumActivation = Math.max(maximumActivation, plan.activationTick());
            hash = mix(hash ^ packed ^ planHash(plan));
        }
        double millis = (System.nanoTime() - started) / 1_000_000.0;
        assertEquals(expectedChunks, footprint.requiredChunkCount());
        assertTrue(total.changedChunks() > 0L);
        assertTrue(total.changedBlocks() > 0L);
        assertTrue(total.changedBiomeQuarts() > 0L);
        assertTrue(maximumActivation <= 15);
        System.out.printf(java.util.Locale.ROOT,
            "WARHEAD_FLAT_FIXTURE yield=%s requiredChunks=%d changedChunks=%d "
                + "changedSections=%d changedBlocks=%d biomeQuarts=%d semantic=%d "
                + "snapshotBytesEstimate=%d planBytesEstimate=%d compileMillis=%.3f "
                + "planHash=%016x%n",
            yield.getSerializedName(), footprint.requiredChunkCount(),
            total.changedChunks(), total.changedSections(), total.changedBlocks(),
            total.changedBiomeQuarts(), total.semanticMutations(), snapshotBytes,
            total.estimatedBytes(), millis, hash);
    }

    private static WarheadChunkSnapshot flatSnapshot(final ChunkPos chunk,
        final PreparedImpactSpec impact) {
        StrategicExplosionProfile profile = StrategicExplosionProfiles.get(impact.yield());
        boolean crater = WarheadFootprintCalculator.chunkIntersectsCircle(
            chunk.x(), chunk.z(), impact.target().x, impact.target().z,
            profile.horizontalRadius() + 1.0);
        int minimumCraterY = crater
            ? Mth.floor(impact.target().y) - Mth.ceil(profile.downwardRadius()) - 1 : 0;
        int maximumCraterY = crater
            ? Mth.floor(impact.target().y) + Mth.ceil(profile.upwardRadius()) + 1 : -1;
        int craterCells = Math.max(0, maximumCraterY - minimumCraterY + 1) * 256;
        int air = Block.getId(Blocks.AIR.defaultBlockState());
        int dirt = Block.getId(Blocks.DIRT.defaultBlockState());
        int stone = Block.getId(Blocks.STONE.defaultBlockState());
        int[] motion = new int[256];
        int[] terrain = new int[256];
        Arrays.fill(motion, 64);
        Arrays.fill(terrain, 64);
        int[] surfaceStates = new int[256 * WarheadChunkSnapshot.SURFACE_LAYERS];
        int[] surfaceFlags = new int[surfaceStates.length];
        Arrays.fill(surfaceStates, dirt);
        Arrays.fill(surfaceFlags, WarheadSnapshotFlags.SOIL
            | WarheadSnapshotFlags.NATURAL_SURFACE);
        for (int column = 0; column < 256; column++) {
            surfaceStates[column] = air;
            surfaceFlags[column] = WarheadSnapshotFlags.AIR;
        }
        int[] craterStates = new int[craterCells];
        int[] craterFlags = new int[craterCells];
        float[] resistance = new float[craterCells];
        Arrays.fill(craterStates, stone);
        Arrays.fill(craterFlags, WarheadSnapshotFlags.COMMON_ROCK);
        Arrays.fill(resistance, 6.0F);
        return new WarheadChunkSnapshot(chunk, 1L, -4, new long[24],
            -64, 319, minimumCraterY, maximumCraterY, motion, terrain,
            new int[256], surfaceStates, surfaceFlags, craterStates, craterFlags,
            resistance, new long[0], new int[0], new int[0]);
    }

    private static long planHash(final PreparedChunkPlan plan) {
        long hash = plan.activationTick();
        for (PreparedSectionPlan section : plan.blockSections()) {
            hash = mix(hash ^ section.sectionY() ^ Arrays.hashCode(
                section.localIndicesUnsafe()));
            hash = mix(hash ^ Arrays.hashCode(section.expectedStateIdsUnsafe()));
            hash = mix(hash ^ Arrays.hashCode(section.finalStateIdsUnsafe()));
        }
        for (PreparedBiomeSectionPlan biome : plan.biomeSections()) {
            hash = mix(hash ^ biome.sectionY() ^ biome.quartMask());
        }
        for (PreparedFireMutation fire : plan.fireMutations()) hash = mix(hash ^ fire.hashCode());
        return hash;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
