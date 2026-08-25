package com.andye.warmod.warhead.client.curtain;

import com.andye.warmod.particle.gpu.GpuParticleEngine;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectClass;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectHandle;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EmitterCommand;
import com.andye.warmod.particle.gpu.GpuParticleEngine.ParticleType;
import com.andye.warmod.particle.gpu.GpuParticleEngine.VisualLayer;
import com.andye.warmod.warhead.client.curtain.ClientNuclearCurtainManager.CurtainAnchor;
import com.andye.warmod.warhead.client.curtain.ClientNuclearCurtainManager.CurtainBandView;
import com.andye.warmod.warhead.client.curtain.ClientNuclearCurtainManager.CurtainImpactView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Independent packed analytical dust curtain; it never allocates Minecraft particles. */
public final class NuclearDestructionCurtainRenderer {
    private static final double MAX_DISTANCE = 3_072.0;
    private static final double BAND_HOLD_TICKS = 42.0;
    private static final double BAND_FADE_TICKS = 210.0;
    private static volatile Frame frame = Frame.EMPTY;
    private static boolean registered;

    private NuclearDestructionCurtainRenderer() { }

    public static void register() {
        if (registered) return;
        LevelExtractionEvents.END_EXTRACTION.register(NuclearDestructionCurtainRenderer::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(NuclearDestructionCurtainRenderer::submit);
        registered = true;
    }

    private static void extract(final LevelExtractionContext context) {
        ClientLevel level = context.level();
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (level == null || camera == null || camera.pos == null) { frame = Frame.EMPTY; return; }
        List<CurtainImpactView> visible = new ArrayList<>();
        for (CurtainImpactView impact : ClientNuclearCurtainManager.INSTANCE.snapshot(level)) {
            if (impact.center().distanceTo(camera.pos) <= MAX_DISTANCE + 96.0
                && !impact.bands().isEmpty()) {
                visible.add(impact);
                if (GpuParticleEngine.canRender(VisualLayer.GROUND_CURTAIN))
                    submitGpuCurtain(impact, camera.pos, level.getGameTime());
            }
        }
        frame = visible.isEmpty() ? Frame.EMPTY : new Frame(camera.pos,
            camera.orientation == null ? new Quaternionf() : new Quaternionf(camera.orientation),
            level.getGameTime() + context.deltaTracker().getGameTimeDeltaPartialTick(true),
            List.copyOf(visible));
    }

    private static void submit(final LevelRenderContext context) {
        Frame snapshot = frame;
        if (snapshot == Frame.EMPTY || snapshot.impacts().isEmpty() || context.poseStack() == null) return;
        if (GpuParticleEngine.canRender(VisualLayer.GROUND_CURTAIN)) return;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
            NuclearCurtainRenderPipelines.curtain(),
			(pose, buffer) -> render(pose, buffer, snapshot));
    }

    private static void submitGpuCurtain(final CurtainImpactView impact,
        final Vec3 camera, final long gameTime) {
        int submitted = 0;
        List<EmitterCommand> curtain = new ArrayList<>();
        for (CurtainBandView band : impact.bands()) {
            double age = Math.max(0.0, gameTime - band.spawnGameTime());
            if (age > BAND_HOLD_TICKS + BAND_FADE_TICKS) continue;
            float fade = (float) Math.pow(1.0 - Mth.clamp(
                (age - BAND_HOLD_TICKS) / BAND_FADE_TICKS, 0.0, 1.0), 1.18);
            List<CurtainAnchor> anchors = band.anchors();
            for (int index = 0; index < anchors.size() && submitted < 1_536; index++) {
                CurtainAnchor anchor = anchors.get(index);
                double distance = anchor.position().distanceTo(camera);
                int stride = distance > 960.0 ? 6 : distance > 480.0 ? 3 : 1;
                if (index % stride != 0) continue;
                DustColour colour = dustColour(anchor.seed());
                curtain.add(new EmitterCommand(anchor.position(),
                    new Vec3(0.0, 0.18 + anchor.height() * 0.12, 0.0),
                    impact.visualScale(), 5.8F,
                    colour.red() / 255.0F, colour.green() / 255.0F,
                    colour.blue() / 255.0F, Math.max(1.4F, anchor.width() * 0.24F),
                    Math.max(0.8F, anchor.width() * 0.32F), 0.35F,
                    Math.max(1, Math.round(8.0F * fade)),
                    (int) (anchor.seed() ^ anchor.seed() >>> 32),
                    ParticleType.CURTAIN, 0));
                submitted++;
            }
            if (submitted >= 1_536) break;
        }
        if (!curtain.isEmpty()) {
            float bounds = 96.0F + impact.visualScale() * 640.0F;
            EffectHandle effect = GpuParticleEngine.beginEffect(EffectClass.CURTAIN,
                GpuParticleEngine.stableId(impact.id()), impact.center(), bounds, 1.25F);
            effect.submitLayer(VisualLayer.GROUND_CURTAIN, List.copyOf(curtain));
        }
    }

