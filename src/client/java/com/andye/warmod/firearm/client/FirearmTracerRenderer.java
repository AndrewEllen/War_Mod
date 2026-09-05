package com.andye.warmod.firearm.client;

import com.andye.warmod.client.model.BlockbenchGameplayMeshes;
import com.andye.warmod.client.model.BlockbenchGameplayMeshes.Model;
import com.andye.warmod.client.model.BlockbenchModelRenderType;
import com.andye.warmod.firearm.FirearmType;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Full-bright analytical tracers retained briefly even for same-tick hits. */
public final class FirearmTracerRenderer {
    private record Streak(Vec3 start, Vec3 end, FirearmType type, float alpha) { }
    private record Frame(Vec3 camera, List<Streak> streaks) {
        private static final Frame EMPTY = new Frame(Vec3.ZERO, List.of());
    }
    private static volatile Frame frame = Frame.EMPTY;
    private static boolean registered;
    private FirearmTracerRenderer() { }
    public static void register() {
        if (registered) return;
        LevelExtractionEvents.END_EXTRACTION.register(FirearmTracerRenderer::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(FirearmTracerRenderer::render);
        registered = true;
    }
    private static void extract(final LevelExtractionContext context) {
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (context.level() == null || camera == null || camera.pos == null) {
            frame = Frame.EMPTY; return;
        }
        double now = context.level().getGameTime()
            + context.deltaTracker().getGameTimeDeltaPartialTick(true);
        List<Streak> streaks = new ArrayList<>();
        for (FirearmTracerManager.Tracer tracer : FirearmTracerManager.snapshot(now)) {
            double age = Math.max(0.0, now - tracer.startTime());
            Vec3 end = tracer.impact() == null ? tracer.position(age) : tracer.impact();
            double trailTicks = switch (tracer.type()) {
                case PISTOL -> 0.24; case ASSAULT_RIFLE -> 0.20; case SNIPER_RIFLE -> 0.17;
            };
            Vec3 start = tracer.position(Math.max(0.0, age - trailTicks));
            if (tracer.impact() != null) {
                Vec3 direction = end.subtract(start);
                if (direction.lengthSqr() > 1.0E-6) start = end.subtract(direction.normalize()
                    .scale(Math.min(direction.length(), tracer.type().muzzleSpeed() * trailTicks)));
            }
            if (start.isFinite() && end.isFinite()) streaks.add(new Streak(start, end,
                tracer.type(), tracer.impact() == null ? 1.0F
                    : (float) Math.max(0.12, 1.0 - (now - tracer.impactTime()) / 4.0)));
        }
        frame = streaks.isEmpty() ? Frame.EMPTY : new Frame(camera.pos, List.copyOf(streaks));
    }
    private static void render(final LevelRenderContext context) {
        Frame current = frame;
        if (current == Frame.EMPTY || context.poseStack() == null) return;
        for (Streak streak : current.streaks) {
            PoseStack stack = context.poseStack(); stack.pushPose();
            Vec3 offset = streak.start.subtract(current.camera);
            stack.translate(offset.x, offset.y, offset.z);
            Vec3 direction = streak.end.subtract(streak.start);
            context.submitNodeCollector().submitCustomGeometry(stack,
                WarheadRenderPipelines.NUCLEAR_FLASH,
                (pose, buffer) -> ribbons(pose, buffer, direction, streak.type, streak.alpha));
            if (direction.lengthSqr() > 1.0E-8) {
                stack.translate(direction.x, direction.y, direction.z);
                Vector3f heading = new Vector3f((float)direction.x,
                    (float)direction.y, (float)direction.z).normalize();
                stack.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), heading));
                context.submitNodeCollector().submitCustomGeometry(stack,
                    BlockbenchModelRenderType.SOLID,
                    (pose, buffer) -> BlockbenchGameplayMeshes.render(pose, buffer,
                        bulletModel(streak.type), 0.075F, 0.0F, 0.0F, 0.0F, 0xF000F0));
            }
            stack.popPose();
        }
    }
    private static Model bulletModel(final FirearmType type) {
        return switch (type) {
            case PISTOL -> Model.PISTOL_BULLET;
            case ASSAULT_RIFLE -> Model.RIFLE_BULLET;
            case SNIPER_RIFLE -> Model.SNIPER_BULLET;
        };
    }
    private static void ribbons(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 direction, final FirearmType type, final float alpha) {
        if (direction.lengthSqr() < 1.0E-8) return;
        Vec3 forward = direction.normalize();
        Vec3 sideA = forward.cross(new Vec3(0.0, 1.0, 0.0));
        if (sideA.lengthSqr() < 1.0E-8) sideA = new Vec3(1.0, 0.0, 0.0);
        double width = type == FirearmType.SNIPER_RIFLE ? 0.075
            : type == FirearmType.ASSAULT_RIFLE ? 0.060 : 0.052;
        sideA = sideA.normalize().scale(width);
        Vec3 sideB = forward.cross(sideA).normalize().scale(width);
        ribbon(pose, buffer, direction, sideA, alpha);
        ribbon(pose, buffer, direction, sideB, alpha);
    }
    private static void ribbon(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 end, final Vec3 side, final float alpha) {
        vertex(pose, buffer, side, 255, 252, 216, (int) (255 * alpha));
        vertex(pose, buffer, side.scale(-1), 255, 252, 216, (int) (255 * alpha));
        vertex(pose, buffer, end.subtract(side), 255, 126, 22, (int) (105 * alpha));
        vertex(pose, buffer, end.add(side), 255, 126, 22, (int) (105 * alpha));
    }
    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 value, final int red, final int green, final int blue, final int alpha) {
        buffer.addVertex(pose, (float) value.x, (float) value.y, (float) value.z)
            .setColor(red, green, blue, alpha).setUv(0.5F, 0.5F).setOverlay(0)
            .setLight(0xF000F0).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
