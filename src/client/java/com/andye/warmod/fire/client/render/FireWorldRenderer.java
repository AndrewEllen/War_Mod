package com.andye.warmod.fire.client.render;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.client.ClientFireVisualManager;
import com.andye.warmod.fire.client.ClientFireVisualManager.VisualPatch;
import com.andye.warmod.fire.client.ClientFireVisualManager.VisualEmber;
import com.andye.warmod.fire.client.ClientFireVisualManager.VisualSmokeCluster;
import com.andye.warmod.fire.client.ClientSmokeFlowField;
import com.andye.warmod.fire.client.ClientSmokeFlowField.SmokeFlow;
import com.andye.warmod.particle.gpu.GpuParticleEngine;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldCluster;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldEmber;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldPatch;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldSubmission;
import com.andye.warmod.diagnostics.client.ClientPerformanceTelemetry;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final int GPU_FIELD_CELL_SIZE = 64;
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
            GpuParticleEngine.recordClientFirePatches(0);
            ClientPerformanceTelemetry.recordFireNanos(
                Math.max(0L, System.nanoTime() - extractionStarted));
            return;
        }
        Vec3 cameraPosition = camera.pos;
        Quaternionf orientation = camera.orientation == null
            ? new Quaternionf() : new Quaternionf(camera.orientation);
        double gameTime = level.getGameTime()
            + context.deltaTracker().getGameTimeDeltaPartialTick(true);
        boolean packedFallback = !GpuParticleEngine.isGpuActive();
        List<FireRenderPatch> patches = new ArrayList<>();
        List<VisualPatch> clientPatches = ClientFireVisualManager.INSTANCE.snapshot(level);
        GpuParticleEngine.recordClientFirePatches(clientPatches.size());
        Map<Long, FireFieldBuilder> gpuFields = new LinkedHashMap<>();
        for (VisualPatch patch : clientPatches) {
            Vec3 worldPosition = patch.anchor().position();
            double distance = worldPosition.distanceTo(cameraPosition);
            if (!Double.isFinite(distance) || distance > MAX_DISTANCE) continue;
            Vec3 wind = ClientFireVisualManager.INSTANCE.effectiveWind(worldPosition,
                patch.wind(), gameTime);
            field(gpuFields, worldPosition).patches.add(new FireFieldPatch(patch.id(),
                worldPosition, wind, patch.intensity(), patch.heat(), patch.coverage(),
                patch.smoke(), patch.seed()));
            if (packedFallback) {
                SmokeFlow smokeFlow = ClientSmokeFlowField.INSTANCE.request(level,
                    patch.anchor(), level.getGameTime());
                patches.add(new FireRenderPatch(worldPosition.subtract(cameraPosition),
                    patch.anchor().face(), patch.intensity(), patch.heat(), patch.coverage(),
                    patch.smoke(), patch.phase(), patch.seed(), patch.ignitionGameTime(),
					wind, patch.clumpStrength(), distance, smokeFlow));
            }
        }
		List<FireRenderEmber> embers = new ArrayList<>();
		for (VisualEmber ember : ClientFireVisualManager.INSTANCE.emberSnapshot(level)) {
			Vec3 worldPosition = ember.position();
			double distance = worldPosition.distanceTo(cameraPosition);
			if (!Double.isFinite(distance) || distance > MAX_DISTANCE) continue;
            float emberImportance = Math.max(0.1F, ember.intensity()
                * (0.85F + (float) Math.min(1.5, ember.velocity().length()) * 0.35F));
            field(gpuFields, worldPosition).embers.add(new FireFieldEmber(ember.id(),
                worldPosition, ember.velocity(), Math.max(0.05F, ember.intensity()),
                0.075F + ember.intensity() * 0.035F, emberImportance, ember.seed()));
            if (!packedFallback) continue;
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
            field(gpuFields, worldPosition).clusters.add(new FireFieldCluster(cluster.id(),
                worldPosition, wind, cluster.smoke(), cluster.heat(), cluster.radius(),
                cluster.memberCount(), cluster.seed()));
            if (packedFallback) smokeClusters.add(new FireRenderSmokeCluster(
                worldPosition.subtract(cameraPosition), cluster.smoke(), cluster.heat(),
                cluster.radius(), wind, cluster.seed(), cluster.memberCount(), distance));
        }
        currentFrame = patches.isEmpty() && embers.isEmpty() && smokeClusters.isEmpty()
            ? RenderFrame.EMPTY : new RenderFrame(gameTime, orientation, List.copyOf(patches),
                List.copyOf(embers), List.copyOf(smokeClusters));
        for (FireFieldBuilder builder : gpuFields.values()) {
            FireFieldSubmission submission = builder.finish();
            if (submission != null) GpuParticleEngine.submitFireField(submission);
        }
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

    private static FireFieldBuilder field(final Map<Long, FireFieldBuilder> fields,
        final Vec3 position) {
        int cellX = Math.floorDiv((int) Math.floor(position.x), GPU_FIELD_CELL_SIZE);
        int cellZ = Math.floorDiv((int) Math.floor(position.z), GPU_FIELD_CELL_SIZE);
        long key = ((long) cellX << 32) ^ (cellZ & 0xFFFF_FFFFL);
        return fields.computeIfAbsent(key, ignored -> new FireFieldBuilder(key));
    }

    private static final class FireFieldBuilder {
        private final long regionId;
        private final List<FireFieldPatch> patches = new ArrayList<>();
        private final List<FireFieldEmber> embers = new ArrayList<>();
        private final List<FireFieldCluster> clusters = new ArrayList<>();

        private FireFieldBuilder(final long regionId) { this.regionId = regionId; }

        private FireFieldSubmission finish() {
            if (patches.isEmpty() && embers.isEmpty() && clusters.isEmpty()) return null;
            double x = 0.0, y = 0.0, z = 0.0;
            int count = 0;
            for (FireFieldPatch patch : patches) {
                x += patch.position().x; y += patch.position().y; z += patch.position().z;
                count++;
            }
            for (FireFieldEmber ember : embers) {
                x += ember.position().x; y += ember.position().y; z += ember.position().z;
                count++;
            }
            for (FireFieldCluster cluster : clusters) {
                x += cluster.position().x; y += cluster.position().y; z += cluster.position().z;
                count++;
            }
            Vec3 center = new Vec3(x / Math.max(1, count), y / Math.max(1, count),
                z / Math.max(1, count));
            double radius = 4.0;
            for (FireFieldPatch patch : patches)
                radius = Math.max(radius, center.distanceTo(patch.position()) + 2.0);
            for (FireFieldEmber ember : embers)
                radius = Math.max(radius, center.distanceTo(ember.position()) + 2.0);
            for (FireFieldCluster cluster : clusters)
                radius = Math.max(radius, center.distanceTo(cluster.position()) + cluster.radius());
            return new FireFieldSubmission(regionId, center, (float) Math.min(128.0, radius),
                List.copyOf(patches), List.copyOf(embers), List.copyOf(clusters));
        }
    }

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