    private static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Frame frame) {
        for (CurtainImpactView impact : frame.impacts()) {
            for (CurtainBandView band : impact.bands()) {
                double age = Math.max(0.0, frame.gameTime() - band.spawnGameTime());
				double fadeProgress = Mth.clamp((age - BAND_HOLD_TICKS) / BAND_FADE_TICKS,
					0.0, 1.0);
				float fade = (float) Math.pow(1.0 - fadeProgress, 1.18);
				double completionAge = impact.completionGameTime() == Long.MIN_VALUE ? 0.0
					: Math.max(0.0, frame.gameTime() - impact.completionGameTime());
				if (impact.completionGameTime() != Long.MIN_VALUE) {
					fade = Math.min(fade, (float) Math.pow(
						Math.max(0.0, 1.0 - completionAge / 180.0), 1.10));
				}
				if (fade <= 0.001F) continue;
				double settledAge = Math.min(age, 32.0);
                List<CurtainAnchor> anchors = band.anchors();
                for (int index = 0; index < anchors.size(); index++) {
                    CurtainAnchor anchor = anchors.get(index);
                    double distanceSquared = anchor.position().distanceToSqr(frame.camera());
                    if (distanceSquared > MAX_DISTANCE * MAX_DISTANCE)
                        continue;
                    double distance = Math.sqrt(distanceSquared);
                    int stride = distance > 960.0 ? 4 : distance > 480.0 ? 2 : 1;
                    if (index % stride != 0) continue;
                    long seed = anchor.seed();
					double radialX = anchor.position().x - impact.center().x;
					double radialZ = anchor.position().z - impact.center().z;
					double radialLength = Math.sqrt(radialX * radialX + radialZ * radialZ);
					float rx = radialLength < 1.0E-4 ? 1.0F : (float) (radialX / radialLength);
					float rz = radialLength < 1.0E-4 ? 0.0F : (float) (radialZ / radialLength);
					float tx = -rz;
					float tz = rx;
					double curl = Math.sin(frame.gameTime() * 0.10 + unit(seed, 0) * Mth.TWO_PI)
						* (0.16 + unit(seed, 1) * 0.24);
					float width = anchor.width() * (1.0F + (float) settledAge / 32.0F * 0.16F);
					float height = anchor.height() * (0.72F + (float) unit(seed, 2) * 0.24F);
					Vec3 center = anchor.position().subtract(frame.camera()).add(
						tx * curl, 0.08 + unit(seed, 3) * 0.10, tz * curl);
					DustColour colour = dustColour(seed);
					float alpha = fade * (0.24F + (float) unit(seed, 4) * 0.16F);
					addGroundSheet(pose, buffer, center, tx, tz, rx, rz,
						width * 0.68F, width * (0.62F + (float) unit(seed, 5) * 0.20F),
						colour, alpha);
					/* A shallow skirt gives the sheet volume without reinstating the
					   tall vertical cards that exposed the terrain underneath. */
					addVerticalWisp(pose, buffer, center, tx, tz, width * 0.54F,
                        height * 0.72F, colour, alpha * 0.34F);
					if (unit(seed, 6) > 0.72 && stride == 1) {
						addVerticalWisp(pose, buffer, center.add(0.0, 0.10, 0.0), rx, rz,
							width * 0.30F, height * 0.82F, dustColour(seed ^ 0x415348L),
							alpha * 0.22F);
					}
                }
            }
        }
    }

    private static void addGroundSheet(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 center, final float tx, final float tz, final float rx, final float rz,
        final float halfWidth, final float halfDepth, final DustColour colour, final float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        vertex(pose, buffer, center, -tx * halfWidth - rx * halfDepth, 0.0F,
            -tz * halfWidth - rz * halfDepth, 0.0F, 1.0F, colour, a, 0.0F, 1.0F, 0.0F);
        vertex(pose, buffer, center, tx * halfWidth - rx * halfDepth, 0.0F,
            tz * halfWidth - rz * halfDepth, 1.0F, 1.0F, colour, a, 0.0F, 1.0F, 0.0F);
        vertex(pose, buffer, center, tx * halfWidth + rx * halfDepth, 0.0F,
            tz * halfWidth + rz * halfDepth, 1.0F, 0.0F, colour, a, 0.0F, 1.0F, 0.0F);
        vertex(pose, buffer, center, -tx * halfWidth + rx * halfDepth, 0.0F,
            -tz * halfWidth + rz * halfDepth, 0.0F, 0.0F, colour, a, 0.0F, 1.0F, 0.0F);
    }

    private static void addVerticalWisp(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 center, final float dx, final float dz, final float halfWidth,
        final float height, final DustColour colour, final float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        float nx = -dz;
        float nz = dx;
        vertex(pose, buffer, center, -dx * halfWidth, 0.0F, -dz * halfWidth,
            0.0F, 1.0F, colour, a, nx, 0.0F, nz);
        vertex(pose, buffer, center, dx * halfWidth, 0.0F, dz * halfWidth,
            1.0F, 1.0F, colour, a, nx, 0.0F, nz);
        vertex(pose, buffer, center, dx * halfWidth, height, dz * halfWidth,
            1.0F, 0.0F, colour, a, nx, 0.0F, nz);
        vertex(pose, buffer, center, -dx * halfWidth, height, -dz * halfWidth,
            0.0F, 0.0F, colour, a, nx, 0.0F, nz);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 center, final float x, final float y, final float z, final float u,
        final float v, final DustColour colour, final int alpha,
        final float normalX, final float normalY, final float normalZ) {
        buffer.addVertex(pose, (float) center.x + x, (float) center.y + y, (float) center.z + z)
            .setColor(colour.red(), colour.green(), colour.blue(), alpha)
            .setUv(u, v).setOverlay(0).setLight(0xB000B0)
            .setNormal(pose, normalX, normalY, normalZ);
    }

    private static DustColour dustColour(final long seed) {
        int palette = (int) (unit(seed, 7) * 5.0);
        int variation = (int) (unit(seed, 8) * 19.0) - 9;
        return switch (palette) {
            case 0 -> new DustColour(78 + variation, 78 + variation, 80 + variation);
            case 1 -> new DustColour(112 + variation, 108 + variation, 101 + variation);
            case 2 -> new DustColour(105 + variation, 88 + variation, 70 + variation);
            case 3 -> new DustColour(132 + variation, 116 + variation, 94 + variation);
            default -> new DustColour(66 + variation, 64 + variation, 62 + variation);
        };
    }

    private static double unit(final long value, final int lane) {
        long mixed = mix(value + lane * 0x9E3779B97F4A7C15L);
        return (mixed >>> 11) * 0x1.0p-53;
    }
    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private record DustColour(int red, int green, int blue) { }

    private record Frame(Vec3 camera, Quaternionf orientation, double gameTime,
        List<CurtainImpactView> impacts) {
        private static final Frame EMPTY = new Frame(Vec3.ZERO, new Quaternionf(), 0.0, List.of());
    }
}
