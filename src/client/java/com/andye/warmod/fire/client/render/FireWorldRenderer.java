package com.andye.warmod.fire.client.render;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.client.ClientFireVisualManager;
import com.andye.warmod.fire.client.ClientFireVisualManager.VisualPatch;
import com.andye.warmod.fire.client.ClientFireVisualManager.VisualEmber;
import com.andye.warmod.fire.client.ClientFireVisualManager.VisualSmokeCluster;
import com.andye.warmod.fire.client.ClientSmokeFlowField;
import com.andye.warmod.fire.client.ClientSmokeFlowField.SmokeFlow;
import com.andye.warmod.particle.gpu.GpuParticleEngine;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EmitterCommand;
import com.andye.warmod.particle.gpu.GpuParticleEngine.ParticleType;
import com.andye.warmod.diagnostics.client.ClientPerformanceTelemetry;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Packed analytical surface-fire renderer; only custom fire reads wind. */
public final class FireWorldRenderer {
    private static final double MAX_DISTANCE = 320.0;
    private static final double MIN_SMOKE_CLUSTER_DISTANCE = 288.0;
    private static final double MAX_SMOKE_CLUSTER_DISTANCE = 1_536.0;
    private static volatile RenderFrame currentFrame = RenderFrame.EMPTY;
    private static boolean registered;

    private FireWorldRenderer() { }

