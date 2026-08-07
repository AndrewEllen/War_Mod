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
        if (spokes == null || spokes.isEmpty() || age < 2.0) return;
        double lifetime = nuclear ? 2_050.0 : 520.0 + visualScale * 85.0;
        if (age >= lifetime) return;

        float budgetScale = Mth.clamp(
            (float) Math.sqrt(WarheadRenderSettings.particleBudgetMultiplier() / 6.0F),
            0.40F, 8.0F);
        int spokeStride = lod == WarheadMesh.Lod.NEAR ? 1
            : lod == WarheadMesh.Lod.MEDIUM ? 2 : 4;
        double craterRadius = nuclear
            ? 13.0 + visualScale * 13.2
            : 2.5 + visualScale * 13.5;
        double innerRadius = nuclear ? craterRadius * 0.30 : craterRadius * 0.12;
        /*
         * Conventional fog deliberately reaches about four blocks farther than
         * the previous pass. Nuclear ground smoke retains the established base
         * ring and adds a slow, low skirt outside it.
         */
        double outerRadius = nuclear
            ? craterRadius * 1.22 + Math.min(30.0, age * 0.035)
            : craterRadius + 11.0;
        float settleProgress = smoothstep(Mth.clamp((float) (age
            / (nuclear ? 155.0 : 72.0)), 0.0F, 1.0F));
        float finalFade = age < lifetime * 0.82 ? 1.0F
            : smoothstep(Mth.clamp((float) ((lifetime - age) / (lifetime * 0.18)),
                0.0F, 1.0F));
        Basis basis = Basis.from(cameraOrientation);
        int rendered = renderSettledBase(pose, buffer, spokes, impactPosition,
            age, visualScale, visualSeed, lod, nuclear, budgetScale, spokeStride,
            innerRadius, outerRadius, settleProgress, finalFade, basis);

        if (nuclear) {
            renderNuclearShockwall(pose, buffer, spokes, impactPosition, age,
                visualScale, visualSeed, lod, budgetScale, spokeStride, basis,
                rendered);
        }
    }

    private static int renderSettledBase(final PoseStack.Pose pose,
        final VertexConsumer buffer, final List<TerrainShockfrontSpoke> spokes,
        final Vec3 impactPosition, final double age, final float visualScale,
        final long visualSeed, final WarheadMesh.Lod lod, final boolean nuclear,
        final float budgetScale, final int spokeStride, final double innerRadius,
        final double outerRadius, final float settleProgress, final float finalFade,
        final Basis basis) {
        int rendered = 0;
        int limit = Math.max(128, Math.round((lod == WarheadMesh.Lod.NEAR ? 8_800
            : lod == WarheadMesh.Lod.MEDIUM ? 4_200 : 1_500) * budgetScale));

        for (int spokeIndex = 0; spokeIndex < spokes.size() && rendered < limit;
            spokeIndex += spokeStride) {
            List<TerrainShockfrontNode> nodes = spokes.get(spokeIndex).snapshotNodes();
            for (int nodeIndex = 0; nodeIndex < nodes.size() && rendered < limit; nodeIndex++) {
                TerrainShockfrontNode node = nodes.get(nodeIndex);
                if (!node.valid() || !node.visibleFromImpact()) continue;
                double radius = node.directDistance();
                if (radius < innerRadius || radius > outerRadius) continue;
                long seed = mix(visualSeed ^ node.surfaceBlock().asLong()
                    ^ (nuclear ? 0x4E554B455F424153L : 0x434F4E565F424153L));
                int stacks = nuclear ? 2 + Math.floorMod((int) seed, 5)
                    : 1 + Math.floorMod((int) seed, 3);
                stacks = Math.max(1,
                    Math.round(stacks * Math.min(2.6F, budgetScale)));
                Vec3 base = node.position().subtract(impactPosition);
                for (int stack = 0; stack < stacks && rendered < limit; stack++) {
                    long particleSeed = mix(seed + stack * 0x9E3779B97F4A7C15L);
                    float initialHeight = nuclear
                        ? 3.0F + unit(particleSeed, 0) * 9.0F
                        : 1.4F + unit(particleSeed, 0) * 4.5F;
                    float groundedHeight = 0.10F
                        + stack * (nuclear ? 0.50F : 0.34F);
                    float vertical = Mth.lerp(settleProgress,
                        initialHeight, groundedHeight);
                    float swirl = (1.0F - settleProgress)
                        * (nuclear ? 1.6F : 0.72F);
                    double phase = age * (nuclear ? 0.006 : 0.012)
                        + unit(particleSeed, 1) * Mth.TWO_PI;
                    float px = (float) base.x + Mth.cos((float) phase) * swirl
                        + signed(particleSeed, 2) * 0.50F;
                    float py = (float) base.y + vertical;
                    float pz = (float) base.z + Mth.sin((float) phase) * swirl
                        + signed(particleSeed, 3) * 0.50F;
                    float particleRadius = (nuclear
                        ? 1.30F + unit(particleSeed, 4) * 3.15F
                        : 0.58F + unit(particleSeed, 4) * 1.42F)
                        * (0.88F + visualScale * (nuclear ? 0.08F : 0.15F));
                    int toneBase = nuclear ? 38 : 70;
                    int tone = Mth.clamp(toneBase
                        + Math.floorMod((int) (particleSeed >>> 18),
                            nuclear ? 66 : 92),
                        nuclear ? 27 : 54, nuclear ? 120 : 172);
                    float alpha = (nuclear ? 0.90F : 0.84F)
                        * finalFade * (0.78F + unit(particleSeed, 5) * 0.20F);
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
     * Dense smoke wall travelling on the sampled terrain with the nuclear
     * pressure front. Each stack starts at its node's real surface height, so
     * the wall climbs hills and descends into valleys instead of floating in a
     * single horizontal ring.
     */
    private static void renderNuclearShockwall(final PoseStack.Pose pose,
        final VertexConsumer buffer, final List<TerrainShockfrontSpoke> spokes,
        final Vec3 impactPosition, final double age, final float visualScale,
        final long visualSeed, final WarheadMesh.Lod lod, final float budgetScale,
        final int spokeStride, final Basis basis, final int alreadyRendered) {
        float radiusScale = WarheadYieldScaling.radiusScale(
            WarheadPayloadType.NUCLEAR, visualScale);
        double frontDistance = WarheadVisualMath.groundShockwaveDistance(age,
            radiusScale);
        if (frontDistance <= 0.0) return;
        float frontAlpha = Mth.clamp((float)
            WarheadVisualMath.groundShockwaveAlpha(age, radiusScale), 0.0F, 1.0F);
        if (frontAlpha <= 0.008F) return;

        double halfWidth = 8.0 + visualScale * 2.4;
        int limit = alreadyRendered + Math.max(320,
            Math.round((lod == WarheadMesh.Lod.NEAR ? 12_000
                : lod == WarheadMesh.Lod.MEDIUM ? 5_800 : 2_100)
                * budgetScale));
        int rendered = alreadyRendered;
        for (int spokeIndex = 0; spokeIndex < spokes.size() && rendered < limit;
            spokeIndex += spokeStride) {
            List<TerrainShockfrontNode> nodes = spokes.get(spokeIndex).snapshotNodes();
            for (int nodeIndex = 0; nodeIndex < nodes.size() && rendered < limit; nodeIndex++) {
                TerrainShockfrontNode node = nodes.get(nodeIndex);
                if (!node.valid() || !node.visibleFromImpact()) continue;
                double offset = node.directDistance() - frontDistance;
                if (Math.abs(offset) > halfWidth) continue;
                float band = 1.0F - (float) (Math.abs(offset) / halfWidth);
                long seed = mix(visualSeed ^ node.surfaceBlock().asLong()
                    ^ 0x4E554B455F57414CL);
                int stacks = 5 + Math.floorMod((int) seed, 7);
                stacks = Math.max(3,
                    Math.round(stacks * Math.min(3.0F, budgetScale)));
                Vec3 base = node.position().subtract(impactPosition);
                for (int stack = 0; stack < stacks && rendered < limit; stack++) {
                    long particleSeed = mix(seed
                        + stack * 0xD1B54A32D192ED03L);
                    float wallHeight = 3.0F + visualScale * 1.9F
                        + unit(particleSeed, 0) * (9.0F + visualScale * 2.4F);
                    float layer = stack / (float) Math.max(1, stacks - 1);
                    float py = (float) base.y
                        + 0.14F + wallHeight * layer
                        + signed(particleSeed, 1) * 0.55F;
                    float radialJitter = signed(particleSeed, 2)
                        * (1.2F + visualScale * 0.32F);
                    double direct = Math.max(1.0E-4, node.directDistance());
                    float radialX = (float) ((node.position().x - impactPosition.x)
                        / direct);
                    float radialZ = (float) ((node.position().z - impactPosition.z)
                        / direct);
                    float tangentX = -radialZ;
                    float tangentZ = radialX;
                    float px = (float) base.x + radialX * radialJitter
                        + tangentX * signed(particleSeed, 3) * 1.3F;
                    float pz = (float) base.z + radialZ * radialJitter
                        + tangentZ * signed(particleSeed, 4) * 1.3F;
                    float particleRadius = (1.35F
                        + unit(particleSeed, 5) * 3.7F)
                        * (0.90F + visualScale * 0.10F)
                        * (0.92F + band * 0.34F);
                    int selector = Math.floorMod((int) (particleSeed >>> 12), 100);
                    int tone = selector < 22
                        ? 132 + Math.floorMod((int) (particleSeed >>> 23), 62)
                        : 48 + Math.floorMod((int) (particleSeed >>> 23), 82);
                    float alpha = frontAlpha * band
                        * (0.78F + unit(particleSeed, 6) * 0.20F);
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
