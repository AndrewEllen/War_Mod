package com.andye.warmod.firearm.client;

import com.andye.warmod.item.ModItems;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Directional daylight scope reflection, visible only to the aimed-at viewer. */
public final class SniperGlintRenderer {
    private record Glint(Vec3 relative, float size) { }
    private record Frame(Quaternionf camera, List<Glint> glints) {
        private static final Frame EMPTY = new Frame(new Quaternionf(), List.of());
    }
    private static volatile Frame frame = Frame.EMPTY;
    private static boolean registered;
    private SniperGlintRenderer() { }
    public static void register() {
        if (registered) return;
        LevelExtractionEvents.END_EXTRACTION.register(SniperGlintRenderer::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(SniperGlintRenderer::render);
        registered = true;
    }
    private static void extract(final LevelExtractionContext context) {
        ClientLevel level = context.level();
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (level == null || camera == null || camera.pos == null || !level.isBrightOutside()) {
            frame = Frame.EMPTY; return;
        }
        double angle = Math.toRadians(level.environmentAttributes()
            .getValue(EnvironmentAttributes.SUN_ANGLE, camera.pos));
        Vec3 sun = new Vec3(Math.cos(angle), Math.sin(angle), 0.18).normalize();
        List<Glint> glints = new ArrayList<>();
        for (AbstractClientPlayer player : level.players()) {
            if (!player.isUsingItem() || !player.getUseItem().is(ModItems.SNIPER_RIFLE)) continue;
            Vec3 eye = player.getEyePosition();
            double distance = eye.distanceTo(camera.pos);
            if (distance < 18.0 || distance > 1_600.0
                || !level.canSeeSky(BlockPos.containing(eye))) continue;
            Vec3 toCamera = camera.pos.subtract(eye).normalize();
            if (player.getLookAngle().normalize().dot(toCamera) < 0.9985
                || sun.dot(toCamera) > -0.10) continue;
            if (level.clip(new ClipContext(camera.pos, eye, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty())).getType()
                != HitResult.Type.MISS) continue;
            glints.add(new Glint(eye.add(player.getLookAngle().scale(0.28))
                .subtract(camera.pos), (float) Mth.clamp(0.055 + distance * 0.00034,
                    0.055, 0.42)));
        }
        Quaternionf orientation = camera.orientation == null ? new Quaternionf()
            : new Quaternionf(camera.orientation);
        frame = glints.isEmpty() ? Frame.EMPTY : new Frame(orientation, List.copyOf(glints));
    }
    private static void render(final LevelRenderContext context) {
        Frame current = frame;
        if (current == Frame.EMPTY || context.poseStack() == null) return;
        for (Glint glint : current.glints) {
            PoseStack stack = context.poseStack(); stack.pushPose();
            stack.translate(glint.relative.x, glint.relative.y, glint.relative.z);
            context.submitNodeCollector().submitCustomGeometry(stack,
                WarheadRenderPipelines.NUCLEAR_FLASH,
                (pose, buffer) -> quad(pose, buffer, glint.size, current.camera));
            stack.popPose();
        }
    }
    private static void quad(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float size, final Quaternionf camera) {
        Vector3f right = new Vector3f(1, 0, 0).rotate(camera).mul(size);
        Vector3f up = new Vector3f(0, 1, 0).rotate(camera).mul(size);
        vertex(pose, buffer, -right.x - up.x, -right.y - up.y, -right.z - up.z, 0, 1);
        vertex(pose, buffer, -right.x + up.x, -right.y + up.y, -right.z + up.z, 0, 0);
        vertex(pose, buffer, right.x + up.x, right.y + up.y, right.z + up.z, 1, 0);
        vertex(pose, buffer, right.x - up.x, right.y - up.y, right.z - up.z, 1, 1);
    }
    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x, final float y, final float z, final float u, final float v) {
        buffer.addVertex(pose, x, y, z).setColor(236, 248, 255, 245).setUv(u, v)
            .setOverlay(0).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
    }
}
