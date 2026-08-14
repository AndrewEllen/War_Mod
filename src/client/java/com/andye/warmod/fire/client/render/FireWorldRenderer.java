package com.andye.warmod.fire.client.render;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.client.ClientFireVisualManager;
import com.andye.warmod.fire.client.ClientFireVisualManager.VisualPatch;
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
            currentFrame = RenderFrame.EMPTY; return;
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
            patches.add(new FireRenderPatch(worldPosition.subtract(cameraPosition),
                patch.anchor().face(), patch.intensity(), patch.heat(), patch.coverage(),
                patch.smoke(), patch.phase(), patch.seed(), patch.ignitionGameTime(),
                patch.wind(), distance));
        }
        currentFrame = patches.isEmpty() ? RenderFrame.EMPTY
            : new RenderFrame(gameTime, orientation, List.copyOf(patches));
    }

    private static void collectSubmits(final LevelRenderContext context) {
        RenderFrame frame = currentFrame;
        if (frame == RenderFrame.EMPTY || frame.patches().isEmpty()) return;
        PoseStack poseStack = context.poseStack();
        if (poseStack == null) return;
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.FIREBALL_HOT,
            (pose, buffer) -> FireParticleRenderer.renderFlames(pose, buffer,
                frame.gameTime(), frame.patches(), frame.cameraOrientation()));
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.FIREBALL_COOL,
            (pose, buffer) -> FireParticleRenderer.renderEmbers(pose, buffer,
                frame.gameTime(), frame.patches(), frame.cameraOrientation()));
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.HEAVY_SMOKE,
            (pose, buffer) -> FireParticleRenderer.renderSmoke(pose, buffer,
                frame.gameTime(), frame.patches(), frame.cameraOrientation()));
    }

    record FireRenderPatch(Vec3 relativePosition, Direction face, float intensity,
        float heat, float coverage, float smoke, FirePhase phase, long seed,
        long ignitionGameTime, Vec3 wind, double distance) { }
    private record RenderFrame(double gameTime, Quaternionf cameraOrientation,
        List<FireRenderPatch> patches) {
        private static final RenderFrame EMPTY = new RenderFrame(0.0, new Quaternionf(), List.of());
    }
}
