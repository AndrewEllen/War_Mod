package com.andye.warmod.icbm.client.render;

import com.andye.warmod.icbm.client.IcbmLaunchGroundSmokeManager;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Adds persistent launch-cloud lobes to Minecraft's translucent particle pass.
 * This draws after water and selects the proper Fabulous particle target.
 */
public final class IcbmLaunchGroundSmokeRenderer {
    private static final SingleQuadParticle.Layer LAYER = new SingleQuadParticle.Layer(true,
        Identifier.withDefaultNamespace("textures/particle/big_smoke_2.png"),
        IcbmRenderPipelines.LAUNCH_SMOKE_PARTICLE);

    private IcbmLaunchGroundSmokeRenderer() { }

    /**
     * Appends camera-relative, independently rotating smoke puffs. The policy
     * gives every ordinal a fixed birth and path, so a lower LOD skips a stable
     * stratified subset instead of recycling visible puffs.
     */
    public static void append(final QuadParticleRenderState particles,
        final IcbmLaunchGroundSmokeManager.LaunchCloud cloud, final double elapsed,
        final Vec3 cameraPosition, final Quaternionf camera) {
        if (particles == null || cloud == null || cloud.position() == null
            || !cloud.position().isFinite() || cameraPosition == null || !cameraPosition.isFinite()
            || camera == null || elapsed < 0.0 || elapsed >= IcbmLaunchGroundSmokePolicy.LIFETIME_TICKS) {
            return;
        }
        int total = Math.max(0, Math.min(IcbmLaunchGroundSmokePolicy.ICBM_LOBES, cloud.lobeCount()));
        double distance = cameraPosition.distanceTo(cloud.position());
        float distanceSizeScale = 1.0F + 0.14F * smooth(clamp((distance - 80.0) / 280.0));
        java.util.ArrayList<Puff> visible = new java.util.ArrayList<>(total);
        for (int ordinal = 0; ordinal < total; ordinal++) {
            // A rank is permanent for the cloud. Unlike selecting the first N
            // samples, crossing an LOD boundary cannot substitute one lobe's
            // path for another and visibly make the smoke jump.
            int rank = rank(ordinal, total, cloud.seed());
            float visibility = visibility(distance, rank);
            if (visibility <= 0.002F) continue;
            IcbmLaunchGroundSmokePolicy.Lobe lobe = IcbmLaunchGroundSmokePolicy.sample(
                cloud.seed(), ordinal, elapsed, cloud.scale());
            if (lobe.alpha() * visibility <= 0.002F) continue;

            // Lobes begin at the shared silo throat, then settle onto their
            // cached terrain samples as their radial ground roll reaches them.
            double groundY = cloud.position().y
                + (cloud.groundHeight(ordinal) - cloud.position().y) * lobe.terrainRollout();
            Vec3 center = new Vec3(cloud.position().x + lobe.x(), groundY + lobe.y(),
                cloud.position().z + lobe.z());
            Vec3 relative = center.subtract(cameraPosition);
            float size = Math.min(2.4F, lobe.radius() * distanceSizeScale);
            if (size <= 0.0F) continue;
            visible.add(new Puff(relative, lobe, visibility, size));
        }
        visible.sort(java.util.Comparator.comparingDouble(
            (Puff puff) -> puff.relative().lengthSqr()).reversed());
        for (Puff puff : visible) {
            Vec3 relative = puff.relative();
            var lobe = puff.lobe();
            Quaternionf rotation = new Quaternionf(camera).rotateZ(lobe.rotation());
            particles.add(LAYER, (float) relative.x, (float) relative.y, (float) relative.z,
                rotation.x, rotation.y, rotation.z, rotation.w, puff.size(),
                0.0F, 1.0F, 0.0F, 1.0F, argb(lobe, puff.visibility()), 0xE000E0);
        }
    }

    private record Puff(Vec3 relative, IcbmLaunchGroundSmokePolicy.Lobe lobe,
        float visibility, float size) { }

    private static int rank(final int ordinal, final int total, final long seed) {
        int offset = Math.floorMod((int) (seed ^ (seed >>> 32)), total);
        return Math.floorMod(ordinal * 197 + offset, total);
    }

    private static float visibility(final double distance, final int rank) {
        // Nested stable sets: 192 always persist, then the remaining cohorts
        // fade individually over a distance band rather than hard-switching.
        if (rank < 192) return 1.0F;
        if (rank < 320) return fadeOut(distance, 290.0, 350.0);
        if (rank < 640) return fadeOut(distance, 160.0, 200.0);
        return fadeOut(distance, 78.0, 114.0);
    }

    private static float fadeOut(final double distance, final double from, final double to) {
        return 1.0F - smooth(clamp((distance - from) / (to - from)));
    }

    private static float clamp(final double value) {
        return (float) Math.max(0.0, Math.min(1.0, value));
    }

    private static float smooth(final float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static int argb(final IcbmLaunchGroundSmokePolicy.Lobe lobe, final float visibility) {
        int alpha = Math.clamp(Math.round(lobe.alpha() * visibility * 255.0F), 0, 255);
        return (alpha << 24) | (lobe.red() << 16) | (lobe.green() << 8) | lobe.blue();
    }
}
