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

/** Terrain-following dust and explosion flecks emitted from sampled surface blocks. */
public final class GroundDustFrontRenderer {
    private static final long DUST_CHANNEL = 0x445553545F4E4F44L;
    private static final long FLECK_CHANNEL = 0x464C45434B5F4E4FL;
    private static final long SETTLED_SMOKE_CHANNEL = 0x534554544C45444CL;
    private static final Set<Long> CLAIMED_NODES = new HashSet<>();

    private GroundDustFrontRenderer() { }

    /**
     * Keeps overlapping conventional shock fronts from submitting the same
     * terrain-node puff stack more than once in a render frame.  The node
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
        final WarheadMesh.Lod lod, final float densityScale, final Quaternionf cameraOrientation) {
        if (nodes == null || nodes.isEmpty()) return;
        float budgetScale = Mth.clamp(
            (float) Math.sqrt(WarheadRenderSettings.particleBudgetMultiplier() / 10.0F),
            0.45F, 4.0F);
        int limit = Math.round((lod == WarheadMesh.Lod.NEAR ? 2_200
            : lod == WarheadMesh.Lod.MEDIUM ? 1_050 : 420)
            * Mth.clamp(densityScale, 0.25F, 3.2F) * budgetScale);
        int count = Math.min(limit, nodes.size());
        Basis basis = Basis.from(cameraOrientation);
        for (int index = 0; index < count; index++) {
            TerrainShockfrontNode node = nodes.get(index);
            long seed = mix(node.surfaceBlock().asLong());
            long start = node.emittedGameTime() == Long.MIN_VALUE
                ? node.readyGameTime() : node.emittedGameTime();
            double age = Math.max(0.0, gameTime - start);
            double lifetime = 68.0 + ((seed >>> 8) & 51L);
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
            float fadeStart = 0.48F + unit(seed, 7) * 0.20F;
            float fade = progress < fadeStart ? 1.0F
                : smoothstep(Mth.clamp((float) ((1.0 - progress)
                    / Math.max(0.03, 1.0 - fadeStart)), 0.0F, 1.0F));
            float alpha = (0.58F + unit(seed, 8) * 0.24F) * fade;
            float outwardFraction = Mth.clamp((float) (node.directDistance() / 72.0),
                0.0F, 1.0F);
            int puffs = lod == WarheadMesh.Lod.NEAR
                ? (outwardFraction < 0.52F ? 3 : 2)
                : lod == WarheadMesh.Lod.MEDIUM ? 2 : 1;
            for (int puff = 0; puff < puffs; puff++) {
                long puffSeed = mix(seed + puff * 0x9E3779B97F4A7C15L);
                int selector = Math.floorMod((int) puffSeed, 100);
                int red;
                int green;
                int blue;
                if (selector < 28) {
                    int pale = 206 + Math.floorMod((int) (puffSeed >>> 17), 43);
                    red = pale;
                    green = Math.min(252, pale + 2);
                    blue = Math.min(255, pale + 7);
                } else if (selector < 66) {
                    int neutral = 158 + Math.floorMod((int) (puffSeed >>> 17), 45);
                    red = neutral;
                    green = Math.min(211, neutral + 3);
                    blue = Math.min(219, neutral + 8);
                } else {
                    int earth = 128 + Math.floorMod((int) (puffSeed >>> 17), 48);
                    red = earth;
                    green = Mth.clamp(earth - 13, 105, 172);
                    blue = Mth.clamp(earth - 28, 78, 154);
                }
                float radius = (0.26F + unit(puffSeed, 0) * 0.58F)
                    * (0.96F + (float) progress * 0.48F)
                    * (1.0F + outwardFraction * 0.30F);
                float rotation = unit(puffSeed, 1) * Mth.TWO_PI;
                Vec3 center = base.add(signed(puffSeed, 2) * radius * 1.7,
                    unit(puffSeed, 3) * radius * 0.95,
                    signed(puffSeed, 4) * radius * 1.7);
                addBillboard(pose, buffer, center, radius, rotation, red, green, blue,
                    alpha * (0.68F + unit(puffSeed, 5) * 0.30F), basis);
            }
        }
    }

    /** Minecraft explosion artwork emitted from the exact same terrain nodes. */
    public static void renderExplosionFlecks(final PoseStack.Pose pose,
        final VertexConsumer buffer, final List<TerrainShockfrontNode> nodes,
        final Vec3 impactPosition, final long gameTime, final WarheadMesh.Lod lod,
        final float densityScale, final Quaternionf cameraOrientation) {
        if (nodes == null || nodes.isEmpty()) return;
        float budgetScale = Mth.clamp(
            (float) Math.sqrt(WarheadRenderSettings.particleBudgetMultiplier() / 10.0F),
            0.45F, 4.0F);
        int limit = Math.round((lod == WarheadMesh.Lod.NEAR ? 1_600
            : lod == WarheadMesh.Lod.MEDIUM ? 760 : 300)
            * Mth.clamp(densityScale, 0.25F, 3.2F) * budgetScale);
        int count = Math.min(limit, nodes.size());
        Basis basis = Basis.from(cameraOrientation);
        int divisor = lod == WarheadMesh.Lod.NEAR ? 1
            : lod == WarheadMesh.Lod.MEDIUM ? 2 : 3;
        for (int index = 0; index < count; index++) {
            TerrainShockfrontNode node = nodes.get(index);
            long seed = mix(node.surfaceBlock().asLong() ^ 0x4558504C4F444537L);
            if (Math.floorMod((int) seed, divisor) != 0) continue;
            long start = node.emittedGameTime() == Long.MIN_VALUE
                ? node.readyGameTime() : node.emittedGameTime();
            double age = Math.max(0.0, gameTime - start);
            double lifetime = 18.0 + ((seed >>> 9) & 17L);
            if (age > lifetime) continue;
            if (!CLAIMED_NODES.add(mix(node.surfaceBlock().asLong() ^ FLECK_CHANNEL))) continue;
            double progress = age / lifetime;
            double dx = node.position().x - impactPosition.x;
            double dz = node.position().z - impactPosition.z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-4) continue;
            int puffs = 1;
            for (int puff = 0; puff < puffs; puff++) {
                long puffSeed = mix(seed + puff * 0xD1B54A32D192ED03L);
                double outward = (0.10 + unit(puffSeed, 1) * 0.58) * progress;
                Vec3 center = node.position().subtract(impactPosition).add(
                    dx / length * outward + signed(puffSeed, 2) * 0.28,
                    0.10 + Math.sin(progress * Math.PI)
                        * (0.18 + unit(puffSeed, 3) * 0.62),
                    dz / length * outward + signed(puffSeed, 4) * 0.28);
                float radius = (0.28F + unit(puffSeed, 5) * 0.58F)
                    * (0.94F + (float) progress * 0.28F);
                float alpha = (float) (0.96 * Math.pow(1.0 - progress, 0.58));
                int green = 205 + Math.floorMod((int) (puffSeed >>> 21), 44);
                int blue = 128 + Math.floorMod((int) (puffSeed >>> 34), 80);
                addBillboard(pose, buffer, center, radius,
                    unit(puffSeed, 6) * Mth.TWO_PI,
                    255, Math.min(255, green), Math.min(235, blue), alpha, basis);
            }
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
