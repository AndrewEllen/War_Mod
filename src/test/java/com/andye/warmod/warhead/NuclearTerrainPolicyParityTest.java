package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Golden fingerprints captured from the 62a89 deterministic terrain decisions. */
final class NuclearTerrainPolicyParityTest {
    private static final long REFERENCE_62A89_CRATER = -318957587613413812L;
    private static final long REFERENCE_62A89_SURFACE = 1296543654895764267L;
    private static final long REFERENCE_62A89_ENVIRONMENT = -554422905070750682L;
    private static final long REFERENCE_62A89_FIRE = 3394490952612551973L;

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void extractedPoliciesRetainReferenceDecisionFingerprints() {
        WarheadStatePalette palette = WarheadStatePalette.capture();
        long crater = craterFingerprint(palette);
        long surface = surfaceFingerprint(palette);
        long environment = environmentFingerprint(palette);
        long fire = fireFingerprint();
        String actual = "crater=" + crater + ", surface=" + surface
            + ", environment=" + environment + ", fire=" + fire;
        assertEquals(REFERENCE_62A89_CRATER, crater, actual);
        assertEquals(REFERENCE_62A89_SURFACE, surface, actual);
        assertEquals(REFERENCE_62A89_ENVIRONMENT, environment, actual);
        assertEquals(REFERENCE_62A89_FIRE, fire, actual);
    }

    @Test
    void referenceSurfaceGradientRetainsRareMaterialBranches() {
        WarheadStatePalette palette = WarheadStatePalette.capture();
        boolean calcite = false;
        boolean coral = false;
        for (int x = -240; x <= 240; x++) {
            long hash = 0x62A89EA0D3F2F64AL ^ BlockPos.asLong(x, 64, x / 3);
            int sand = NuclearSurfacePolicy.replacement(palette,
                WarheadSnapshotFlags.SAND, hash, 1.1, 0.35, 0, false, false);
            int soil = NuclearSurfacePolicy.replacement(palette,
                WarheadSnapshotFlags.SOIL, hash, 0.7, 0.25, 0, false, false);
            calcite |= sand == palette.calcite();
            for (int selector = 0; selector < 5; selector++) {
                coral |= soil == palette.decoration().deadCoralBlock(selector);
            }
        }
        assertTrue(calcite, "Reference fused-sand gradient must include calcite");
        assertTrue(coral, "Reference scorched-soil gradient must include dead coral");
    }

