package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Compact terrain-node ground detail with a bounded CPU fallback. */
public final class GroundDustFrontRenderer {
    private static final long DUST_CHANNEL = 0x445553545F4E4F44L;
    private static final long SETTLED_SMOKE_CHANNEL = 0x534554544C45444CL;
    private static final Set<Long> CLAIMED_NODES = new HashSet<>();

    private GroundDustFrontRenderer() { }

    /**
     * Keeps overlapping conventional shock fronts from submitting the same
     * terrain-node card more than once in a render frame. The node
     * position is shared by the existing terrain sampler, so this is both
     * deterministic and substantially cheaper than per-billboard collision.
     */
    static void beginFrame() {
        CLAIMED_NODES.clear();
    }

    static boolean claimSettledSmokeNode(final long packedNode) {
        return CLAIMED_NODES.add(mix(packedNode ^ SETTLED_SMOKE_CHANNEL));
    }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final List<TerrainShockfrontNode> nodes, final Vec3 impactPosition, final long gameTime,
        final WarheadMesh.Lod lod, final float densityScale, final boolean nuclear,
        final Quaternionf cameraOrientation) {
        if (nodes == null || nodes.isEmpty()) return;
        int maximum = switch (lod) {
            case NEAR -> 256;
            case MEDIUM -> 128;
            case FAR -> 64;
        };
        float detailScale = Mth.clamp((float) Math.sqrt(
            WarheadRenderSettings.qualityScale()), 0.25F, 1.0F)
            * Mth.clamp(densityScale, 0.25F, 1.0F);
        int limit = Math.max(16, Math.min(maximum, Math.round(maximum * detailScale)));
        int count = Math.min(limit, nodes.size());
        Basis basis = Basis.from(cameraOrientation);
        for (int selected = 0; selected < count; selected++) {
            TerrainShockfrontNode node = nodes.get(
                (int) ((long) selected * nodes.size() / count));
            long seed = mix(node.surfaceBlock().asLong());
            long start = node.emittedGameTime() == Long.MIN_VALUE
                ? node.readyGameTime() : node.emittedGameTime();
            double age = Math.max(0.0, gameTime - start);
            boolean hotFleck = age <= 35.0 && unit(seed, 11) < 0.30F;
            double lifetime = hotFleck
                ? 18.0 + ((seed >>> 9) & 17L)
                : 68.0 + ((seed >>> 8) & 51L);
            if (age > lifetime) continue;
            if (!CLAIMED_NODES.add(mix(node.surfaceBlock().asLong() ^ DUST_CHANNEL))) continue;
            double progress = age / lifetime;
            double dx = node.position().x - impactPosition.x;
            double dz = node.position().z - impactPosition.z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-4) continue;

            double outward = (0.24 + ((seed >>> 18) & 31L) / 36.0) * progress;
            double rise = (0.18 + ((seed >>> 27) & 31L) / 25.0)
                * Math.sin(progress * Math.PI * 0.88);
            Vec3 base = node.position().subtract(impactPosition)
                .add(dx / length * outward, 0.06 + rise, dz / length * outward);
            float fade = (float) Math.pow(Math.max(0.0, 1.0 - progress),
                hotFleck ? 0.58 : 0.82);
            float alpha = (hotFleck ? 0.90F : 0.54F + unit(seed, 8) * 0.20F) * fade;
            float outwardFraction = Mth.clamp((float) (node.directDistance() / 72.0),
                0.0F, 1.0F);
            int tint = node.tintColor();
            int red = hotFleck ? 255 : (tint >> 16) & 255;
            int green = hotFleck ? 205 + Math.floorMod((int) (seed >>> 21), 44)
                : (tint >> 8) & 255;
            int blue = hotFleck ? 128 + Math.floorMod((int) (seed >>> 34), 80)
                : tint & 255;
            float radius = (hotFleck ? 0.28F + unit(seed, 5) * 0.58F
                : 0.42F + unit(seed, 0) * 0.72F)
                * (0.96F + (float) progress * 0.38F)
                * (1.0F + outwardFraction * 0.22F);
            Vec3 center = base.add(signed(seed, 2) * radius * 0.65,
                unit(seed, 3) * radius * 0.42,
                signed(seed, 4) * radius * 0.65);
            addBillboard(pose, buffer, center, radius, unit(seed, 1) * Mth.TWO_PI,
                red, Math.min(255, green), Math.min(235, blue), alpha, basis);
        }
    }

    private static void addBillboard(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 center, final float radius, final float rotation, final int red, final int green,
        final int blue, final float alpha, final Basis basis) {
        float cosine = Mth.cos(rotation), sine = Mth.sin(rotation);
        float ux = cosine * radius, uy = sine * radius;
        float vx = -sine * radius, vy = cosine * radius;
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        vertex(pose, buffer, center, -ux - vx, -uy - vy, 0.0F, 1.0F,
            red, green, blue, a, basis);
        vertex(pose, buffer, center, -ux + vx, -uy + vy, 0.0F, 0.0F,
            red, green, blue, a, basis);
        vertex(pose, buffer, center, ux + vx, uy + vy, 1.0F, 0.0F,
            red, green, blue, a, basis);
        vertex(pose, buffer, center, ux - vx, uy - vy, 1.0F, 1.0F,
            red, green, blue, a, basis);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 center, final float x, final float y, final float u, final float v,
        final int red, final int green, final int blue, final int alpha, final Basis basis) {
        float ox = basis.right.x * x + basis.up.x * y;
        float oy = basis.right.y * x + basis.up.y * y;
        float oz = basis.right.z * x + basis.up.z * y;
        buffer.addVertex(pose, (float) center.x + ox, (float) center.y + oy,
                (float) center.z + oz)
            .setColor(red, green, blue, alpha).setUv(u, v).setOverlay(0)
            .setLight(0xB000B0)
            .setNormal(pose, basis.normal.x, basis.normal.y, basis.normal.z);
    }

    private record Basis(Vector3f right, Vector3f up, Vector3f normal) {
        private static Basis from(final Quaternionf camera) {
            return new Basis(new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera));
        }
    }

    private static float smoothstep(final float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static float unit(final long value, final int lane) {
        long mixed = mix(value + lane * 0x9E3779B97F4A7C15L);
        return (float) ((mixed >>> 40) * 0x1.0p-24);
    }

    private static float signed(final long value, final int lane) {
        return unit(value, lane) * 2.0F - 1.0F;
    }
}
