package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BlastCloudRenderer {
    private BlastCloudRenderer() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale,
        final WarheadClientVisualProfile profile, final List<BlastCloudLobe> lobes,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        render(pose, buffer, age, visualScale, profile, lobes, lod, camera, Vec3.ZERO);
    }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale,
        final WarheadClientVisualProfile profile, final List<BlastCloudLobe> lobes,
        final WarheadMesh.Lod lod, final Quaternionf camera, final Vec3 wind) {
        if (age < profile.smokeStartTick() || age >= profile.cloudDissipationEndTick()
            || lobes == null || lobes.isEmpty()) return;
        int limit = lod == WarheadMesh.Lod.NEAR ? profile.nearSmokeLobes()
            : lod == WarheadMesh.Lod.MEDIUM ? profile.mediumSmokeLobes()
            : profile.farSmokeLobes();
        limit = Math.min(limit, lobes.size());
        double fade = Math.pow(1.0 - WarheadVisualMath.clamp(
            (age - profile.cloudRiseEndTick())
                / (double) Math.max(1,
                    profile.cloudDissipationEndTick() - profile.cloudRiseEndTick()),
            0.0, 1.0), 0.72);
        double scale = profile.payloadType() == WarheadPayloadType.NUCLEAR
            ? 1.0 : Mth.clamp(visualScale, 0.55F, 1.45F);
        double lodRadius = lod == WarheadMesh.Lod.FAR ? 1.55
            : lod == WarheadMesh.Lod.MEDIUM ? 1.18 : 1.0;
        for (int visible = 0; visible < limit; visible++) {
            int index = (int) ((long) visible * lobes.size() / limit);
            BlastCloudLobe lobe = lobes.get(index);
            if (age < lobe.spawnTick()) continue;
            double rawLocal = WarheadVisualMath.clamp(
                (age - lobe.spawnTick()) / Math.max(1.0, lobe.growthTicks()), 0.0, 1.0);
            double local = smooth(rawLocal);
            double after = Math.max(0.0,
                age - lobe.spawnTick() - lobe.growthTicks());
            Vec3 center = center(lobe, profile, age, (float) scale, wind);
            float radius = (float) (lobe.baseRadius()
                * Mth.lerp(local, 0.16, 1.0) * scale * lodRadius
                * (1.0 + 0.075 * Math.sin(lobe.phase() + after * 0.031)));
            double appear = smooth(rawLocal / 0.24);
            float alpha = (float) (lobe.opacity() * appear * fade);
            billboard(pose, buffer, center, radius,
                lobe.rotation() + after * 0.0015,
                lobe.red(), lobe.green(), lobe.blue(), alpha, camera);
        }
    }

    public static Vec3 center(final BlastCloudLobe lobe,
        final WarheadClientVisualProfile profile, final double age,
        final float scale) {
        return center(lobe, profile, age, scale, Vec3.ZERO);
    }

    public static Vec3 center(final BlastCloudLobe lobe,
        final WarheadClientVisualProfile profile, final double age,
        final float scale, final Vec3 wind) {
        if (age < lobe.spawnTick()) return new Vec3(Double.NaN, Double.NaN, Double.NaN);
        double raw = WarheadVisualMath.clamp(
            (age - lobe.spawnTick()) / Math.max(1.0, lobe.growthTicks()), 0.0, 1.0);
        double local = smooth(raw);
        double after = Math.max(0.0,
            age - lobe.spawnTick() - lobe.growthTicks());
        Vec3 result = switch (lobe.flowRole()) {
            case STEM -> stemCenter(lobe, profile, local, after);
            case CAP_CORE -> capCoreCenter(lobe, profile, raw, after);
            case CAP_ROLLING_RIM -> rimCenter(lobe, profile, raw, age);
        };
        if (profile.payloadType() == WarheadPayloadType.NUCLEAR) {
            double dissipating = dissipationProgress(profile, age);
            double spread = 1.0 + smooth(dissipating) * 0.42;
            result = new Vec3(result.x * spread,
                result.y + profile.maximumCloudHeight() * 0.055 * smooth(dissipating),
                result.z * spread).add(windOffset(lobe.flowRole(), profile, age, wind));
        }
        return result.scale(scale);
    }

    static double dissipationProgress(final WarheadClientVisualProfile profile,
        final double age) {
        return WarheadVisualMath.clamp((age - profile.cloudRiseEndTick())
            / (double) Math.max(1,
                profile.cloudDissipationEndTick() - profile.cloudRiseEndTick()),
            0.0, 1.0);
    }

    static Vec3 windOffset(final BlastCloudFlowRole role,
        final WarheadClientVisualProfile profile, final double age, final Vec3 wind) {
        if (wind == null || !wind.isFinite()) return Vec3.ZERO;
        Vec3 horizontal = new Vec3(wind.x, 0.0, wind.z);
        double speed = horizontal.length();
        if (speed <= 1.0E-5) return Vec3.ZERO;
        if (speed > 2.5) horizontal = horizontal.scale(2.5 / speed);
        double establishedAge = Math.max(0.0, age - 180.0);
        double driftTicks = Math.min(establishedAge, 2_400.0) * 0.010
            + Math.max(0.0, age - profile.cloudRiseEndTick()) * 0.024;
        double coupling = role == BlastCloudFlowRole.STEM ? 0.20 : 1.0;
        return horizontal.scale(driftTicks * coupling);
    }

    private static Vec3 stemCenter(final BlastCloudLobe lobe,
        final WarheadClientVisualProfile profile, final double local,
        final double after) {
        Vec3 linear = lobe.originOffset().lerp(lobe.finalOffset(), local);
        double inward = 1.0 - 0.18 * local;
        double amplitude = Math.max(1.0,
            Math.min(5.0, profile.smokeStemWidth() * 0.065));
        double turbulence = amplitude * (0.25 + 0.75 * local);
        double x = linear.x * inward
            + Math.sin(lobe.lateralPhase() + local * 7.0 + after * 0.018) * turbulence;
        double z = linear.z * inward
            + Math.cos(lobe.lateralPhase() * 0.83 + local * 6.0 + after * 0.016)
                * turbulence;
        double continuedRise = Math.min(profile.maximumCloudHeight() * 0.08,
            after * 0.018 * lobe.riseFactor());
        return new Vec3(x, linear.y + continuedRise, z);
    }

    private static Vec3 capCoreCenter(final BlastCloudLobe lobe,
        final WarheadClientVisualProfile profile, final double local,
        final double after) {
        Vec3 stemTop = new Vec3(0.0, profile.maximumCloudHeight() * 0.60, 0.0);
        Vec3 emerged = new Vec3(lobe.originOffset().x * 0.25,
            profile.maximumCloudHeight() * 0.74, lobe.originOffset().z * 0.25);
        Vec3 spread = new Vec3(lobe.finalOffset().x,
            Math.max(lobe.finalOffset().y, profile.maximumCloudHeight() * 0.82),
            lobe.finalOffset().z);
        Vec3 center;
        if (local < 0.30) center = cubic(stemTop, emerged, local / 0.30);
        else if (local < 0.75) center = cubic(emerged, spread, (local - 0.30) / 0.45);
        else center = cubic(spread, lobe.finalOffset(), (local - 0.75) / 0.25);
        double driftAngle = after * 0.0012 + lobe.flowPhase();
        double drift = 0.025 * profile.smokeCapWidth()
            * Math.sin(after * 0.009 + lobe.phase());
        return center.add(Math.cos(driftAngle) * drift,
            Math.sin(after * 0.008 + lobe.lateralPhase())
                * profile.maximumCloudHeight() * 0.008,
            Math.sin(driftAngle) * drift);
    }

    private static Vec3 rimCenter(final BlastCloudLobe lobe,
        final WarheadClientVisualProfile profile, final double local,
        final double age) {
        Vec3 stemTop = new Vec3(0.0, profile.maximumCloudHeight() * 0.58, 0.0);
        Vec3 capTop = new Vec3(lobe.finalOffset().x * 0.15,
            profile.maximumCloudHeight() * 0.79, lobe.finalOffset().z * 0.15);
        Vec3 outerTop = new Vec3(lobe.finalOffset().x,
            Math.max(lobe.finalOffset().y, profile.maximumCloudHeight() * 0.78),
            lobe.finalOffset().z);
        Vec3 growthPath = local < 0.30
            ? cubic(stemTop, capTop, local / 0.30)
            : cubic(capTop, outerTop, (local - 0.30) / 0.70);
        double circulationAge = Math.max(0.0,
            age - lobe.spawnTick() - lobe.growthTicks() * 0.65);
        double freeCirculation = Math.max(0.0,
            profile.cloudRiseEndTick() - lobe.spawnTick() - lobe.growthTicks() * 0.65);
        double beforeDissipation = Math.min(circulationAge, freeCirculation);
        double lateCirculation = Math.max(0.0, circulationAge - freeCirculation);
        double dampingWindow = Math.max(1.0,
            (profile.cloudDissipationEndTick() - profile.cloudRiseEndTick()) * 0.18);
        double dampedLate = dampingWindow
            * (1.0 - Math.exp(-lateCirculation / dampingWindow));
        double effectiveCirculationAge = beforeDissipation + dampedLate;
        double circulationAngle = lobe.flowPhase()
            + effectiveCirculationAge * lobe.circulationRate();
        double tubeRadius = profile.smokeCapWidth()
            * (0.09 + 0.025 * Math.sin(lobe.phase()));
        double verticalTube = profile.maximumCloudHeight()
            * (0.055 + 0.018 * Math.cos(lobe.phase()));
        double radial = profile.smokeCapWidth() * lobe.capRadialFraction()
            + Math.cos(circulationAngle) * tubeRadius;
        double y = profile.maximumCloudHeight() * lobe.capVerticalFraction()
            + Math.sin(circulationAngle) * verticalTube;
        double azimuth = lobe.rotation()
            + effectiveCirculationAge * Math.abs(lobe.circulationRate()) * 0.12;
        Vec3 circulating = new Vec3(Math.cos(azimuth) * radial, y,
            Math.sin(azimuth) * radial);
        double circulationBlend = smooth((local - 0.52) / 0.33);
        return growthPath.lerp(circulating, circulationBlend);
    }

    private static Vec3 cubic(final Vec3 from, final Vec3 to,
        final double value) {
        return from.lerp(to, smooth(value));
    }

    private static void billboard(final PoseStack.Pose pose,
        final VertexConsumer buffer, final Vec3 center, final float radius,
        final double rotation, final int red, final int green, final int blue,
        final float alpha, final Quaternionf camera) {
        float cosine = Mth.cos((float) rotation);
        float sine = Mth.sin((float) rotation);
        float ux = cosine * radius;
        float uy = sine * radius;
        float vx = -sine * radius;
        float vy = cosine * radius;
        int packedAlpha = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        vertex(pose, buffer, center, -ux - vx, -uy - vy, 0.0F, 1.0F,
            red, green, blue, packedAlpha, camera);
        vertex(pose, buffer, center, -ux + vx, -uy + vy, 0.0F, 0.0F,
            red, green, blue, packedAlpha, camera);
        vertex(pose, buffer, center, ux + vx, uy + vy, 1.0F, 0.0F,
            red, green, blue, packedAlpha, camera);
        vertex(pose, buffer, center, ux - vx, uy - vy, 1.0F, 1.0F,
            red, green, blue, packedAlpha, camera);
    }

    private static void vertex(final PoseStack.Pose pose,
        final VertexConsumer buffer, final Vec3 center, final float x,
        final float y, final float u, final float v, final int red,
        final int green, final int blue, final int alpha,
        final Quaternionf camera) {
        Vector3f offset = new Vector3f(x, y, 0.0F).rotate(camera);
        Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera);
        buffer.addVertex(pose, (float) center.x + offset.x,
                (float) center.y + offset.y, (float) center.z + offset.z)
            .setColor(red, green, blue, alpha).setUv(u, v).setOverlay(0)
            .setLight(0xA000A0).setNormal(pose, normal.x, normal.y, normal.z);
    }

    private static double smooth(final double value) {
        double t = WarheadVisualMath.clamp(value, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
