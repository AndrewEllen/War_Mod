package com.andye.warmod.fire.client.render;

import com.andye.warmod.diagnostics.client.ClientPerformanceTelemetry;
import com.andye.warmod.fire.FireRepresentationPlan;
import com.andye.warmod.fire.FireRepresentationPlan.Card;
import com.andye.warmod.fire.FireRepresentationPlan.CellPlan;
import com.andye.warmod.fire.FireSurfaceAnchor;
import com.andye.warmod.fire.FireVisualLodPolicy;
import com.andye.warmod.fire.client.ClientFireVisualManager;
import com.andye.warmod.fire.client.ClientFireVisualManager.VisualCell;
import com.andye.warmod.fire.client.ClientFireVisualManager.VisualEmber;
import com.andye.warmod.fire.client.ClientSmokeFlowField;
import com.andye.warmod.fire.client.ClientSmokeFlowField.SmokeFlow;
import com.andye.warmod.fire.network.FireVisualCell;
import com.andye.warmod.particle.gpu.GpuParticleEngine;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldCell;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldEmber;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldSubmission;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.andye.warmod.warhead.client.render.WarheadRenderSettings;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Shared coverage-plan renderer for both packed CPU and GPU fire paths. */
public final class FireWorldRenderer {
    private static final double MAX_EMBER_DISTANCE = 320.0;
    private static final int GPU_FIELD_CELL_SIZE = 64;
    private static final float EMBER_VISIBILITY_RADIUS = 0.75F;
    private static final FireOcclusionCache OCCLUSION = new FireOcclusionCache();
    private static volatile RenderFrame currentFrame = RenderFrame.EMPTY;
    private static volatile FireRenderStats lastStats = FireRenderStats.EMPTY;
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
            lastStats = FireRenderStats.EMPTY;
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
        boolean cpuFlames = !GpuParticleEngine.canRender(GpuParticleEngine.VisualLayer.FLAMES);
        boolean cpuSmoke = !GpuParticleEngine.canRender(GpuParticleEngine.VisualLayer.SMOKE);
        boolean cpuEmbers = !GpuParticleEngine.canRender(GpuParticleEngine.VisualLayer.EMBERS);
        boolean gpuFlames = !cpuFlames;
        boolean gpuSmoke = !cpuSmoke;
        boolean gpuEmbers = !cpuEmbers;
        List<VisualCell> clientCells = ClientFireVisualManager.INSTANCE.snapshot(level);
        GpuParticleEngine.recordClientFirePatches(clientCells.size());
        ArrayList<FireRenderCell> renderCells = new ArrayList<>(clientCells.size());
        Map<Long, FireFieldBuilder> gpuFields = new LinkedHashMap<>();
        int sourceHosts = 0;
        int representedCells = 0;
        int cpuFlameCards = 0;
        int cpuSmokeCards = 0;

        for (VisualCell visual : clientCells) {
            FireVisualCell cell = visual.cell();
            sourceHosts += Math.max(1, cell.hostCount());
            Vec3 worldPosition = cell.centroid();
            double distance = worldPosition.distanceTo(cameraPosition);
            float representationWeight = visual.transitionWeight()
                * cell.band().weight(distance);
            float boundsRadius = cell.boundingRadius();
            if (!Double.isFinite(distance) || representationWeight <= 0.001F
                || !projection.visible(worldPosition, boundsRadius)) continue;
            BlockPos centerBlock = BlockPos.containing(worldPosition);
            if (distance <= 320.0 && level.hasChunkAt(centerBlock)
                && !OCCLUSION.visible(level, cameraPosition, worldPosition, distance)) continue;
            double projectedCellDiameter = projection.projectedDiameter(worldPosition,
                Math.max(0.5F, Math.max((float) cell.extents().x,
                    Math.max((float) cell.extents().y, (float) cell.extents().z))));
            int detailLevel = ClientFireVisualManager.INSTANCE.lodLevel(level,
                cell.id(), projectedCellDiameter);
            CellPlan plan = FireRepresentationPlan.plan(cell, projectedCellDiameter,
                WarheadRenderSettings.qualityScale(), representationWeight, detailLevel);
            if (plan.flames().isEmpty() && plan.smoke().isEmpty()) continue;
            representedCells++;
            if (cpuFlames) cpuFlameCards += plan.flames().size();
            if (cpuSmoke) cpuSmokeCards += plan.smoke().size();
            Vec3 wind = ClientFireVisualManager.INSTANCE.effectiveWind(worldPosition,
                cell.wind(), gameTime);
            if (gpuFlames || gpuSmoke) field(gpuFields, worldPosition).cells.add(
                new FireFieldCell(cell.id(), worldPosition, wind,
                    cell.averageIntensity(), cell.maximumHeat(), boundsRadius,
                    filteredPlan(plan, gpuFlames, gpuSmoke), cell.seed()));
            if (cpuFlames || cpuSmoke || cpuEmbers) {
                SmokeFlow smokeFlow = smokeFlow(level, cell, centerBlock);
                renderCells.add(new FireRenderCell(cell, relativePlan(plan, cameraPosition),
                    wind, smokeFlow, distance, projectedCellDiameter));
            }
        }