    private static long craterFingerprint(final WarheadStatePalette palette) {
        long hash = 1L;
        for (WarheadYield yield : new WarheadYield[] {WarheadYield.TACTICAL_NUCLEAR,
            WarheadYield.STRATEGIC_NUCLEAR, WarheadYield.HEAVY_NUCLEAR}) {
            NuclearTerrainProfile profile = NuclearTerrainProfile.forYield(yield);
            PreparedImpactSpec impact = impact(yield, 0x5EED1234CAFEBABEL, true);
            int radius = (int)Math.ceil(profile.horizontalRadius());
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    NuclearCraterPolicy.Column column = NuclearCraterPolicy.column(profile,
                        x, z);
                    if (column == null) {
                        hash = combine(hash, -1);
                        continue;
                    }
                    hash = combine(hash, column.bottomY());
                    hash = combine(hash, column.topY());
                    hash = combine(hash, Double.doubleToLongBits(column.radial()));
                    int[] flags = {0, WarheadSnapshotFlags.SAND,
                        WarheadSnapshotFlags.RED_SAND};
                    for (int flag : flags) {
                        double normalized = NuclearCraterPolicy.normalized(profile,
                            column, column.bottomY());
                        int replacement = NuclearCraterPolicy.shellReplacement(profile,
                            impact, palette, x, 64 + column.bottomY(), z, flag,
                            normalized);
                        hash = combine(hash, replacement);
                        hash = combine(hash, NuclearCraterPolicy.acceptsResistance(profile,
                            normalized, 18.5F) ? 1 : 0);
                    }
                }
            }
        }
        return hash;
    }

    private static long surfaceFingerprint(final WarheadStatePalette palette) {
        int[] flags = {WarheadSnapshotFlags.SAND, WarheadSnapshotFlags.RED_SAND,
            WarheadSnapshotFlags.SOIL, WarheadSnapshotFlags.COMMON_ROCK,
            WarheadSnapshotFlags.COMMON_ROCK | WarheadSnapshotFlags.EXPOSED,
            WarheadSnapshotFlags.NATURAL_SURFACE, WarheadSnapshotFlags.SNOW};
        long result = 7L;
        for (int x = -320; x <= 320; x += 2) {
            for (int z = -160; z <= 160; z += 3) {
                double crater = Math.hypot(x + 0.5, z + 0.5) / 64.0;
                double aftermath = Math.hypot(x + 0.5, z + 0.5) / 329.0;
                boolean mud = NuclearSurfacePolicy.mudPatch(0x62A89L, x, z,
                    aftermath);
                boolean sulfur = NuclearSurfacePolicy.sulfurPatch(0x62A89L, x, z,
                    aftermath, (x & 3) == 0);
                for (int flag : flags) {
                    for (int depth = 0; depth <= 3; depth++) {
                        long cellHash = 0x62A89L ^ ((long)x << 32)
                            ^ (z & 0xFFFF_FFFFL) ^ BlockPos.asLong(x, 70 - depth, z)
                            ^ depth * 0x9E3779B97F4A7C15L;
                        result = combine(result, NuclearSurfacePolicy.replacement(palette,
                            flag, cellHash, crater, aftermath, depth, mud, sulfur));
                    }
                }
            }
        }
        return result;
    }

    private static long environmentFingerprint(final WarheadStatePalette palette) {
        PreparedImpactSpec custom = impact(WarheadYield.HEAVY_NUCLEAR,
            0x13579BDF2468ACE0L, true);
        PreparedImpactSpec legacy = impact(WarheadYield.HEAVY_NUCLEAR,
            0x13579BDF2468ACE0L, false);
        long result = 11L;
        int[] fragileFlags = {WarheadSnapshotFlags.FRAGILE,
            WarheadSnapshotFlags.FRAGILE | WarheadSnapshotFlags.BUSH,
            WarheadSnapshotFlags.FRAGILE | WarheadSnapshotFlags.SUGAR_CANE,
            WarheadSnapshotFlags.FRAGILE | WarheadSnapshotFlags.AQUATIC_PLANT,
            WarheadSnapshotFlags.FRAGILE | WarheadSnapshotFlags.AQUATIC_PLANT
                | WarheadSnapshotFlags.DOUBLE_UPPER};
        for (int index = 0; index < 20_000; index++) {
            int x = index % 173 - 86;
            int y = 60 + index % 41;
            int z = index / 173 - 57;
            long packed = BlockPos.asLong(x, y, z);
            double normalized = (index % 1_001) / 1_000.0;
            result = combine(result, NuclearVegetationPolicy.leaves(custom,
                palette, normalized, packed));
            result = combine(result, NuclearVegetationPolicy.naturalLog(custom,
                palette, WarheadSnapshotFlags.AXIS_X, y, 63, normalized, packed));
            for (int flags : fragileFlags) {
                NuclearVegetationPolicy.Mutation mutation =
                    NuclearVegetationPolicy.fragile(custom, palette, flags,
                        WarheadSnapshotFlags.SULFUR, normalized, packed);
                result = combine(result, mutation == null ? Integer.MIN_VALUE
                    : mutation.replacementStateId());
            }
            result = combine(result, NuclearStructurePolicy.structuralLog(custom,
                palette, 0, normalized, packed));
            result = combine(result, NuclearStructurePolicy.plank(custom, palette,
                normalized, packed));
            result = combine(result, NuclearStructurePolicy.cobble(custom, palette,
                normalized, packed));
            result = combine(result, NuclearDecorationPolicy.ash(palette,
                custom.seed() ^ packed, normalized));
            NuclearDecorationPolicy.Remnants remnants =
                NuclearDecorationPolicy.treeRemnants(custom, palette,
                    BlockPos.of(packed), normalized);
            result = combine(result, remnants.fanStateId());
            result = combine(result, remnants.hangingMoss() ? 1 : 0);
            NuclearFirePolicy.TreeFire tree = NuclearFirePolicy.treeFire(
                index % 2 == 0 ? custom : legacy, normalized, packed, 1.0);
            result = combine(result, tree == null ? 0 : Float.floatToIntBits(
                tree.intensity()));
        }
        return result;
    }

    private static long fireFingerprint() {
        long result = 13L;
        for (int x = -420; x <= 420; x++) {
            for (int z = -220; z <= 220; z++) {
                double normalized = Math.hypot(x + 0.5, z + 0.5) / 480.0;
                result = combine(result, NuclearFirePolicy.firePocket(0x62A89L,
                    x, z, normalized) ? 1 : 0);
                result = combine(result, NuclearFirePolicy.legacyFirePocket(0x62A89L,
                    x, z, normalized) ? 1 : 0);
            }
        }
        return result;
    }

    private static PreparedImpactSpec impact(final WarheadYield yield,
        final long seed, final boolean customFire) {
        return new PreparedImpactSpec(new UUID(0x62A89EA0D3F2F64AL,
            0x98A037D58A14802CL), new Vec3(0.0, 64.0, 0.0),
            WarheadPayloadType.NUCLEAR, yield, seed, customFire);
    }

    private static long combine(final long current, final long value) {
        return (current ^ value) * 0x100000001B3L;
    }
}
