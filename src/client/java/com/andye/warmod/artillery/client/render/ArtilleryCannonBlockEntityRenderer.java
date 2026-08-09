package com.andye.warmod.artillery.client.render;

import com.andye.warmod.WarMod;
import com.andye.warmod.artillery.ArtilleryTrajectory;
import com.andye.warmod.block.ArtilleryCannonBlock;
import com.andye.warmod.block.entity.ArtilleryCannonBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Target-driven barrel renderer; the block model supplies the fixed carriage. */
public final class ArtilleryCannonBlockEntityRenderer
    implements BlockEntityRenderer<ArtilleryCannonBlockEntity, ArtilleryCannonRenderState> {
    private static final RenderType BARREL = RenderType.create("war_mod_artillery_barrel",
        RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT)
            .withTexture("Sampler0", Identifier.fromNamespaceAndPath(WarMod.MOD_ID,
                "textures/block/artillery_barrel.png"))
            .useLightmap().useOverlay().createRenderSetup());

    public ArtilleryCannonBlockEntityRenderer(final BlockEntityRendererProvider.Context context) { }

    @Override
    public ArtilleryCannonRenderState createRenderState() {
        return new ArtilleryCannonRenderState();
    }

    @Override
    public void extractRenderState(final ArtilleryCannonBlockEntity cannon,
        final ArtilleryCannonRenderState state, final float partialTick, final Vec3 camera,
        final ModelFeatureRenderer.@Nullable CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(cannon, state, partialTick, camera, overlay);
        Direction facing = cannon.getBlockState().getValue(ArtilleryCannonBlock.FACING);
        state.yawDegrees = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> -90.0F;
            default -> 0.0F;
        };
        state.elevationDegrees = 8.0F;
        state.hasTarget = cannon.target() != null;
        if (cannon.target() == null) return;

        Vec3 origin = Vec3.atCenterOf(cannon.getBlockPos()).add(0.0, 0.55, 0.0);
        ArtilleryTrajectory.solve(origin, cannon.target().position()).ifPresent(velocity -> {
            state.yawDegrees = (float)Math.toDegrees(Math.atan2(velocity.x, -velocity.z));
            state.elevationDegrees = (float)Math.toDegrees(
                Math.atan2(velocity.y, velocity.horizontalDistance()));
        });
    }

    @Override
    public void submit(final ArtilleryCannonRenderState state, final PoseStack poses,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        poses.pushPose();
        poses.translate(0.5, 0.62, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(state.yawDegrees));
        poses.mulPose(Axis.XP.rotationDegrees(state.elevationDegrees));
        collector.submitCustomGeometry(poses, BARREL,
            (pose, buffer) -> renderBarrel(pose, buffer, state.lightCoords));
        poses.popPose();
    }

    private static void renderBarrel(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int light) {
        box(pose, buffer, -0.13F, -0.13F, -1.22F, 0.13F, 0.13F, 0.36F, light);
        box(pose, buffer, -0.20F, -0.20F, 0.20F, 0.20F, 0.20F, 0.48F, light);
    }

    private static void box(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x0, final float y0, final float z0,
        final float x1, final float y1, final float z1, final int light) {
        quad(pose, buffer, x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0, 0,0,-1,light);
        quad(pose, buffer, x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1, 0,0,1,light);
        quad(pose, buffer, x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, -1,0,0,light);
        quad(pose, buffer, x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, 1,0,0,light);
        quad(pose, buffer, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, 0,1,0,light);
        quad(pose, buffer, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, 0,-1,0,light);
    }

    private static void quad(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float ax, final float ay, final float az, final float bx, final float by,
        final float bz, final float cx, final float cy, final float cz, final float dx,
        final float dy, final float dz, final float nx, final float ny, final float nz,
        final int light) {
        vertex(pose,buffer,ax,ay,az,0,0,nx,ny,nz,light);
        vertex(pose,buffer,bx,by,bz,1,0,nx,ny,nz,light);
        vertex(pose,buffer,cx,cy,cz,1,1,nx,ny,nz,light);
        vertex(pose,buffer,dx,dy,dz,0,1,nx,ny,nz,light);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x, final float y, final float z, final float u, final float v,
        final float nx, final float ny, final float nz, final int light) {
        buffer.addVertex(pose, x, y, z).setColor(255,255,255,255).setUv(u,v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
            .setNormal(pose,nx,ny,nz);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}