package com.andye.warmod.warhead;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Pure extraction of the 62a89 noisy crater and fused-shell policy. */
final class NuclearCraterPolicy {
    private NuclearCraterPolicy() { }

    static Column column(final NuclearTerrainProfile profile, final int x,
        final int z) {
        double radial = Math.sqrt((x * (double)x + z * (double)z)
            / (profile.horizontalRadius() * profile.horizontalRadius()));
        if (radial > 1.0) return null;
        double angle = Math.atan2(z, x);
        double broadNoise = Math.sin(angle * 5.0 + profile.yield().ordinal() * 1.7) * 0.48
            + Math.sin(angle * 11.0 - radial * 8.0) * 0.22
            + (NuclearPolicyHash.unit(((long)x << 32) ^ (z & 0xFFFF_FFFFL)
                ^ profile.yield().ordinal()) - 0.5) * 0.30;
        double adjusted = radial / (1.0 + broadNoise * profile.boundaryRoughness());
        if (adjusted > 1.0) return null;
        double verticalFactor = Math.sqrt(Math.max(0.0, 1.0 - adjusted * adjusted));
        int bottom = -Math.max(1,
            (int)Math.floor(profile.downwardRadius() * verticalFactor));
        int top = Math.max(1,
            (int)Math.floor(profile.upwardRadius() * verticalFactor));
        return new Column(bottom, top, adjusted);
    }

    static boolean ownsCell(final NuclearTerrainProfile profile, final Vec3 center,
        final int x, final int y, final int z) {
        int centerX = Mth.floor(center.x);
        int centerY = Mth.floor(center.y);
        int centerZ = Mth.floor(center.z);
        Column column = column(profile, x - centerX, z - centerZ);
        return column != null && y >= centerY + column.bottomY()
            && y <= centerY + column.topY();
    }

    static double normalized(final NuclearTerrainProfile profile,
        final Column column, final int yOffset) {
        double verticalRadius = yOffset < 0
            ? profile.downwardRadius() : profile.upwardRadius();
        double vertical = Math.abs(yOffset) / Math.max(1.0, verticalRadius);
        return Math.sqrt(Math.min(1.0,
            column.radial() * column.radial() + vertical * vertical));
    }

    static boolean acceptsResistance(final NuclearTerrainProfile profile,
        final double normalized, final float resistance) {
        if (normalized <= profile.guaranteedVoidScale()) return true;
        float threshold = profile.maximumDestroyResistance()
            * (float)Math.max(0.08,
                1.0 - normalized * profile.edgeResistanceScale());
        return resistance <= threshold;
    }

    static int shellReplacement(final NuclearTerrainProfile profile,
        final PreparedImpactSpec impact, final WarheadStatePalette palette,
        final int x, final int y, final int z, final int flags,
        final double normalized) {
        long hash = NuclearPolicyHash.mix(impact.seed() ^ BlockPos.asLong(x, y, z)
            ^ 0x4352415445525F53L);
        double selector = NuclearPolicyHash.unit(hash);
        if (isMagmaFissure(profile, impact, x, z, normalized)) return palette.magma();
        if ((flags & WarheadSnapshotFlags.SAND) != 0) {
            if (selector < 0.20) return palette.tintedGlass();
            if (selector < 0.38) return palette.blackGlass();
            if (selector < 0.56) return palette.grayGlass();
            if (selector < 0.78) return palette.whiteTerracotta();
            return palette.sandstone();
        }
        if ((flags & WarheadSnapshotFlags.RED_SAND) != 0) {
            if (selector < 0.28) return palette.blackGlass();
            if (selector < 0.52) return palette.grayGlass();
            if (selector < 0.78) return palette.terracotta();
            return palette.redSandstone();
        }
        if (selector < 0.22) return palette.basalt();
        if (selector < 0.40) return palette.blackstone();
        if (selector < 0.64) return palette.deepslate();
        if (selector < 0.84) return palette.cobbledDeepslate();
        return palette.tuff();
    }

    static boolean isMagmaFissure(final NuclearTerrainProfile profile,
        final PreparedImpactSpec impact, final int x, final int z,
        final double normalized) {
        return normalized <= 0.94 && NuclearCrackField.contains(impact.seed(),
            impact.target().x, impact.target().z, x + 0.5, z + 0.5,
            profile.horizontalRadius() * 0.94);
    }

    record Column(int bottomY, int topY, double radial) { }
}