        List<FireRenderEmber> embers = new ArrayList<>();
        for (VisualEmber ember : ClientFireVisualManager.INSTANCE.emberSnapshot(level)) {
            Vec3 worldPosition = ember.position();
            double distance = worldPosition.distanceTo(cameraPosition);
            if (!Double.isFinite(distance) || distance > MAX_EMBER_DISTANCE
                || !projection.visible(worldPosition, EMBER_VISIBILITY_RADIUS)
                || (level.hasChunkAt(BlockPos.containing(worldPosition))
                    && !OCCLUSION.visible(level, cameraPosition, worldPosition, distance))) continue;
            double projectedEmberDiameter = projection.projectedDiameter(worldPosition, 0.10F);
            if (stableUnit(ember.id() ^ ember.seed())
                > FireVisualLodPolicy.emberRetention(projectedEmberDiameter)) continue;
            float emberLodScale = FireVisualLodPolicy.emberScale(projectedEmberDiameter);
            float emberImportance = Math.max(0.1F, ember.intensity()
                * (0.85F + (float) Math.min(1.5, ember.velocity().length()) * 0.35F));
            if (gpuEmbers) field(gpuFields, worldPosition).embers.add(new FireFieldEmber(
                ember.id(), worldPosition, ember.velocity(), Math.max(0.05F, ember.intensity()),
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

        currentFrame = renderCells.isEmpty() && embers.isEmpty()
            ? RenderFrame.EMPTY : new RenderFrame(gameTime, orientation,
                List.copyOf(renderCells), List.copyOf(embers),
                cpuFlames, cpuSmoke, cpuEmbers);
        for (FireFieldBuilder builder : gpuFields.values()) {
            FireFieldSubmission submission = builder.finish();
            if (submission != null) GpuParticleEngine.submitFireField(submission);
        }
        lastStats = new FireRenderStats(sourceHosts, clientCells.size(), representedCells,
            cpuFlameCards, cpuSmokeCards);
        ClientPerformanceTelemetry.recordFireNanos(
            Math.max(0L, System.nanoTime() - extractionStarted));
    }

    private static SmokeFlow smokeFlow(final ClientLevel level,
        final FireVisualCell cell, final BlockPos centerBlock) {
        if (cell.cellSize() > 2 || !level.hasChunkAt(centerBlock))
            return new SmokeFlow(8.5F, 3.0F, Vec3.ZERO, false, 1.0F);
        FireSurfaceAnchor anchor = new FireSurfaceAnchor(centerBlock,
            cell.dominantFace(), 0.5F, 0.5F, 0.5F);
        return ClientSmokeFlowField.INSTANCE.request(level, anchor, level.getGameTime());
    }

    private static CellPlan filteredPlan(final CellPlan source,
        final boolean flames, final boolean smoke) {
        return new CellPlan(flames ? source.flames() : List.of(),
            smoke ? source.smoke() : List.of(), source.sparkCount(),
            source.representedFlameArea(), source.representedSmokeOpticalDepth(),
            source.representationWeight());
    }

    private static CellPlan relativePlan(final CellPlan source, final Vec3 camera) {
        return new CellPlan(relativeCards(source.flames(), camera),
            relativeCards(source.smoke(), camera), source.sparkCount(),
            source.representedFlameArea(), source.representedSmokeOpticalDepth(),
            source.representationWeight());
    }

    private static List<Card> relativeCards(final List<Card> cards, final Vec3 camera) {
        return cards.stream().map(card -> new Card(card.position().subtract(camera),
            card.radius(), card.opacity(), card.seed())).toList();
    }

    private static void collectSubmits(final LevelRenderContext context) {
        RenderFrame frame = currentFrame;
        if (frame == RenderFrame.EMPTY || (frame.cells().isEmpty() && frame.embers().isEmpty()))
            return;
        PoseStack poseStack = context.poseStack();
        if (poseStack == null) return;
        if (frame.cpuFlames()) context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.FIREBALL_HOT,
            (pose, buffer) -> FireParticleRenderer.renderFlames(pose, buffer,
                frame.gameTime(), frame.cells(), frame.cameraOrientation()));
        if (frame.cpuEmbers() && !frame.embers().isEmpty())
            context.submitNodeCollector().submitCustomGeometry(poseStack,
                WarheadRenderPipelines.FIREBALL_HOT,
                (pose, buffer) -> FireParticleRenderer.renderFirebrands(pose, buffer,
                    frame.gameTime(), frame.embers(), frame.cameraOrientation()));
        if (frame.cpuEmbers()) context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.FIREBALL_COOL,
            (pose, buffer) -> FireParticleRenderer.renderEmbers(pose, buffer,
                frame.gameTime(), frame.cells(), frame.cameraOrientation()));
        if (frame.cpuSmoke()) context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.GROUND_DUST,
            (pose, buffer) -> FireParticleRenderer.renderSmoke(pose, buffer,
                frame.gameTime(), frame.cells(), frame.cameraOrientation()));
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

