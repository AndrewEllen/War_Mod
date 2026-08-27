package com.andye.warmod.warhead.client.obscuration;

import com.andye.warmod.particle.gpu.GpuParticleEngine;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectClass;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectHandle;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EmitterCommand;
import com.andye.warmod.particle.gpu.GpuParticleEngine.ParticleType;
import com.andye.warmod.particle.gpu.GpuParticleEngine.VisualLayer;
import com.andye.warmod.warhead.client.obscuration.ClientNuclearTerrainObscurationManager.DustCell;
import com.andye.warmod.warhead.client.obscuration.ClientNuclearTerrainObscurationManager.ObscurationImpactView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Stable, mutation-driven terrain dust with GPU submission and CPU fallback. */
public final class NuclearTerrainObscurationRenderer {
    private static final double MAX_DISTANCE = 3_072.0;
    private static final int SCREEN_BIN_PIXELS = 64;
    private static final float TARGET_BIN_OPACITY = 0.78F;
    private static volatile Frame frame = Frame.EMPTY;
    private static boolean registered;

    private NuclearTerrainObscurationRenderer() { }

    public static void register() {
        if (registered) return;
        LevelExtractionEvents.END_EXTRACTION.register(
            NuclearTerrainObscurationRenderer::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(
            NuclearTerrainObscurationRenderer::submit);
        registered = true;
    }

    private static void extract(final LevelExtractionContext context) {
        ClientLevel level = context.level();
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (level == null || camera == null || camera.pos == null
            || camera.projectionMatrix == null || camera.viewRotationMatrix == null) {
            frame = Frame.EMPTY;
            return;
        }
        double gameTime = level.getGameTime()
            + context.deltaTracker().getGameTimeDeltaPartialTick(true);
        Matrix4f viewProjection = new Matrix4f(camera.projectionMatrix)
            .mul(camera.viewRotationMatrix);
        int viewportWidth = Math.max(1, Minecraft.getInstance().getWindow().getWidth());
        int viewportHeight = Math.max(1, Minecraft.getInstance().getWindow().getHeight());
        ArrayList<SelectedCell> selected = new ArrayList<>();
        for (ObscurationImpactView impact
            : ClientNuclearTerrainObscurationManager.INSTANCE.snapshot(level)) {
            selected.addAll(selectCells(impact, camera.pos, viewProjection,
                viewportWidth, viewportHeight, gameTime));
        }
        if (selected.isEmpty()) {
            frame = Frame.EMPTY;
            return;
        }
        if (GpuParticleEngine.canRender(VisualLayer.TERRAIN_OBSCURATION))
            submitGpu(selected);
        frame = new Frame(camera.pos, gameTime, List.copyOf(selected));
    }

    private static List<SelectedCell> selectCells(final ObscurationImpactView impact,
        final Vec3 camera, final Matrix4f viewProjection, final int viewportWidth,
        final int viewportHeight, final double gameTime) {
        ArrayList<SelectedCell> candidates = new ArrayList<>();
        for (DustCell cell : impact.cells()) {
            double distanceSquared = cell.groundPosition().distanceToSqr(camera);
            if (distanceSquared > MAX_DISTANCE * MAX_DISTANCE) continue;
            float fade = cellFade(cell, gameTime);
            if (fade <= 0.001F) continue;
            float opacity = Mth.clamp((0.17F + cell.density() * 0.19F) * fade,
                0.02F, 0.38F);
            float cardRadius = Mth.clamp(2.8F + cell.cellSize() * 0.12F
                + impact.visualScale() * 0.42F, 3.0F, 8.0F);
            float height = Mth.clamp(1.8F + impact.visualScale() * 0.85F
                + (float) unit(cell.seed(), 3) * 3.2F, 2.0F, 10.0F);
            candidates.add(new SelectedCell(impact, cell, opacity, cardRadius,
                height, distanceSquared));
        }
        candidates.sort(Comparator.comparingDouble(SelectedCell::distanceSquared));
        Map<Long, Float> binOpacity = new HashMap<>();
        ArrayList<SelectedCell> selected = new ArrayList<>(candidates.size());
        for (SelectedCell candidate : candidates) {
            Vec3 relative = candidate.cell().groundPosition().subtract(camera);
            Vector4f clip = new Vector4f((float) relative.x, (float) relative.y,
                (float) relative.z, 1.0F);
            viewProjection.transform(clip);
            if (clip.w <= 1.0E-5F) continue;
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (ndcX < -1.25F || ndcX > 1.25F || ndcY < -1.25F || ndcY > 1.25F)
                continue;
            int pixelX = Mth.floor((ndcX * 0.5F + 0.5F) * viewportWidth);
            int pixelY = Mth.floor((ndcY * 0.5F + 0.5F) * viewportHeight);
            int binX = Math.floorDiv(pixelX, SCREEN_BIN_PIXELS);
            int binY = Math.floorDiv(pixelY, SCREEN_BIN_PIXELS);
            long bin = ((long) binX << 32) ^ (binY & 0xFFFF_FFFFL);
            float accumulated = binOpacity.getOrDefault(bin, 0.0F);
            float admitted = TerrainObscurationOpticalBudget.admittedOpacity(
                accumulated, candidate.opacity(), TARGET_BIN_OPACITY);
            if (admitted <= 0.001F) continue;
            float combined = TerrainObscurationOpticalBudget.accumulate(
                accumulated, admitted);
            binOpacity.put(bin, combined);
            selected.add(new SelectedCell(candidate.impact(), candidate.cell(), admitted,
                candidate.cardRadius(), candidate.height(), candidate.distanceSquared()));
        }
        return List.copyOf(selected);
    }

    private static float cellFade(final DustCell cell, final double gameTime) {
        double spawnAge = Math.max(0.0, gameTime - cell.spawnGameTime());
        float fadeIn = (float) Mth.clamp(spawnAge / 10.0, 0.0, 1.0);
        if (cell.revealGameTime() == Long.MAX_VALUE) return fadeIn;
        double revealAge = Math.max(0.0, gameTime - cell.revealGameTime());
        double persistence = 300.0 + unit(cell.seed(), 2) * 240.0;
        float revealFade = (float) Math.pow(1.0 - Mth.clamp(
            (revealAge - 20.0) / persistence, 0.0, 1.0), 1.12);
        return fadeIn * revealFade;
    }

    private static void submitGpu(final List<SelectedCell> selected) {
        Map<ObscurationImpactView, ArrayList<EmitterCommand>> byImpact = new HashMap<>();
        for (SelectedCell item : selected) {
            DustCell cell = item.cell();
            DustColour colour = dustColour(cell.seed());
            EmitterCommand command = new EmitterCommand(cell.groundPosition(),
                new Vec3(0.0, 0.012 + item.height() * 0.003, 0.0),
                item.impact().visualScale(), 6.5F,
                colour.red() / 255.0F, colour.green() / 255.0F,
                colour.blue() / 255.0F, item.opacity(), item.cardRadius(),
                item.cardRadius() * 0.18F, 0.035F,
                Math.max(1, Math.round(1.0F + cell.density() * 1.5F)),
                (int) (cell.seed() ^ cell.seed() >>> 32), ParticleType.TERRAIN_DUST,
                0, 1.35F, VisualLayer.TERRAIN_OBSCURATION,
                cell.groundNormal(), 1);
            byImpact.computeIfAbsent(item.impact(), ignored -> new ArrayList<>()).add(command);
        }
        for (Map.Entry<ObscurationImpactView, ArrayList<EmitterCommand>> entry
            : byImpact.entrySet()) {
            ObscurationImpactView impact = entry.getKey();
            EffectHandle effect = GpuParticleEngine.beginEffect(
                EffectClass.TERRAIN_OBSCURATION, GpuParticleEngine.stableId(impact.id()),
                impact.center(), impact.destructionRadius() + 16.0F, 1.35F);
            effect.submitLayer(VisualLayer.TERRAIN_OBSCURATION,
                List.copyOf(entry.getValue()));
        }
    }

    private static void submit(final LevelRenderContext context) {
        Frame snapshot = frame;
        if (snapshot == Frame.EMPTY || snapshot.cells().isEmpty()
            || context.poseStack() == null
            || GpuParticleEngine.canRender(VisualLayer.TERRAIN_OBSCURATION)) return;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
            NuclearTerrainObscurationRenderPipelines.terrainDust(),
            (pose, buffer) -> render(pose, buffer, snapshot));
    }

