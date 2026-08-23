package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.WarheadYieldScaling;
import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Ground smoke anchored to sampled terrain rather than an impact-height disc. */
public final class TerrainSettledSmokeRenderer {
    private TerrainSettledSmokeRenderer() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final List<TerrainShockfrontSpoke> spokes, final Vec3 impactPosition,
        final double age, final float visualScale, final long visualSeed,
        final WarheadMesh.Lod lod, final boolean nuclear,
        final Quaternionf cameraOrientation) {
        if (age < 1.0) return;
        Basis basis = Basis.from(cameraOrientation);
        if (nuclear) renderImmediateGroundShroud(pose, buffer, spokes, impactPosition,
            age, visualScale, visualSeed, lod, basis);
        if (spokes == null || spokes.isEmpty()) return;
        double lifetime = nuclear ? 3_400.0 : 190.0 + visualScale * 36.0;
        if (age >= lifetime) return;

        float budgetScale = Mth.clamp(
            (float) Math.sqrt(WarheadRenderSettings.particleBudgetMultiplier() / 10.0F),
            0.40F, 8.0F);
        if (nuclear) budgetScale = Math.max(1.0F, budgetScale);
        int spokeStride = lod == WarheadMesh.Lod.NEAR ? 1
            : lod == WarheadMesh.Lod.MEDIUM ? 2 : 4;
        double craterRadius = nuclear
            ? 13.0 + visualScale * 13.2
            : 2.5 + visualScale * 13.5;
        double innerRadius = nuclear ? craterRadius * 0.30 : craterRadius * 0.12;
        double outerRadius = nuclear
            ? craterRadius * 1.22 + Math.min(34.0, age * 0.038)
            : craterRadius + 11.0;
        float settleProgress = smoothstep(Mth.clamp((float) (age
            / (nuclear ? 155.0 : 115.0)), 0.0F, 1.0F));
        renderSettledBase(pose, buffer, spokes, impactPosition,
            age, lifetime, visualScale, visualSeed, lod, nuclear, budgetScale,
            spokeStride, innerRadius, outerRadius, settleProgress, basis);

        if (nuclear) {
            float radiusScale = WarheadYieldScaling.radiusScale(
                WarheadPayloadType.NUCLEAR, visualScale);
            renderNuclearShockwall(pose, buffer, spokes, impactPosition, age,
                visualScale, visualSeed, lod, basis,
                WarheadVisualMath.groundShockwaveDistance(age, radiusScale),
                (float) WarheadVisualMath.groundShockwaveAlpha(age, radiusScale), false);
            double returnDistance = WarheadVisualMath.nuclearReturnWaveRadius(age, radiusScale);
            if (returnDistance > 0.0) {
                renderNuclearShockwall(pose, buffer, spokes, impactPosition, age,
                    visualScale, visualSeed ^ 0x52455455524E5741L, lod, basis,
                    returnDistance,
                    (float) WarheadVisualMath.nuclearReturnWaveAlpha(age, radiusScale), true);
            }
        }
    }

    /** Dense, short-lived ground bank that survives independently of the moving front. */
    private static void renderImmediateGroundShroud(final PoseStack.Pose pose,
        final VertexConsumer buffer, final List<TerrainShockfrontSpoke> spokes,
        final Vec3 impactPosition, final double age, final float visualScale,
        final long visualSeed, final WarheadMesh.Lod lod, final Basis basis) {
        final double lifetime = 170.0;
        if (age >= lifetime) return;
        float appear = smoothstep(Mth.clamp((float) age / 9.0F, 0.0F, 1.0F));
        float fade = age < 78.0 ? 1.0F : smoothstep(Mth.clamp(
            (float) ((lifetime - age) / (lifetime - 78.0)), 0.0F, 1.0F));
        float alphaScale = appear * fade;
        double craterRadius = 13.0 + visualScale * 13.2;
        double outerRadius = craterRadius * (1.08 + Math.min(0.52, age / lifetime * 0.52));
        int limit = lod == WarheadMesh.Lod.NEAR ? 3_600
            : lod == WarheadMesh.Lod.MEDIUM ? 2_400 : 1_200;
        int rendered = 0;

        if (spokes != null) {
            int stride = lod == WarheadMesh.Lod.FAR ? 2 : 1;
            for (int spokeIndex = 0; spokeIndex < spokes.size() && rendered < limit;
                spokeIndex += stride) {
                List<TerrainShockfrontNode> nodes = spokes.get(spokeIndex).snapshotNodes();
                for (TerrainShockfrontNode node : nodes) {
                    if (rendered >= limit || !node.valid() || !node.visibleFromImpact()
                        || node.directDistance() > outerRadius) continue;
                    long seed = mix(visualSeed ^ node.surfaceBlock().asLong()
                        ^ 0x47524F554E445348L);
                    Vec3 base = node.position().subtract(impactPosition);
                    int layers = 2 + Math.floorMod((int) seed, 4);
                    for (int layer = 0; layer < layers && rendered < limit; layer++) {
                        long particleSeed = mix(seed + layer * 0x9E3779B97F4A7C15L);
                        float radius = (1.15F + unit(particleSeed, 0) * 2.75F)
                            * (0.88F + visualScale * 0.075F);
                        float px = (float) base.x + signed(particleSeed, 1) * 1.8F;
                        float py = (float) base.y + 0.12F + layer * 0.34F
                            + unit(particleSeed, 2) * 1.15F;
                        float pz = (float) base.z + signed(particleSeed, 3) * 1.8F;
                        int tone = 36 + Math.floorMod((int) (particleSeed >>> 20), 66);
                        float alpha = alphaScale * (0.66F + unit(particleSeed, 4) * 0.28F);
                        billboard(pose, buffer, px, py, pz, radius,
                            unit(particleSeed, 5) * Mth.TWO_PI,
                            tone, tone + 2, tone + 5, alpha, 0x900090, basis);
                        rendered++;
                    }
                }
            }
        }

        /* The first extraction can precede terrain-spoke readiness. Keep the
         * impact frame visually covered instead of showing an empty ground disc. */
        if (rendered == 0) {
            int fallback = lod == WarheadMesh.Lod.NEAR ? 720
                : lod == WarheadMesh.Lod.MEDIUM ? 480 : 260;
            for (int index = 0; index < fallback; index++) {
                long seed = mix(visualSeed ^ index * 0xD1B54A32D192ED03L);
                double radiusFromCenter = Math.sqrt(unit(seed, 0)) * outerRadius;
                double angle = unit(seed, 1) * Mth.TWO_PI;
                float radius = (1.25F + unit(seed, 2) * 2.65F)
                    * (0.88F + visualScale * 0.075F);
                int tone = 38 + Math.floorMod((int) (seed >>> 18), 62);
                billboard(pose, buffer,
                    (float) (Math.cos(angle) * radiusFromCenter),
                    0.15F + unit(seed, 3) * 1.45F,
                    (float) (Math.sin(angle) * radiusFromCenter), radius,
                    unit(seed, 4) * Mth.TWO_PI, tone, tone + 2, tone + 5,
                    alphaScale * (0.62F + unit(seed, 5) * 0.30F),
                    0x900090, basis);
            }
        }
    }

    private static int renderSettledBase(final PoseStack.Pose pose,
        final VertexConsumer buffer, final List<TerrainShockfrontSpoke> spokes,
        final Vec3 impactPosition, final double age, final double lifetime,
        final float visualScale, final long visualSeed, final WarheadMesh.Lod lod,
        final boolean nuclear, final float budgetScale, final int spokeStride,
        final double innerRadius, final double outerRadius,
        final float settleProgress, final Basis basis) {
        int rendered = 0;
        int limit = Math.max(96, Math.round((lod == WarheadMesh.Lod.NEAR ? 2_400
            : lod == WarheadMesh.Lod.MEDIUM ? 1_100 : 400) * budgetScale));

        for (int spokeIndex = 0; spokeIndex < spokes.size() && rendered < limit;
            spokeIndex += spokeStride) {
            List<TerrainShockfrontNode> nodes = spokes.get(spokeIndex).snapshotNodes();
            for (int nodeIndex = 0; nodeIndex < nodes.size() && rendered < limit; nodeIndex++) {
                TerrainShockfrontNode node = nodes.get(nodeIndex);
                if (!node.valid() || !node.visibleFromImpact()) continue;
                double radius = node.directDistance();
                if (radius < innerRadius || radius > outerRadius) continue;
                if (!nuclear && !GroundDustFrontRenderer.claimSettledSmokeNode(
                    node.surfaceBlock().asLong())) continue;
                long seed = mix(visualSeed ^ node.surfaceBlock().asLong()
                    ^ (nuclear ? 0x4E554B455F424153L : 0x434F4E565F424153L));
                int stacks = nuclear ? 3 + Math.floorMod((int) seed, 6)
                    : 1 + Math.floorMod((int) seed, 2);
                stacks = Math.max(1,
                    Math.round(stacks * Math.min(2.8F, budgetScale)));
                Vec3 base = node.position().subtract(impactPosition);
                for (int stack = 0; stack < stacks && rendered < limit; stack++) {
                    long particleSeed = mix(seed + stack * 0x9E3779B97F4A7C15L);
                    double particleLife = lifetime * (nuclear
                        ? 0.88 + unit(particleSeed, 8) * 0.12
                        : 0.70 + unit(particleSeed, 8) * 0.30);
                    if (age >= particleLife) continue;
                    float lifeProgress = Mth.clamp((float) (age / particleLife),
                        0.0F, 1.0F);
                    float initialHeight = nuclear
                        ? 3.0F + unit(particleSeed, 0) * 9.0F
                        : 1.7F + unit(particleSeed, 0) * 5.2F;
                    float groundedHeight = 0.10F
                        + stack * (nuclear ? 0.50F : 0.30F);
                    float localSettle = smoothstep(Mth.clamp(
                        settleProgress + signed(particleSeed, 9) * 0.12F,
                        0.0F, 1.0F));
                    float vertical = Mth.lerp(localSettle,
                        initialHeight, groundedHeight);
                    float swirl = (1.0F - localSettle)
                        * (nuclear ? 1.6F : 0.78F);
                    double phase = age * (nuclear ? 0.006 : 0.011)
                        + unit(particleSeed, 1) * Mth.TWO_PI;
                    float px = (float) base.x + Mth.cos((float) phase) * swirl
                        + signed(particleSeed, 2) * 0.58F;
                    float py = (float) base.y + vertical;
                    float pz = (float) base.z + Mth.sin((float) phase) * swirl
                        + signed(particleSeed, 3) * 0.58F;
                    float particleRadius = (nuclear
                        ? 1.30F + unit(particleSeed, 4) * 3.15F
                        : 0.95F + unit(particleSeed, 4) * 2.05F)
                        * (0.88F + visualScale * (nuclear ? 0.08F : 0.15F));
                    int toneBase = nuclear ? 38 : 70;
                    int tone = Mth.clamp(toneBase
                        + Math.floorMod((int) (particleSeed >>> 18),
                            nuclear ? 66 : 92),
                        nuclear ? 27 : 54, nuclear ? 120 : 172);
                    float fadeStart = nuclear ? 0.64F + unit(particleSeed, 10) * 0.14F
                        : 0.50F + unit(particleSeed, 10) * 0.18F;
                    float individualFade = lifeProgress < fadeStart ? 1.0F
                        : smoothstep(Mth.clamp((1.0F - lifeProgress)
                            / Math.max(0.025F, 1.0F - fadeStart), 0.0F, 1.0F));
                    float systemFade = !nuclear || age < 2_300.0 ? 1.0F
                        : smoothstep(Mth.clamp((float) ((3_400.0 - age) / 1_100.0),
                            0.0F, 1.0F));
                    float alpha = (nuclear ? 0.90F : 0.68F)
                        * individualFade
                        * systemFade
                        * (0.78F + unit(particleSeed, 5) * 0.20F);
                    billboard(pose, buffer, px, py, pz, particleRadius,
                        unit(particleSeed, 6) * Mth.TWO_PI
                            + (float) age * signed(particleSeed, 7) * 0.0028F,
                        tone, tone, tone, alpha,
                        nuclear ? 0x880088 : 0xA000A0, basis);
                    rendered++;
                }
            }
        }
        return rendered;
    }

    /**
     * Dense smoke wall travelling on the terrain path used by the physical
     * pressure front. Cumulative path distance, rather than straight-line
     * distance, keeps it attached when the front climbs hills.
     */
    private static void renderNuclearShockwall(final PoseStack.Pose pose,
        final VertexConsumer buffer, final List<TerrainShockfrontSpoke> spokes,
        final Vec3 impactPosition, final double age, final float visualScale,
        final long visualSeed, final WarheadMesh.Lod lod, final Basis basis,
        final double frontDistance, final float requestedAlpha,
        final boolean returning) {
        if (frontDistance <= 0.0) return;
        float frontAlpha = Mth.clamp(requestedAlpha, 0.0F, 1.0F);
        if (frontAlpha <= 0.004F) return;

        double halfWidth = (returning ? 9.0 : 13.0) + visualScale * 3.0;
        for (int spokeIndex = 0; spokeIndex < spokes.size(); spokeIndex++) {
            List<TerrainShockfrontNode> nodes = spokes.get(spokeIndex).snapshotNodes();
            for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
                TerrainShockfrontNode node = nodes.get(nodeIndex);
                if (!node.valid() || !node.visibleFromImpact()) continue;
                double offset = node.cumulativePathDistance() - frontDistance;
                if (Math.abs(offset) > halfWidth) continue;
                float band = 1.0F - (float) (Math.abs(offset) / halfWidth);
                long seed = mix(visualSeed ^ node.surfaceBlock().asLong()
                    ^ (returning ? 0x5245545F57414C4CL : 0x4E554B455F57414CL));
                int stacks = switch (lod) {
                    case NEAR -> 9 + Math.floorMod((int) seed, 6);
                    case MEDIUM -> 6 + Math.floorMod((int) seed, 4);
                    case FAR -> 3 + Math.floorMod((int) seed, 3);
                };
                Vec3 base = node.position().subtract(impactPosition);
                for (int stack = 0; stack < stacks; stack++) {
                    long particleSeed = mix(seed
                        + stack * 0xD1B54A32D192ED03L);
                    float wallHeight = (returning ? 3.0F : 5.0F) + visualScale * 2.3F
                        + unit(particleSeed, 0)
                            * ((returning ? 8.0F : 12.0F) + visualScale * 3.0F);
                    float layer = stack / (float) Math.max(1, stacks - 1);
                    float py = (float) base.y
                        + 0.12F + wallHeight * layer
                        + signed(particleSeed, 1) * 0.72F;
                    float radialJitter = signed(particleSeed, 2)
                        * (2.0F + visualScale * 0.42F);
                    double direct = Math.max(1.0E-4, node.directDistance());
                    float radialX = (float) ((node.position().x - impactPosition.x)
                        / direct);
                    float radialZ = (float) ((node.position().z - impactPosition.z)
                        / direct);
                    float tangentX = -radialZ;
                    float tangentZ = radialX;
                    float px = (float) base.x + radialX * radialJitter
                        + tangentX * signed(particleSeed, 3) * 1.8F;
                    float pz = (float) base.z + radialZ * radialJitter
                        + tangentZ * signed(particleSeed, 4) * 1.8F;
                    float particleRadius = (2.55F
                        + unit(particleSeed, 5) * 6.15F)
                        * (0.92F + visualScale * 0.11F)
                        * (0.90F + band * 0.42F);
                    int selector = Math.floorMod((int) (particleSeed >>> 12), 100);
                    int tone = selector < 28
                        ? (returning ? 118 : 142)
                            + Math.floorMod((int) (particleSeed >>> 23), 70)
                        : (returning ? 36 : 44)
                            + Math.floorMod((int) (particleSeed >>> 23), 90);
                    float alpha = frontAlpha * (0.38F + band * 0.62F)
                        * (0.82F + unit(particleSeed, 6) * 0.17F);
                    billboard(pose, buffer, px, py, pz, particleRadius,
                        unit(particleSeed, 7) * Mth.TWO_PI
                            + (float) age * signed(particleSeed, 8) * 0.003F,
                        tone, tone, tone, alpha, 0x980098, basis);
                }
            }
        }
    }

    private record Basis(Vector3f right, Vector3f up, Vector3f normal) {
        private static Basis from(final Quaternionf camera) {
            return new Basis(new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera));
        }
    }

    private static void billboard(final PoseStack.Pose pose,
        final VertexConsumer buffer, final float centerX, final float centerY,
        final float centerZ, final float radius, final float rotation,
        final int red, final int green, final int blue, final float alpha,
        final int light, final Basis basis) {
        float cosine = Mth.cos(rotation);
        float sine = Mth.sin(rotation);
        vertex(pose, buffer, centerX, centerY, centerZ, -radius, -radius,
            cosine, sine, 0.0F, 1.0F, red, green, blue, alpha, light, basis);
        vertex(pose, buffer, centerX, centerY, centerZ, -radius, radius,
            cosine, sine, 0.0F, 0.0F, red, green, blue, alpha, light, basis);
        vertex(pose, buffer, centerX, centerY, centerZ, radius, radius,
            cosine, sine, 1.0F, 0.0F, red, green, blue, alpha, light, basis);
        vertex(pose, buffer, centerX, centerY, centerZ, radius, -radius,
            cosine, sine, 1.0F, 1.0F, red, green, blue, alpha, light, basis);
    }

    private static void vertex(final PoseStack.Pose pose,
        final VertexConsumer buffer, final float centerX, final float centerY,
        final float centerZ, final float localX, final float localY,
        final float cosine, final float sine, final float u, final float v,
        final int red, final int green, final int blue, final float alpha,
        final int light, final Basis basis) {
        float rotatedX = localX * cosine - localY * sine;
        float rotatedY = localX * sine + localY * cosine;
        float offsetX = basis.right.x * rotatedX + basis.up.x * rotatedY;
        float offsetY = basis.right.y * rotatedX + basis.up.y * rotatedY;
        float offsetZ = basis.right.z * rotatedX + basis.up.z * rotatedY;
        buffer.addVertex(pose, centerX + offsetX, centerY + offsetY,
                centerZ + offsetZ)
            .setColor(red, green, blue,
                Mth.clamp((int) (alpha * 255.0F), 0, 255))
            .setUv(u, v).setOverlay(0).setLight(light)
            .setNormal(pose, basis.normal.x, basis.normal.y, basis.normal.z);
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
