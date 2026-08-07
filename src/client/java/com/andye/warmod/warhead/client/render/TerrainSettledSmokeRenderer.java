package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.WarheadYieldScaling;
import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Ground smoke anchored to sampled terrain rather than an impact-height disc. */
public final class TerrainSettledSmokeRenderer {
    private static final double NUCLEAR_GROUND_LIFETIME = 5_900.0;
    private static final double NUCLEAR_FLOW_SPEED = 0.80;
    private static final double NUCLEAR_UPHILL_TOLERANCE = 1.25;

    private TerrainSettledSmokeRenderer() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final List<TerrainShockfrontSpoke> spokes, final Vec3 impactPosition,
        final double age, final float visualScale, final long visualSeed,
        final WarheadMesh.Lod lod, final boolean nuclear,
        final Quaternionf cameraOrientation) {
        if (spokes == null || spokes.isEmpty() || age < 2.0) return;
        double lifetime = nuclear ? NUCLEAR_GROUND_LIFETIME : 900.0 + visualScale * 120.0;
        if (age >= lifetime) return;
        if (!WarheadParticleVisibility.claimWorldClusterOnce(impactPosition,
            WarheadParticleVisibility.CHANNEL_SETTLED_SMOKE, nuclear ? 72.0 : 28.0)) return;

        float budgetScale = Mth.clamp(
            (float) Math.sqrt(WarheadRenderSettings.particleBudgetMultiplier() / 10.0F),
            0.40F, 3.0F);
        int spokeStride = lod == WarheadMesh.Lod.NEAR ? 1
            : lod == WarheadMesh.Lod.MEDIUM ? 2 : 4;
        double craterRadius = nuclear
            ? 13.0 + visualScale * 13.2
            : 2.5 + visualScale * 13.5;
        Basis basis = Basis.from(cameraOrientation);
        int rendered;
        if (nuclear) {
            rendered = renderNuclearFlood(pose, buffer, spokes, impactPosition,
                age, lifetime, visualScale, visualSeed, lod, budgetScale,
                spokeStride, craterRadius, basis);
            renderNuclearShockwall(pose, buffer, spokes, impactPosition, age,
                visualScale, visualSeed, lod, budgetScale, basis, rendered);
        } else {
            rendered = renderConventionalBase(pose, buffer, spokes, impactPosition,
                age, lifetime, visualScale, visualSeed, lod, budgetScale,
                spokeStride, craterRadius, basis);
        }
    }

    /**
     * Advances by distance layer across every spoke before moving to the next
     * layer. This prevents a particle budget from filling one angular sector
     * while the rest of the blast remains empty.
     */
    private static int renderNuclearFlood(final PoseStack.Pose pose,
        final VertexConsumer buffer, final List<TerrainShockfrontSpoke> spokes,
        final Vec3 impactPosition, final double age, final double lifetime,
        final float visualScale, final long visualSeed, final WarheadMesh.Lod lod,
        final float budgetScale, final int spokeStride, final double craterRadius,
        final Basis basis) {
        int limit = Math.max(384, Math.round((lod == WarheadMesh.Lod.NEAR ? 5_600
            : lod == WarheadMesh.Lod.MEDIUM ? 2_800 : 1_050) * budgetScale));
        double innerRadius = craterRadius * 0.18;
        double maximumRadius = nuclearFloodRadius(visualScale);
        double reachedRadius = Math.min(maximumRadius,
            innerRadius + Math.max(0.0, age - 18.0) * NUCLEAR_FLOW_SPEED);
        float globalFade = smoothstep(Mth.clamp((float) ((lifetime - age) / 520.0),
            0.0F, 1.0F));
        if (globalFade <= 0.002F) return 0;

        int maximumNodeCount = 0;
        for (int spokeIndex = 0; spokeIndex < spokes.size(); spokeIndex += spokeStride) {
            maximumNodeCount = Math.max(maximumNodeCount,
                spokes.get(spokeIndex).snapshotNodes().size());
        }

        int rendered = 0;
        for (int nodeIndex = 0; nodeIndex < maximumNodeCount && rendered < limit; nodeIndex++) {
            for (int spokeIndex = 0; spokeIndex < spokes.size() && rendered < limit;
                spokeIndex += spokeStride) {
                List<TerrainShockfrontNode> nodes = spokes.get(spokeIndex).snapshotNodes();
                if (nodeIndex >= nodes.size()) continue;
                TerrainShockfrontNode node = nodes.get(nodeIndex);
                if (!node.valid() || !node.visibleFromImpact()) continue;
                double radius = node.directDistance();
                if (radius < innerRadius || radius > reachedRadius || radius > maximumRadius) continue;

                TerrainShockfrontNode previous = nodeIndex > 0 ? nodes.get(nodeIndex - 1) : null;
                if (previous != null && previous.valid()
                    && node.position().y > previous.position().y + NUCLEAR_UPHILL_TOLERANCE) {
                    continue;
                }

                double activation = 18.0 + Math.max(0.0,
                    node.cumulativePathDistance() - innerRadius) / NUCLEAR_FLOW_SPEED;
                double localAge = age - activation;
                if (localAge < 0.0) continue;

                long seed = mix(visualSeed ^ node.surfaceBlock().asLong()
                    ^ 0x4E554B455F464C4FL);
                int stacks = lod == WarheadMesh.Lod.NEAR
                    ? 2 + Math.floorMod((int) seed, 3) : 2;
                stacks = Math.max(1, Math.round(stacks * Math.min(1.65F, budgetScale)));
                Vec3 target = node.position().subtract(impactPosition);
                Vec3 source;
                if (previous != null && previous.valid()) {
                    source = previous.position().subtract(impactPosition);
                } else {
                    double inward = Math.max(0.0, radius - 4.0) / Math.max(1.0, radius);
                    source = new Vec3(target.x * inward,
                        Math.max(target.y, 0.0) + 3.8, target.z * inward);
                }

                float distanceFraction = Mth.clamp((float) ((radius - innerRadius)
                    / Math.max(1.0, maximumRadius - innerRadius)), 0.0F, 1.0F);
                float sizeTaper = 1.0F - distanceFraction * 0.58F;
                for (int stack = 0; stack < stacks && rendered < limit; stack++) {
                    long particleSeed = mix(seed + stack * 0x9E3779B97F4A7C15L);
                    float travelDuration = 14.0F + unit(particleSeed, 0) * 24.0F;
                    float flow = smoothstep(Mth.clamp((float) localAge / travelDuration,
                        0.0F, 1.0F));
                    float fall = flow * flow;
                    float groundedHeight = 0.10F + stack * 0.34F * sizeTaper;
                    float sourceLift = 1.8F + unit(particleSeed, 1) * 4.6F;
                    float sourceY = (float) source.y + sourceLift;
                    float targetY = (float) target.y + groundedHeight;
                    float lateral = (1.0F - flow)
                        * (0.65F + unit(particleSeed, 2) * 1.15F);
                    float phase = unit(particleSeed, 3) * Mth.TWO_PI
                        + (float) localAge * signed(particleSeed, 4) * 0.008F;
                    float px = Mth.lerp(flow, (float) source.x, (float) target.x)
                        + Mth.cos(phase) * lateral
                        + signed(particleSeed, 5) * 0.34F;
                    float py = Mth.lerp(fall, sourceY, targetY);
                    float pz = Mth.lerp(flow, (float) source.z, (float) target.z)
                        + Mth.sin(phase) * lateral
                        + signed(particleSeed, 6) * 0.34F;

                    if (flow >= 0.999F) {
                        float creep = Math.min(3.0F, (float) Math.max(0.0,
                            localAge - travelDuration) * 0.0040F);
                        double pathX = target.x - source.x;
                        double pathZ = target.z - source.z;
                        double pathLength = Math.sqrt(pathX * pathX + pathZ * pathZ);
                        if (pathLength > 1.0E-4) {
                            px += (float) (pathX / pathLength) * creep;
                            pz += (float) (pathZ / pathLength) * creep;
                        }
                    }

                    float particleRadius = (2.15F + unit(particleSeed, 7) * 2.75F)
                        * (0.92F + visualScale * 0.075F) * sizeTaper;
                    int tone = Mth.clamp(34
                        + Math.floorMod((int) (particleSeed >>> 18), 76), 27, 116);
                    float alpha = globalFade * (0.84F - distanceFraction * 0.20F)
                        * (0.82F + unit(particleSeed, 8) * 0.16F);
                    billboard(pose, buffer, px, py, pz, particleRadius,
                        unit(particleSeed, 9) * Mth.TWO_PI
                            + (float) localAge * signed(particleSeed, 10) * 0.0018F,
                        tone, tone, tone, alpha, 0x880088, basis);
                    rendered++;
                }
            }
        }
        return rendered;
    }

    private static double nuclearFloodRadius(final float visualScale) {
        if (visualScale < 2.25F) return 128.0;
        if (visualScale < 3.15F) return 192.0;
        return 256.0;
    }

    private static int renderConventionalBase(final PoseStack.Pose pose,
        final VertexConsumer buffer, final List<TerrainShockfrontSpoke> spokes,
        final Vec3 impactPosition, final double age, final double lifetime,
        final float visualScale, final long visualSeed, final WarheadMesh.Lod lod,
        final float budgetScale, final int spokeStride, final double craterRadius,
        final Basis basis) {
        int limit = Math.max(128, Math.round((lod == WarheadMesh.Lod.NEAR ? 2_200
            : lod == WarheadMesh.Lod.MEDIUM ? 1_050 : 420) * budgetScale));
        double innerRadius = craterRadius * 0.12;
        double outerRadius = craterRadius + 11.0;
        float settleProgress = smoothstep(Mth.clamp((float) (age / 115.0), 0.0F, 1.0F));
        int maximumNodeCount = 0;
        for (int spokeIndex = 0; spokeIndex < spokes.size(); spokeIndex += spokeStride) {
            maximumNodeCount = Math.max(maximumNodeCount,
                spokes.get(spokeIndex).snapshotNodes().size());
        }

        int rendered = 0;
        for (int nodeIndex = 0; nodeIndex < maximumNodeCount && rendered < limit; nodeIndex++) {
            for (int spokeIndex = 0; spokeIndex < spokes.size() && rendered < limit;
                spokeIndex += spokeStride) {
                List<TerrainShockfrontNode> nodes = spokes.get(spokeIndex).snapshotNodes();
                if (nodeIndex >= nodes.size()) continue;
                TerrainShockfrontNode node = nodes.get(nodeIndex);
                if (!node.valid() || !node.visibleFromImpact()) continue;
                double radius = node.directDistance();
                if (radius < innerRadius || radius > outerRadius) continue;
                long seed = mix(visualSeed ^ node.surfaceBlock().asLong()
                    ^ 0x434F4E565F424153L);
                int stacks = 1 + Math.floorMod((int) seed, 2);
                Vec3 base = node.position().subtract(impactPosition);
                for (int stack = 0; stack < stacks && rendered < limit; stack++) {
                    long particleSeed = mix(seed + stack * 0x9E3779B97F4A7C15L);
                    double particleLife = lifetime * (0.70 + unit(particleSeed, 8) * 0.30);
                    if (age >= particleLife) continue;
                    float lifeProgress = Mth.clamp((float) (age / particleLife), 0.0F, 1.0F);
                    float initialHeight = 1.7F + unit(particleSeed, 0) * 5.2F;
                    float groundedHeight = 0.10F + stack * 0.38F;
                    float localSettle = smoothstep(Mth.clamp(
                        settleProgress + signed(particleSeed, 9) * 0.12F, 0.0F, 1.0F));
                    float vertical = Mth.lerp(localSettle, initialHeight, groundedHeight);
                    float swirl = (1.0F - localSettle) * 0.78F;
                    double phase = age * 0.011 + unit(particleSeed, 1) * Mth.TWO_PI;
                    float px = (float) base.x + Mth.cos((float) phase) * swirl
                        + signed(particleSeed, 2) * 0.52F;
                    float py = (float) base.y + vertical;
                    float pz = (float) base.z + Mth.sin((float) phase) * swirl
                        + signed(particleSeed, 3) * 0.52F;
                    float particleRadius = (1.35F + unit(particleSeed, 4) * 2.25F)
                        * (0.88F + visualScale * 0.13F);
                    int tone = Mth.clamp(70
                        + Math.floorMod((int) (particleSeed >>> 18), 92), 54, 172);
                    float fadeStart = 0.76F + unit(particleSeed, 10) * 0.20F;
                    float individualFade = lifeProgress < fadeStart ? 1.0F
                        : smoothstep(Mth.clamp((1.0F - lifeProgress)
                            / Math.max(0.025F, 1.0F - fadeStart), 0.0F, 1.0F));
                    float alpha = 0.84F * individualFade
                        * (0.78F + unit(particleSeed, 5) * 0.20F);
                    billboard(pose, buffer, px, py, pz, particleRadius,
                        unit(particleSeed, 6) * Mth.TWO_PI
                            + (float) age * signed(particleSeed, 7) * 0.0028F,
                        tone, tone, tone, alpha, 0xA000A0, basis);
                    rendered++;
                }
            }
        }
        return rendered;
    }

    /** Dense smoke wall travelling on the sampled terrain pressure front. */
    private static void renderNuclearShockwall(final PoseStack.Pose pose,
        final VertexConsumer buffer, final List<TerrainShockfrontSpoke> spokes,
        final Vec3 impactPosition, final double age, final float visualScale,
        final long visualSeed, final WarheadMesh.Lod lod, final float budgetScale,
        final Basis basis, final int alreadyRendered) {
        float radiusScale = WarheadYieldScaling.radiusScale(
            WarheadPayloadType.NUCLEAR, visualScale);
        double frontDistance = WarheadVisualMath.groundShockwaveDistance(age, radiusScale);
        if (frontDistance <= 0.0) return;
        float frontAlpha = Mth.clamp((float)
            WarheadVisualMath.groundShockwaveAlpha(age, radiusScale), 0.0F, 1.0F);
        if (frontAlpha <= 0.004F) return;

        double halfWidth = 13.0 + visualScale * 3.0;
        int additionalLimit = Math.max(480,
            Math.round((lod == WarheadMesh.Lod.NEAR ? 7_200
                : lod == WarheadMesh.Lod.MEDIUM ? 3_600 : 1_500) * budgetScale));
        int rendered = 0;
        int spokeStride = lod == WarheadMesh.Lod.NEAR ? 1
            : lod == WarheadMesh.Lod.MEDIUM ? 2 : 3;
        for (int spokeIndex = 0; spokeIndex < spokes.size() && rendered < additionalLimit;
            spokeIndex += spokeStride) {
            List<TerrainShockfrontNode> nodes = spokes.get(spokeIndex).snapshotNodes();
            for (int nodeIndex = 0; nodeIndex < nodes.size() && rendered < additionalLimit; nodeIndex++) {
                TerrainShockfrontNode node = nodes.get(nodeIndex);
                if (!node.valid() || !node.visibleFromImpact()) continue;
                double offset = node.cumulativePathDistance() - frontDistance;
                if (Math.abs(offset) > halfWidth) continue;
                float band = 1.0F - (float) (Math.abs(offset) / halfWidth);
                long seed = mix(visualSeed ^ node.surfaceBlock().asLong()
                    ^ 0x4E554B455F57414CL);
                int stacks = 4 + Math.floorMod((int) seed, 5);
                Vec3 base = node.position().subtract(impactPosition);
                for (int stack = 0; stack < stacks && rendered < additionalLimit; stack++) {
                    long particleSeed = mix(seed + stack * 0xD1B54A32D192ED03L);
                    float wallHeight = 5.0F + visualScale * 2.3F
                        + unit(particleSeed, 0) * (12.0F + visualScale * 3.0F);
                    float layer = stack / (float) Math.max(1, stacks - 1);
                    float py = (float) base.y + 0.12F + wallHeight * layer
                        + signed(particleSeed, 1) * 0.72F;
                    float radialJitter = signed(particleSeed, 2)
                        * (2.0F + visualScale * 0.42F);
                    double direct = Math.max(1.0E-4, node.directDistance());
                    float radialX = (float) ((node.position().x - impactPosition.x) / direct);
                    float radialZ = (float) ((node.position().z - impactPosition.z) / direct);
                    float tangentX = -radialZ;
                    float tangentZ = radialX;
                    float px = (float) base.x + radialX * radialJitter
                        + tangentX * signed(particleSeed, 3) * 1.8F;
                    float pz = (float) base.z + radialZ * radialJitter
                        + tangentZ * signed(particleSeed, 4) * 1.8F;
                    float particleRadius = (2.70F + unit(particleSeed, 5) * 5.8F)
                        * (0.92F + visualScale * 0.10F)
                        * (0.90F + band * 0.42F);
                    int selector = Math.floorMod((int) (particleSeed >>> 12), 100);
                    int tone = selector < 28
                        ? 142 + Math.floorMod((int) (particleSeed >>> 23), 70)
                        : 44 + Math.floorMod((int) (particleSeed >>> 23), 90);
                    float alpha = frontAlpha * (0.38F + band * 0.62F)
                        * (0.82F + unit(particleSeed, 6) * 0.17F);
                    billboard(pose, buffer, px, py, pz, particleRadius,
                        unit(particleSeed, 7) * Mth.TWO_PI
                            + (float) age * signed(particleSeed, 8) * 0.003F,
                        tone, tone, tone, alpha, 0x980098, basis);
                    rendered++;
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
        if (!WarheadParticleVisibility.visible(pose, centerX, centerY, centerZ, radius)) return;
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
            .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
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
