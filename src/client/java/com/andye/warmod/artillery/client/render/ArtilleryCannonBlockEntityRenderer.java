package com.andye.warmod.artillery.client.render;

import com.andye.warmod.WarMod;
import com.andye.warmod.artillery.ArtilleryConstants;
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

/**
 * Three-block-wide visual carriage with a target-driven traversing and elevating gun.
 * The shared physical muzzle constants keep the shell spawn at the end of the visible tube.
 */
public final class ArtilleryCannonBlockEntityRenderer
    implements BlockEntityRenderer<ArtilleryCannonBlockEntity, ArtilleryCannonRenderState> {
    private static final RenderType MATERIAL = RenderType.create("war_mod_artillery_material",
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
        float placedYaw = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> -90.0F;
            default -> 0.0F;
        };
        state.baseYawDegrees = placedYaw;
        state.yawDegrees = placedYaw;
        state.elevationDegrees = 12.0F;
        state.hasTarget = cannon.target() != null;
        if (cannon.target() == null) return;

        Vec3 origin = Vec3.atCenterOf(cannon.getBlockPos()).add(0.0,
            ArtilleryConstants.BARREL_PIVOT_HEIGHT, 0.0);
        ArtilleryTrajectory.solveFromCannon(origin, cannon.target().position()).ifPresent(launch -> {
            Vec3 velocity = launch.velocity();
            // The hand-built barrel points along local -Z; positive Axis.Y rotation turns it west.
            state.yawDegrees = (float)-Math.toDegrees(Math.atan2(velocity.x, -velocity.z));
            state.elevationDegrees = (float)Math.toDegrees(
                Math.atan2(velocity.y, velocity.horizontalDistance()));
        });
    }

    @Override
    public void submit(final ArtilleryCannonRenderState state, final PoseStack poses,
        final SubmitNodeCollector collector, final CameraRenderState camera) {
        // Fixed 2.8 x 2.7 field carriage follows the placement facing.
        poses.pushPose();
        poses.translate(0.5, 0.0, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(state.baseYawDegrees));
        collector.submitCustomGeometry(poses, MATERIAL,
            (pose, buffer) -> renderCarriage(pose, buffer, state.lightCoords));
        poses.popPose();

        // The upper mount traverses without tilting the pedestal or stabilisers.
        poses.pushPose();
        poses.translate(0.5, ArtilleryConstants.BARREL_PIVOT_HEIGHT, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(state.yawDegrees));
        collector.submitCustomGeometry(poses, MATERIAL,
            (pose, buffer) -> renderTraverseAssembly(pose, buffer, state.lightCoords));

        poses.mulPose(Axis.XP.rotationDegrees(state.elevationDegrees));
        collector.submitCustomGeometry(poses, MATERIAL,
            (pose, buffer) -> renderElevatingAssembly(pose, buffer, state.lightCoords));
        poses.popPose();
    }

    private static void renderCarriage(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light) {
        // Wide tracked bed, stepped deck and four stabilisers form a readable 3x3 silhouette.
        box(pose, buffer, -1.38F, .04F, -1.26F, -1.02F, .42F, 1.26F, light);
        box(pose, buffer, 1.02F, .04F, -1.26F, 1.38F, .42F, 1.26F, light);
        box(pose, buffer, -1.04F, .14F, -1.10F, 1.04F, .38F, 1.10F, light);
        box(pose, buffer, -.84F, .36F, -.78F, .84F, .58F, .78F, light);
        box(pose, buffer, -.62F, .56F, -.60F, .62F, .88F, .60F, light);

        // Track shoes and hubs interrupt the long cuboids with block-scale detail.
        for (int side : new int[] { -1, 1 }) {
            float x1 = side < 0 ? -1.43F : 1.30F;
            float x2 = side < 0 ? -1.30F : 1.43F;
            for (int index = 0; index < 5; index++) {
                float z = -1.08F + index * .53F;
                box(pose, buffer, x1, .10F, z, x2, .34F, z + .30F, light);
            }
        }
        // Deployable-looking outriggers visually anchor the overhang.
        box(pose, buffer, -1.62F, .12F, -.92F, -1.28F, .25F, -.72F, light);
        box(pose, buffer, 1.28F, .12F, -.92F, 1.62F, .25F, -.72F, light);
        box(pose, buffer, -1.62F, .12F, .72F, -1.28F, .25F, .92F, light);
        box(pose, buffer, 1.28F, .12F, .72F, 1.62F, .25F, .92F, light);
        box(pose, buffer, -1.70F, .02F, -.99F, -1.52F, .16F, -.65F, light);
        box(pose, buffer, 1.52F, .02F, -.99F, 1.70F, .16F, -.65F, light);
        box(pose, buffer, -1.70F, .02F, .65F, -1.52F, .16F, .99F, light);
        box(pose, buffer, 1.52F, .02F, .65F, 1.70F, .16F, .99F, light);
    }

    private static void renderTraverseAssembly(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light) {
        // Armoured turntable and split trunnion yoke.
        box(pose, buffer, -.72F, -.43F, -.68F, .72F, -.20F, .68F, light);
        box(pose, buffer, -.58F, -.23F, -.54F, .58F, .18F, .58F, light);
        box(pose, buffer, -.78F, -.10F, -.26F, -.43F, .43F, .42F, light);
        box(pose, buffer, .43F, -.10F, -.26F, .78F, .43F, .42F, light);
        box(pose, buffer, -.88F, -.42F, .34F, -.62F, .10F, .86F, light);
        box(pose, buffer, .62F, -.42F, .34F, .88F, .10F, .86F, light);
    }

    private static void renderElevatingAssembly(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light) {
        // Heavy breech, square recoil cylinders, gun tube and a slotted muzzle brake.
        box(pose, buffer, -.40F, -.38F, -.05F, .40F, .38F, .92F, light);
        box(pose, buffer, -.28F, -.28F, -.92F, .28F, .28F, .18F, light);
        box(pose, buffer, -.19F, -.19F, -3.25F, .19F, .19F, -.86F, light);
        box(pose, buffer, -.34F, .18F, -1.72F, -.08F, .32F, -.28F, light);
        box(pose, buffer, .08F, .18F, -1.72F, .34F, .32F, -.28F, light);
        box(pose, buffer, -.31F, -.31F, -3.28F, .31F, .31F, -3.06F, light);
        box(pose, buffer, -.42F, -.12F, -3.23F, .42F, .12F, -2.96F, light);
        // Blocky mantlet and sighting box add mass without hiding the bore.
        box(pose, buffer, -.72F, -.56F, -.17F, .72F, .56F, .04F, light);
        box(pose, buffer, .46F, .28F, -.30F, .72F, .55F, .26F, light);
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
        final float ax, final float ay, final float az,
        final float bx, final float by, final float bz,
        final float cx, final float cy, final float cz,
        final float dx, final float dy, final float dz,
        final float nx, final float ny, final float nz, final int light) {
        vertex(pose,buffer,ax,ay,az,0,0,nx,ny,nz,light);
        vertex(pose,buffer,bx,by,bz,1,0,nx,ny,nz,light);
        vertex(pose,buffer,cx,cy,cz,1,1,nx,ny,nz,light);
        vertex(pose,buffer,dx,dy,dz,0,1,nx,ny,nz,light);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x, final float y, final float z, final float u, final float v,
        final float nx, final float ny, final float nz, final int light) {
        buffer.addVertex(pose, x, y, z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