    public static void register() {
        if (registered) return;
        LevelExtractionEvents.END_EXTRACTION.register(FireWorldRenderer::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(FireWorldRenderer::collectSubmits);
        registered = true;
    }

    private static void extract(final LevelExtractionContext context) {
        long extractionStarted = System.nanoTime();
        ClientLevel level = context.level();
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (level == null || camera == null || camera.pos == null) {
            currentFrame = RenderFrame.EMPTY;
            ClientPerformanceTelemetry.recordFireNanos(
                Math.max(0L, System.nanoTime() - extractionStarted));
            return;
        }
        Vec3 cameraPosition = camera.pos;
        Quaternionf orientation = camera.orientation == null
            ? new Quaternionf() : new Quaternionf(camera.orientation);
        double gameTime = level.getGameTime()
            + context.deltaTracker().getGameTimeDeltaPartialTick(true);
        List<FireRenderPatch> patches = new ArrayList<>();
        for (VisualPatch patch : ClientFireVisualManager.INSTANCE.snapshot(level)) {
            Vec3 worldPosition = patch.anchor().position();
            double distance = worldPosition.distanceTo(cameraPosition);
            if (!Double.isFinite(distance) || distance > MAX_DISTANCE) continue;
            Vec3 wind = ClientFireVisualManager.INSTANCE.effectiveWind(worldPosition,
                patch.wind(), gameTime);
            SmokeFlow smokeFlow = ClientSmokeFlowField.INSTANCE.request(level,
                patch.anchor(), level.getGameTime());
            submitGpuFire(worldPosition, wind, patch.intensity(), patch.heat(),
                patch.coverage(), patch.smoke(), patch.seed(), distance);
            patches.add(new FireRenderPatch(worldPosition.subtract(cameraPosition),
                patch.anchor().face(), patch.intensity(), patch.heat(), patch.coverage(),
                patch.smoke(), patch.phase(), patch.seed(), patch.ignitionGameTime(),
				wind, patch.clumpStrength(), distance, smokeFlow));
        }
		List<FireRenderEmber> embers = new ArrayList<>();
		for (VisualEmber ember : ClientFireVisualManager.INSTANCE.emberSnapshot(level)) {
			Vec3 worldPosition = ember.position();
			double distance = worldPosition.distanceTo(cameraPosition);
			if (!Double.isFinite(distance) || distance > MAX_DISTANCE) continue;
			GpuParticleEngine.submit(new EmitterCommand(worldPosition, ember.velocity(), 1.0F,
				0.55F, 1.0F, 0.42F, 0.08F, 0.085F, 0.08F, 0.30F,
				24, foldSeed(ember.seed()), ParticleType.EMBER, 0));
			List<FireRenderEmberTrail> trail = ember.trail().stream()
				.map(sample -> new FireRenderEmberTrail(
					sample.position().subtract(cameraPosition),
                    ClientFireVisualManager.INSTANCE.effectiveWind(sample.position(),
                        sample.wind(), gameTime), sample.gameTime()))
				.toList();
			embers.add(new FireRenderEmber(worldPosition.subtract(cameraPosition),
				ember.velocity(), ember.intensity(), ember.seed(), ember.startGameTime(),
				ember.lifetime(), distance, trail));
		}
        List<FireRenderSmokeCluster> smokeClusters = new ArrayList<>();
        for (VisualSmokeCluster cluster : ClientFireVisualManager.INSTANCE.smokeClusterSnapshot(level)) {
            Vec3 worldPosition = cluster.position();
            double distance = worldPosition.distanceTo(cameraPosition);
            /* Local patches supply detailed smoke.  Larger clustered plumes take
               over beyond that without duplicating the near-field effect. */
            if (!Double.isFinite(distance) || distance < MIN_SMOKE_CLUSTER_DISTANCE
                || distance > MAX_SMOKE_CLUSTER_DISTANCE) continue;
            Vec3 wind = ClientFireVisualManager.INSTANCE.effectiveWind(worldPosition,
                cluster.wind(), gameTime);
            float clusterScale = Math.max(1.0F, cluster.radius());
            GpuParticleEngine.submit(new EmitterCommand(worldPosition,
                wind.scale(0.18).add(0.0, 0.85, 0.0), clusterScale, 5.5F,
                0.17F, 0.18F, 0.17F, Math.max(2.0F, clusterScale * 0.35F),
                Math.max(1.0F, clusterScale * 0.45F), 0.38F,
                Math.min(900, 70 + cluster.memberCount() * 3), foldSeed(cluster.seed()),
                ParticleType.SMOKE, 0));
            smokeClusters.add(new FireRenderSmokeCluster(worldPosition.subtract(cameraPosition),
                cluster.smoke(), cluster.heat(), cluster.radius(), wind, cluster.seed(),
                cluster.memberCount(), distance));
        }
        currentFrame = patches.isEmpty() && embers.isEmpty() && smokeClusters.isEmpty()
            ? RenderFrame.EMPTY : new RenderFrame(gameTime, orientation, List.copyOf(patches),
                List.copyOf(embers), List.copyOf(smokeClusters));
        ClientPerformanceTelemetry.recordFireNanos(
            Math.max(0L, System.nanoTime() - extractionStarted));
    }

    private static void collectSubmits(final LevelRenderContext context) {
        RenderFrame frame = currentFrame;
        if (frame == RenderFrame.EMPTY || (frame.patches().isEmpty() && frame.embers().isEmpty()
            && frame.smokeClusters().isEmpty())) return;
        if (GpuParticleEngine.isGpuActive()) return;
        PoseStack poseStack = context.poseStack();
        if (poseStack == null) return;
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.FIREBALL_HOT,
            (pose, buffer) -> FireParticleRenderer.renderFlames(pose, buffer,
                frame.gameTime(), frame.patches(), frame.cameraOrientation()));
		if (!frame.embers().isEmpty()) context.submitNodeCollector().submitCustomGeometry(poseStack,
			WarheadRenderPipelines.FIREBALL_HOT,
			(pose, buffer) -> FireParticleRenderer.renderFirebrands(pose, buffer,
				frame.gameTime(), frame.embers(), frame.cameraOrientation()));
		if (!frame.smokeClusters().isEmpty()) context.submitNodeCollector().submitCustomGeometry(
			poseStack, WarheadRenderPipelines.FIREBALL_HOT,
			(pose, buffer) -> FireParticleRenderer.renderFlameClusters(pose, buffer,
				frame.gameTime(), frame.smokeClusters(), frame.cameraOrientation()));
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.FIREBALL_COOL,
            (pose, buffer) -> FireParticleRenderer.renderEmbers(pose, buffer,
                frame.gameTime(), frame.patches(), frame.cameraOrientation()));
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.GROUND_DUST,
            (pose, buffer) -> FireParticleRenderer.renderSmoke(pose, buffer,
                frame.gameTime(), frame.patches(), frame.cameraOrientation()));
		if (!frame.smokeClusters().isEmpty()) context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.GROUND_DUST,
            (pose, buffer) -> FireParticleRenderer.renderSmokeClusters(pose, buffer,
                frame.gameTime(), frame.smokeClusters(), frame.cameraOrientation()));
		if (!frame.embers().isEmpty()) context.submitNodeCollector().submitCustomGeometry(poseStack,
			WarheadRenderPipelines.GROUND_DUST,
			(pose, buffer) -> FireParticleRenderer.renderFirebrandSmoke(pose, buffer,
				frame.gameTime(), frame.embers(), frame.cameraOrientation()));
    }

    private static void submitGpuFire(final Vec3 position, final Vec3 wind,
        final float intensity, final float heat, final float coverage,
        final float smoke, final long seed, final double distance) {
        float lod = distance < 96.0 ? 1.0F : distance < 224.0 ? 0.55F : 0.24F;
        float scale = Math.max(0.18F, coverage * (0.55F + intensity * 0.55F));
        int folded = foldSeed(seed);
        GpuParticleEngine.submit(new EmitterCommand(position,
            wind.scale(0.10).add(0.0, 1.2 + heat * 0.9, 0.0), scale,
            0.75F + intensity * 0.45F, 1.0F, 0.22F + heat * 0.34F, 0.035F,
            0.13F + scale * 0.20F, 0.10F + scale * 0.24F, 0.50F,
            Math.max(3, Math.round((42.0F + intensity * 80.0F) * lod)), folded,
            ParticleType.FIRE, 0));
        if (smoke > 0.02F) {
            GpuParticleEngine.submit(new EmitterCommand(position.add(0.0, 0.3, 0.0),
                wind.scale(0.22).add(0.0, 0.72 + heat * 0.36, 0.0), scale,
                3.2F + smoke * 2.4F, 0.16F, 0.17F, 0.16F,
                0.45F + scale * 0.48F, 0.18F + scale * 0.34F, 0.32F,
                Math.max(2, Math.round((18.0F + smoke * 46.0F) * lod)),
                folded ^ 0x534D4F4B, ParticleType.SMOKE, 0));
        }
    }

    private static int foldSeed(final long seed) { return (int) (seed ^ seed >>> 32); }

    record FireRenderPatch(Vec3 relativePosition, Direction face, float intensity,
        float heat, float coverage, float smoke, FirePhase phase, long seed,
		long ignitionGameTime, Vec3 wind, float clumpStrength, double distance,
        SmokeFlow smokeFlow) { }
	record FireRenderEmber(Vec3 relativePosition, Vec3 velocity, float intensity,
		long seed, long startGameTime, int lifetime, double distance,
		List<FireRenderEmberTrail> trail) { }
	record FireRenderEmberTrail(Vec3 relativePosition, Vec3 wind, long gameTime) { }
    record FireRenderSmokeCluster(Vec3 relativePosition, float smoke, float heat, float radius,
        Vec3 wind, long seed, int memberCount, double distance) { }
    private record RenderFrame(double gameTime, Quaternionf cameraOrientation,
		List<FireRenderPatch> patches, List<FireRenderEmber> embers,
        List<FireRenderSmokeCluster> smokeClusters) {
		private static final RenderFrame EMPTY = new RenderFrame(0.0, new Quaternionf(),
			List.of(), List.of(), List.of());
    }
}