    private static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Frame frame) {
        for (SelectedCell item : frame.cells()) {
            DustCell cell = item.cell();
            Vec3 center = cell.groundPosition().subtract(frame.camera());
            Vec3 normal = safeNormal(cell.groundNormal());
            Vec3 reference = Math.abs(normal.y) > 0.92
                ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 tangent = safeNormal(reference.cross(normal));
            Vec3 bitangent = safeNormal(normal.cross(tangent));
            DustColour colour = dustColour(cell.seed());
            addTerrainSheet(pose, buffer, center, tangent, bitangent, normal,
                item.cardRadius(), item.cardRadius()
                    * (0.72F + (float) unit(cell.seed(), 4) * 0.36F),
                colour, item.opacity());
            if (unit(cell.seed(), 5) > 0.72) {
                addSideWisp(pose, buffer, center, tangent, item.cardRadius() * 0.58F,
                    item.height(), colour, item.opacity() * 0.26F);
            }
        }
    }

    private static void addTerrainSheet(final PoseStack.Pose pose,
        final VertexConsumer buffer, final Vec3 center, final Vec3 tangent,
        final Vec3 bitangent, final Vec3 normal, final float halfWidth,
        final float halfDepth, final DustColour colour, final float alpha) {
        vertex(pose, buffer, center.add(tangent.scale(-halfWidth))
            .add(bitangent.scale(-halfDepth)), 0.0F, 1.0F, colour, alpha, normal);
        vertex(pose, buffer, center.add(tangent.scale(halfWidth))
            .add(bitangent.scale(-halfDepth)), 1.0F, 1.0F, colour, alpha, normal);
        vertex(pose, buffer, center.add(tangent.scale(halfWidth))
            .add(bitangent.scale(halfDepth)), 1.0F, 0.0F, colour, alpha, normal);
        vertex(pose, buffer, center.add(tangent.scale(-halfWidth))
            .add(bitangent.scale(halfDepth)), 0.0F, 0.0F, colour, alpha, normal);
    }

    private static void addSideWisp(final PoseStack.Pose pose,
        final VertexConsumer buffer, final Vec3 center, final Vec3 tangent,
        final float halfWidth, final float height, final DustColour colour,
        final float alpha) {
        Vec3 normal = safeNormal(new Vec3(-tangent.z, 0.0, tangent.x));
        vertex(pose, buffer, center.add(tangent.scale(-halfWidth)),
            0.0F, 1.0F, colour, alpha, normal);
        vertex(pose, buffer, center.add(tangent.scale(halfWidth)),
            1.0F, 1.0F, colour, alpha, normal);
        vertex(pose, buffer, center.add(tangent.scale(halfWidth)).add(0.0, height, 0.0),
            1.0F, 0.0F, colour, alpha, normal);
        vertex(pose, buffer, center.add(tangent.scale(-halfWidth)).add(0.0, height, 0.0),
            0.0F, 0.0F, colour, alpha, normal);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 position, final float u, final float v, final DustColour colour,
        final float alpha, final Vec3 normal) {
        buffer.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
            .setColor(colour.red(), colour.green(), colour.blue(),
                Mth.clamp(Math.round(alpha * 255.0F), 0, 255))
            .setUv(u, v).setOverlay(0).setLight(0xB000B0)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static Vec3 safeNormal(final Vec3 value) {
        return value == null || !value.isFinite() || value.lengthSqr() < 1.0E-8
            ? new Vec3(0.0, 1.0, 0.0) : value.normalize();
    }

    private static DustColour dustColour(final long seed) {
        int palette = (int) (unit(seed, 6) * 4.0);
        int variation = (int) (unit(seed, 7) * 17.0) - 8;
        return switch (palette) {
            case 0 -> new DustColour(182 + variation, 178 + variation, 169 + variation);
            case 1 -> new DustColour(164 + variation, 158 + variation, 147 + variation);
            case 2 -> new DustColour(198 + variation, 190 + variation, 174 + variation);
            default -> new DustColour(151 + variation, 147 + variation, 141 + variation);
        };
    }

    private static double unit(final long value, final int lane) {
        long mixed = value + lane * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * 0x1.0p-53;
    }

    private record DustColour(int red, int green, int blue) { }

    private record SelectedCell(ObscurationImpactView impact, DustCell cell,
        float opacity, float cardRadius, float height, double distanceSquared) { }

    private record Frame(Vec3 camera, double gameTime, List<SelectedCell> cells) {
        private static final Frame EMPTY = new Frame(Vec3.ZERO, 0.0, List.of());
    }
}
