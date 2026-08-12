package com.andye.warmod.fire.client.render;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.client.ClientFireVisualManager;
import com.andye.warmod.fire.client.ClientFireVisualManager.VisualCell;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Packed analytical fire renderer; it does not create Minecraft Particle instances. */
public final class FireWorldRenderer {
    private static final double MAX_DISTANCE = 192.0;
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
        ClientLevel level = context.level();
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (level == null || camera == null || camera.pos == null) {
            currentFrame = RenderFrame.EMPTY;
            return;
        }
        Vec3 cameraPosition = camera.pos;
        Quaternionf orientation = camera.orientation == null
            ? new Quaternionf() : new Quaternionf(camera.orientation);
        double age = level.getGameTime() + context.deltaTracker().getGameTimeDeltaPartialTick(true);
        List<FireRenderCell> cells = new ArrayList<>();
        for (VisualCell cell : ClientFireVisualManager.INSTANCE.snapshot(level)) {
            /* Cells are anchored to fuel blocks; lift the analytical field to the
               exposed block volume instead of burying it behind an opaque cube. */
            Vec3 worldPosition = Vec3.atBottomCenterOf(cell.position()).add(0.0, 0.82, 0.0);
            double distance = worldPosition.distanceTo(cameraPosition);
            if (!Double.isFinite(distance) || distance > MAX_DISTANCE) continue;
            cells.add(new FireRenderCell(worldPosition.subtract(cameraPosition), cell.intensity(),
                cell.heat(), cell.phase(), cell.seed(), cell.wind(), distance));
        }
        currentFrame = cells.isEmpty() ? RenderFrame.EMPTY
            : new RenderFrame(age, orientation, List.copyOf(cells));
    }

    private static void collectSubmits(final LevelRenderContext context) {
        RenderFrame frame = currentFrame;
        if (frame == RenderFrame.EMPTY || frame.cells().isEmpty()) return;
        PoseStack poseStack = context.poseStack();
        if (poseStack == null) return;

        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.FIREBALL_HOT,
            (pose, buffer) -> FireParticleRenderer.renderFlames(pose, buffer,
                frame.age(), frame.cells(), frame.cameraOrientation()));
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.FIREBALL_COOL,
            (pose, buffer) -> FireParticleRenderer.renderEmbers(pose, buffer,
                frame.age(), frame.cells(), frame.cameraOrientation()));
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.HEAVY_SMOKE,
            (pose, buffer) -> FireParticleRenderer.renderSmoke(pose, buffer,
                frame.age(), frame.cells(), frame.cameraOrientation()));
    }

    record FireRenderCell(Vec3 relativePosition, float intensity, float heat,
        FirePhase phase, long seed, Vec3 wind, double distance) { }
    private record RenderFrame(double age, Quaternionf cameraOrientation,
        List<FireRenderCell> cells) {
        private static final RenderFrame EMPTY = new RenderFrame(0.0, new Quaternionf(), List.of());
    }
}