    private static double stableUnit(final long value) {
        return (mix(value) >>> 11) * 0x1.0p-53;
    }

    public static FireRenderStats debugStats() { return lastStats; }

    public record FireRenderStats(int sourceHosts, int aggregatedCells,
        int visibleCells, int cpuFlameCards, int cpuSmokeCards) {
        private static final FireRenderStats EMPTY = new FireRenderStats(0, 0, 0, 0, 0);
    }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static final class FireFieldBuilder {
        private final long regionId;
        private final List<FireFieldCell> cells = new ArrayList<>();
        private final List<FireFieldEmber> embers = new ArrayList<>();

        private FireFieldBuilder(final long regionId) { this.regionId = regionId; }

        private FireFieldSubmission finish() {
            if (cells.isEmpty() && embers.isEmpty()) return null;
            double x = 0.0, y = 0.0, z = 0.0;
            int count = 0;
            for (FireFieldCell cell : cells) {
                x += cell.position().x; y += cell.position().y; z += cell.position().z;
                count++;
            }
            for (FireFieldEmber ember : embers) {
                x += ember.position().x; y += ember.position().y; z += ember.position().z;
                count++;
            }
            Vec3 center = new Vec3(x / Math.max(1, count), y / Math.max(1, count),
                z / Math.max(1, count));
            double radius = 4.0;
            for (FireFieldCell cell : cells)
                radius = Math.max(radius, center.distanceTo(cell.position())
                    + cell.boundsRadius());
            for (FireFieldEmber ember : embers)
                radius = Math.max(radius, center.distanceTo(ember.position()) + 2.0);
            return new FireFieldSubmission(regionId, center, (float) radius,
                List.copyOf(cells), List.copyOf(embers));
        }
    }

    record FireRenderCell(FireVisualCell cell, CellPlan plan, Vec3 wind,
        SmokeFlow smokeFlow, double distance, double projectedCellDiameter) { }
    record FireRenderEmber(Vec3 relativePosition, Vec3 velocity, float intensity,
        long seed, long startGameTime, int lifetime, double distance,
        double projectedDiameter, float lodScale,
        List<FireRenderEmberTrail> trail) { }
    record FireRenderEmberTrail(Vec3 relativePosition, Vec3 wind, long gameTime) { }
    private record RenderFrame(double gameTime, Quaternionf cameraOrientation,
        List<FireRenderCell> cells, List<FireRenderEmber> embers,
        boolean cpuFlames, boolean cpuSmoke, boolean cpuEmbers) {
        private static final RenderFrame EMPTY = new RenderFrame(0.0, new Quaternionf(),
            List.of(), List.of(), false, false, false);
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
            if (center.distanceToSqr(position) <= (double) radius * radius) return true;
            Vec3 relative = center.subtract(position);
            return new FrustumIntersection(viewProjection).testSphere(
                (float) relative.x, (float) relative.y, (float) relative.z, radius);
        }

        private double projectedDiameter(final Vec3 center, final float radius) {
            double distance = Math.max(0.25, center.distanceTo(position));
            return Math.max(0.0, radius * 2.0 * projectionScale
                * viewportHeight * 0.5 / distance);
        }
    }

    /** Coarse cached world-occlusion probes; depth testing owns partial cells. */
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
                cells.clear(); cachedLevel = level; cameraCell = nextCameraCell;
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
