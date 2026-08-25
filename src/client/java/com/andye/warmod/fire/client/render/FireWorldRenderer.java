package com.andye.warmod.fire.client.render;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.FireVisualLodPolicy;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/** Packed analytical surface-fire renderer; only custom fire reads wind. */
public final class FireWorldRenderer {
    private static final double MAX_DISTANCE = 320.0;
    private static final double MIN_SMOKE_CLUSTER_DISTANCE = 288.0;
    private static final double MAX_SMOKE_CLUSTER_DISTANCE = 1_536.0;
    private static final int GPU_FIELD_CELL_SIZE = 64;
    private static final float PATCH_VISIBILITY_RADIUS = 7.0F;
    private static final float EMBER_VISIBILITY_RADIUS = 0.75F;
    private static final FireOcclusionCache OCCLUSION = new FireOcclusionCache();
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
        CameraProjection projection = CameraProjection.create(camera);
        OCCLUSION.begin(level, cameraPosition, level.getGameTime());
        double gameTime = level.getGameTime()
            + context.deltaTracker().getGameTimeDeltaPartialTick(true);
        boolean cpuFlames = !GpuParticleEngine.canRender(
            GpuParticleEngine.VisualLayer.FLAMES);
        boolean cpuSmoke = !GpuParticleEngine.canRender(
            GpuParticleEngine.VisualLayer.SMOKE);
        boolean cpuEmbers = !GpuParticleEngine.canRender(
            GpuParticleEngine.VisualLayer.EMBERS);
        boolean gpuFlames = !cpuFlames;
        boolean gpuSmoke = !cpuSmoke;
        boolean gpuEmbers = !cpuEmbers;
        List<FireRenderPatch> patches = new ArrayList<>();
        List<VisualPatch> clientPatches = ClientFireVisualManager.INSTANCE.snapshot(level);
        GpuParticleEngine.recordClientFirePatches(clientPatches.size());
        Map<Long, FireFieldBuilder> gpuFields = new LinkedHashMap<>();
        Map<Long, List<ProjectedPatch>> patchesByHost = new LinkedHashMap<>();
        for (VisualPatch patch : clientPatches) {
            Vec3 worldPosition = patch.anchor().position();
            double distance = worldPosition.distanceTo(cameraPosition);
            if (!Double.isFinite(distance) || distance > MAX_DISTANCE
                || !level.hasChunkAt(patch.anchor().host())
                || !projection.visible(worldPosition, PATCH_VISIBILITY_RADIUS)
                || !OCCLUSION.visible(level, cameraPosition, worldPosition, distance)) continue;
            double projectedHostDiameter = projection.projectedDiameter(worldPosition, 0.75F);
            patchesByHost.computeIfAbsent(patch.anchor().host().asLong(),
                ignored -> new ArrayList<>()).add(new ProjectedPatch(patch, worldPosition,
                    distance, projectedHostDiameter));
        }
        for (List<ProjectedPatch> hostPatches : patchesByHost.values()) {
            hostPatches.sort((left, right) -> Long.compareUnsigned(
                detailRank(left.patch()), detailRank(right.patch())));
            double projectedHostDiameter = hostPatches.getFirst().projectedHostDiameter();
            int limit = FireVisualLodPolicy.representativesPerHost(hostPatches.size(),
                projectedHostDiameter);
            for (int index = 0; index < limit; index++) {
                ProjectedPatch projected = hostPatches.get(index);
                VisualPatch patch = projected.patch();
                Vec3 worldPosition = projected.worldPosition();
            Vec3 wind = ClientFireVisualManager.INSTANCE.effectiveWind(worldPosition,
                patch.wind(), gameTime);
            if (gpuFlames || gpuSmoke || gpuEmbers) {
                field(gpuFields, worldPosition).patches.add(new FireFieldPatch(patch.id(),
                    worldPosition, wind, patch.intensity(), patch.heat(), patch.coverage(),
                    patch.smoke(), patch.seed()));
            }
            if (cpuFlames || cpuSmoke || cpuEmbers) {
                SmokeFlow smokeFlow = ClientSmokeFlowField.INSTANCE.request(level,
                    patch.anchor(), level.getGameTime());
                patches.add(new FireRenderPatch(worldPosition.subtract(cameraPosition),
                    patch.id(), patch.anchor().face(), patch.intensity(), patch.heat(), patch.coverage(),
                    patch.smoke(), patch.phase(), patch.seed(), patch.ignitionGameTime(),
					wind, patch.clumpStrength(), projected.distance(),
                    projected.projectedHostDiameter(),
                    FireVisualLodPolicy.particleScale(projected.projectedHostDiameter()),
                    smokeFlow));
                }
            }
        }
		List<FireRenderEmber> embers = new ArrayList<>();
		for (VisualEmber ember : ClientFireVisualManager.INSTANCE.emberSnapshot(level)) {
			Vec3 worldPosition = ember.position();
			double distance = worldPosition.distanceTo(cameraPosition);
			if (!Double.isFinite(distance) || distance > MAX_DISTANCE
                || !level.hasChunkAt(BlockPos.containing(worldPosition))
                || !projection.visible(worldPosition, EMBER_VISIBILITY_RADIUS)
                || !OCCLUSION.visible(level, cameraPosition, worldPosition, distance)) continue;
            double projectedEmberDiameter = projection.projectedDiameter(worldPosition, 0.10F);
            if (stableUnit(ember.id() ^ ember.seed())
                > FireVisualLodPolicy.emberRetention(projectedEmberDiameter)) continue;
            float emberLodScale = FireVisualLodPolicy.emberScale(projectedEmberDiameter);
            float emberImportance = Math.max(0.1F, ember.intensity()
                * (0.85F + (float) Math.min(1.5, ember.velocity().length()) * 0.35F));
            if (gpuEmbers) field(gpuFields, worldPosition).embers.add(new FireFieldEmber(
                ember.id(), worldPosition, ember.velocity(),
                Math.max(0.05F, ember.intensity()),
                (0.075F + ember.intensity() * 0.035F) * emberLodScale,
                emberImportance, ember.seed()));
            if (!cpuEmbers && !cpuSmoke) continue;
            List<FireRenderEmberTrail> trail = ember.trail().stream()
				.map(sample -> new FireRenderEmberTrail(
					sample.position().subtract(cameraPosition),
                    ClientFireVisualManager.INSTANCE.effectiveWind(sample.position(),
                        sample.wind(), gameTime), sample.gameTime()))
				.toList();
			embers.add(new FireRenderEmber(worldPosition.subtract(cameraPosition),
				ember.velocity(), ember.intensity(), ember.seed(), ember.startGameTime(),
				ember.lifetime(), distance, projectedEmberDiameter, emberLodScale, trail));
		}
        List<FireRenderSmokeCluster> smokeClusters = new ArrayList<>();
        for (VisualSmokeCluster cluster : ClientFireVisualManager.INSTANCE.smokeClusterSnapshot(level)) {
            Vec3 worldPosition = cluster.position();
            double distance = worldPosition.distanceTo(cameraPosition);
            /* Local patches supply detailed smoke.  Larger clustered plumes take
               over beyond that without duplicating the near-field effect. */
            if (!Double.isFinite(distance) || distance < MIN_SMOKE_CLUSTER_DISTANCE
                || distance > MAX_SMOKE_CLUSTER_DISTANCE
                || !level.hasChunkAt(BlockPos.containing(worldPosition))
                || !projection.visible(worldPosition.add(0.0, cluster.radius(), 0.0),
                    Math.max(2.0F, cluster.radius() * 1.75F))) continue;
            double projectedHostDiameter = projection.projectedDiameter(worldPosition, 0.75F);
            Vec3 wind = ClientFireVisualManager.INSTANCE.effectiveWind(worldPosition,
                cluster.wind(), gameTime);
            if (gpuSmoke) field(gpuFields, worldPosition).clusters.add(new FireFieldCluster(
                cluster.id(), worldPosition, wind, cluster.smoke(), cluster.heat(),
                cluster.radius(), cluster.memberCount(), cluster.seed()));
            if (cpuFlames || cpuSmoke) smokeClusters.add(new FireRenderSmokeCluster(
                worldPosition.subtract(cameraPosition), cluster.smoke(), cluster.heat(),
                cluster.radius(), wind, cluster.seed(), cluster.memberCount(), distance,
                projectedHostDiameter));
        }
        currentFrame = patches.isEmpty() && embers.isEmpty() && smokeClusters.isEmpty()
            ? RenderFrame.EMPTY : new RenderFrame(gameTime, orientation, List.copyOf(patches),
                List.copyOf(embers), List.copyOf(smokeClusters),
                cpuFlames, cpuSmoke, cpuEmbers);
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
        PoseStack poseStack = context.poseStack();
        if (poseStack == null) return;
        if (frame.cpuFlames()) context.submitNodeCollector().submitCustomGeometry(poseStack,
			WarheadRenderPipelines.FIREBALL_HOT,
			(pose, buffer) -> FireParticleRenderer.renderFlames(pose, buffer,
				frame.gameTime(), frame.patches(), frame.cameraOrientation()));
		if (frame.cpuEmbers() && !frame.embers().isEmpty())
			context.submitNodeCollector().submitCustomGeometry(poseStack,
			WarheadRenderPipelines.FIREBALL_HOT,
			(pose, buffer) -> FireParticleRenderer.renderFirebrands(pose, buffer,
				frame.gameTime(), frame.embers(), frame.cameraOrientation()));
		if (frame.cpuFlames() && !frame.smokeClusters().isEmpty())
			context.submitNodeCollector().submitCustomGeometry(
			poseStack, WarheadRenderPipelines.FIREBALL_HOT,
			(pose, buffer) -> FireParticleRenderer.renderFlameClusters(pose, buffer,
				frame.gameTime(), frame.smokeClusters(), frame.cameraOrientation()));
		if (frame.cpuEmbers()) context.submitNodeCollector().submitCustomGeometry(poseStack,
			WarheadRenderPipelines.FIREBALL_COOL,
			(pose, buffer) -> FireParticleRenderer.renderEmbers(pose, buffer,
				frame.gameTime(), frame.patches(), frame.cameraOrientation()));
		if (frame.cpuSmoke()) context.submitNodeCollector().submitCustomGeometry(poseStack,
			WarheadRenderPipelines.GROUND_DUST,
			(pose, buffer) -> FireParticleRenderer.renderSmoke(pose, buffer,
				frame.gameTime(), frame.patches(), frame.cameraOrientation()));
		if (frame.cpuSmoke() && !frame.smokeClusters().isEmpty())
			context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.GROUND_DUST,
            (pose, buffer) -> FireParticleRenderer.renderSmokeClusters(pose, buffer,
                frame.gameTime(), frame.smokeClusters(), frame.cameraOrientation()));
		if (frame.cpuSmoke() && !frame.embers().isEmpty())
			context.submitNodeCollector().submitCustomGeometry(poseStack,
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

    private static long detailRank(final VisualPatch patch) {
        return mix(patch.id() ^ patch.seed() ^ patch.anchor().key().hashCode());
    }

    private static double stableUnit(final long value) {
        return (mix(value) >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
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

    private record ProjectedPatch(VisualPatch patch, Vec3 worldPosition, double distance,
        double projectedHostDiameter) { }

    record FireRenderPatch(Vec3 relativePosition, long id, Direction face, float intensity,
        float heat, float coverage, float smoke, FirePhase phase, long seed,
		long ignitionGameTime, Vec3 wind, float clumpStrength, double distance,
        double projectedHostDiameter, float lodScale,
        SmokeFlow smokeFlow) { }
	record FireRenderEmber(Vec3 relativePosition, Vec3 velocity, float intensity,
		long seed, long startGameTime, int lifetime, double distance,
		double projectedDiameter, float lodScale,
		List<FireRenderEmberTrail> trail) { }
	record FireRenderEmberTrail(Vec3 relativePosition, Vec3 wind, long gameTime) { }
    record FireRenderSmokeCluster(Vec3 relativePosition, float smoke, float heat, float radius,
        Vec3 wind, long seed, int memberCount, double distance,
        double projectedHostDiameter) { }
    private record RenderFrame(double gameTime, Quaternionf cameraOrientation,
		List<FireRenderPatch> patches, List<FireRenderEmber> embers,
        List<FireRenderSmokeCluster> smokeClusters,
        boolean cpuFlames, boolean cpuSmoke, boolean cpuEmbers) {
		private static final RenderFrame EMPTY = new RenderFrame(0.0, new Quaternionf(),
			List.of(), List.of(), List.of(), false, false, false);
    }

    private record CameraProjection(Vec3 position, Matrix4f viewProjection,
        float projectionScale, int viewportHeight) {
        private static CameraProjection create(final CameraRenderState camera) {
            Matrix4f matrix = camera.projectionMatrix == null || camera.viewRotationMatrix == null
                ? null : new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
            float scale = camera.projectionMatrix == null ? 1.0F
                : Math.max(0.01F, Math.abs(camera.projectionMatrix.m11()));
            int height = Math.max(1, Minecraft.getInstance().getWindow().getHeight());
            return new CameraProjection(camera.pos, matrix, scale, height);
        }

        private boolean visible(final Vec3 center, final float radius) {
            if (viewProjection == null) return true;
            Vec3 relative = center.subtract(position);
            Vector4f clip = new Vector4f((float) relative.x, (float) relative.y,
                (float) relative.z, 1.0F).mul(viewProjection);
            if (clip.w <= 0.0F) return false;
            float allowance = Math.max(0.012F,
                radius * projectionScale / Math.max(0.01F, clip.w));
            return Math.abs(clip.x) <= clip.w * (1.0F + allowance)
                && Math.abs(clip.y) <= clip.w * (1.0F + allowance)
                && clip.z >= -clip.w && clip.z <= clip.w;
        }

        private double projectedDiameter(final Vec3 center, final float radius) {
            double distance = Math.max(0.25, center.distanceTo(position));
            return Math.max(0.0, radius * 2.0 * projectionScale
                * viewportHeight * 0.5 / distance);
        }
    }

    /** Coarse cached world-occlusion probes; depth testing still owns partial cells. */
    private static final class FireOcclusionCache {
        private static final int CELL_SIZE = 8;
        private static final int PROBES_PER_FRAME = 12;
        private static final long CACHE_TICKS = 8L;
        private final Map<Long, CachedOcclusion> cells = new HashMap<>();
        private ClientLevel cachedLevel;
        private long cameraCell = Long.MIN_VALUE;
        private long gameTick;
        private int probesRemaining;

        private void begin(final ClientLevel level, final Vec3 camera, final long now) {
            long nextCameraCell = BlockPos.asLong(
                Math.floorDiv((int) Math.floor(camera.x), 4),
                Math.floorDiv((int) Math.floor(camera.y), 4),
                Math.floorDiv((int) Math.floor(camera.z), 4));
            if (cachedLevel != level || cameraCell != nextCameraCell) {
                cells.clear();
                cachedLevel = level;
                cameraCell = nextCameraCell;
            }
            gameTick = now;
            probesRemaining = PROBES_PER_FRAME;
            if (cells.size() > 2_048)
                cells.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        }

        private boolean visible(final ClientLevel level, final Vec3 camera,
            final Vec3 target, final double distance) {
            if (distance <= 32.0) return true;
            int cellX = Math.floorDiv((int) Math.floor(target.x), CELL_SIZE);
            int cellY = Math.floorDiv((int) Math.floor(target.y), CELL_SIZE);
            int cellZ = Math.floorDiv((int) Math.floor(target.z), CELL_SIZE);
            long key = BlockPos.asLong(cellX, cellY, cellZ);
            CachedOcclusion cached = cells.get(key);
            if (cached != null && cached.expiresAt() >= gameTick) return cached.visible();
            if (probesRemaining-- <= 0) return true;

            Vec3 cellCenter = new Vec3((cellX + 0.5) * CELL_SIZE,
                (cellY + 0.5) * CELL_SIZE, (cellZ + 0.5) * CELL_SIZE);
            HitResult hit = level.clip(new ClipContext(camera, cellCenter,
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
            double cellRadius = Math.sqrt(3.0) * CELL_SIZE * 0.5;
            boolean result = hit.getType() == HitResult.Type.MISS
                || camera.distanceTo(hit.getLocation()) + cellRadius
                    >= camera.distanceTo(cellCenter);
            cells.put(key, new CachedOcclusion(result, gameTick + CACHE_TICKS));
            return result;
        }
    }

    private record CachedOcclusion(boolean visible, long expiresAt) { }
}
