package com.andye.warmod.icbm.client.render;

import com.andye.warmod.client.model.BlockbenchGameplayMeshes;
import com.andye.warmod.client.model.BlockbenchGameplayMeshes.Model;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadYield;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

public final class IcbmMissileMesh {
    private enum Section { FULL, STAGE, WARHEAD }

    private IcbmMissileMesh() { }

    /** Renders the exact saved Blockbench missile for this yield and delivery mode. */
    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final WarheadYield yield, final WarheadDeliveryMode deliveryMode,
        final IcbmLongRangeRenderContext.Lod detail, final int light) {
        boolean cluster = deliveryMode == WarheadDeliveryMode.CLUSTER_FOUR;
        Model model = switch (yield) {
            case HIGH_EXPLOSIVE -> cluster
                ? Model.HIGH_EXPLOSIVE_CLUSTER_MISSILE : Model.HIGH_EXPLOSIVE_MISSILE;
            case HIGH_CAPACITY_HE -> cluster
                ? Model.HIGH_CAPACITY_HE_CLUSTER_MISSILE : Model.HIGH_CAPACITY_HE_MISSILE;
            case CONVENTIONAL -> cluster
                ? Model.CONVENTIONAL_CLUSTER_MISSILE : Model.CONVENTIONAL_MISSILE;
            case HEAVY_CONVENTIONAL -> cluster
                ? Model.HEAVY_CONVENTIONAL_CLUSTER_MISSILE : Model.HEAVY_CONVENTIONAL_MISSILE;
            case TACTICAL_NUCLEAR -> cluster
                ? Model.TACTICAL_NUCLEAR_CLUSTER_MISSILE : Model.TACTICAL_NUCLEAR_MISSILE;
            case STRATEGIC_NUCLEAR -> cluster
                ? Model.STRATEGIC_NUCLEAR_CLUSTER_MISSILE : Model.STRATEGIC_NUCLEAR_MISSILE;
            case HEAVY_NUCLEAR -> cluster
                ? Model.HEAVY_NUCLEAR_CLUSTER_MISSILE : Model.HEAVY_NUCLEAR_MISSILE;
        };
        BlockbenchGameplayMeshes.render(pose, buffer, model,
            IcbmVisualGeometry.TOTAL_VISUAL_HEIGHT / sourceHeight(yield),
            0.0F, originY(yield, Section.FULL), 0.0F, light);
    }

    public static void renderStage(final PoseStack.Pose pose, final VertexConsumer buffer,
        final WarheadYield yield, final WarheadDeliveryMode deliveryMode,
        final int light, final int alpha) {
        renderSection(pose, buffer, yield, deliveryMode, Section.STAGE, light, alpha);
    }

    public static void renderWarhead(final PoseStack.Pose pose, final VertexConsumer buffer,
        final WarheadYield yield, final WarheadDeliveryMode deliveryMode,
        final int light) {
        renderSection(pose, buffer, yield, deliveryMode, Section.WARHEAD, light, 255);
    }

    /** Renders one physical quarter of a four-way cluster nose after separation. */
    public static void renderWarhead(final PoseStack.Pose pose, final VertexConsumer buffer,
        final WarheadYield yield, final WarheadDeliveryMode deliveryMode,
        final int clusterIndex, final int clusterCount, final int light) {
        if (deliveryMode != WarheadDeliveryMode.CLUSTER_FOUR || clusterCount != 4
                || clusterIndex < 0 || clusterIndex >= clusterCount) {
            renderWarhead(pose, buffer, yield, deliveryMode, light);
            return;
        }
        Model full = model(yield, deliveryMode);
        BlockbenchGameplayMeshes.renderQuarter(pose, buffer, full,
            IcbmVisualGeometry.TOTAL_VISUAL_HEIGHT / sourceHeight(yield),
            0.0F, originY(yield, Section.WARHEAD), 0.0F, light, 255,
            separationY(yield), Float.POSITIVE_INFINITY, clusterIndex);
    }

    private static void renderSection(final PoseStack.Pose pose, final VertexConsumer buffer,
        final WarheadYield yield, final WarheadDeliveryMode deliveryMode,
        final Section section, final int light, final int alpha) {
        Model full = model(yield, deliveryMode);
        float separationY = separationY(yield);
        float minimumY = section == Section.WARHEAD
            ? separationY : Float.NEGATIVE_INFINITY;
        float maximumY = section == Section.STAGE
            ? separationY : Float.POSITIVE_INFINITY;
        BlockbenchGameplayMeshes.render(pose, buffer, full,
            IcbmVisualGeometry.TOTAL_VISUAL_HEIGHT / sourceHeight(yield),
            0.0F, originY(yield, section), 0.0F, light, alpha,
            minimumY, maximumY);
    }

    private static Model model(final WarheadYield yield,
        final WarheadDeliveryMode deliveryMode) {
        boolean cluster = deliveryMode == WarheadDeliveryMode.CLUSTER_FOUR;
        return switch (yield) {
            case HIGH_EXPLOSIVE -> cluster
                ? Model.HIGH_EXPLOSIVE_CLUSTER_MISSILE : Model.HIGH_EXPLOSIVE_MISSILE;
            case HIGH_CAPACITY_HE -> cluster
                ? Model.HIGH_CAPACITY_HE_CLUSTER_MISSILE : Model.HIGH_CAPACITY_HE_MISSILE;
            case CONVENTIONAL -> cluster
                ? Model.CONVENTIONAL_CLUSTER_MISSILE : Model.CONVENTIONAL_MISSILE;
            case HEAVY_CONVENTIONAL -> cluster
                ? Model.HEAVY_CONVENTIONAL_CLUSTER_MISSILE : Model.HEAVY_CONVENTIONAL_MISSILE;
            case TACTICAL_NUCLEAR -> cluster
                ? Model.TACTICAL_NUCLEAR_CLUSTER_MISSILE : Model.TACTICAL_NUCLEAR_MISSILE;
            case STRATEGIC_NUCLEAR -> cluster
                ? Model.STRATEGIC_NUCLEAR_CLUSTER_MISSILE : Model.STRATEGIC_NUCLEAR_MISSILE;
            case HEAVY_NUCLEAR -> cluster
                ? Model.HEAVY_NUCLEAR_CLUSTER_MISSILE : Model.HEAVY_NUCLEAR_MISSILE;
        };
    }

    private static float separationY(final WarheadYield yield) {
        return switch (yield) {
            case HIGH_EXPLOSIVE -> 8.0F;
            case HIGH_CAPACITY_HE -> 8.5F;
            case CONVENTIONAL -> 9.0F;
            case HEAVY_CONVENTIONAL -> 9.5F;
            case TACTICAL_NUCLEAR -> 10.0F;
            case STRATEGIC_NUCLEAR -> 10.5F;
            case HEAVY_NUCLEAR -> 11.0F;
        };
    }

    private static float sourceHeight(final WarheadYield yield) {
        return switch (yield) {
            case HIGH_EXPLOSIVE -> 34.15F;
            case HIGH_CAPACITY_HE -> 35.65F;
            case CONVENTIONAL -> 37.15F;
            case HEAVY_CONVENTIONAL, TACTICAL_NUCLEAR -> 38.65F;
            case STRATEGIC_NUCLEAR -> 40.65F;
            case HEAVY_NUCLEAR -> 42.15F;
        };
    }

    private static float originY(final WarheadYield yield, final Section section) {
        if (section == Section.STAGE) return switch (yield) {
            case HIGH_EXPLOSIVE -> -3.575F;
            case HIGH_CAPACITY_HE -> -4.425F;
            case CONVENTIONAL -> -4.675F;
            case HEAVY_CONVENTIONAL, STRATEGIC_NUCLEAR -> -4.925F;
            case TACTICAL_NUCLEAR -> -3.925F;
            case HEAVY_NUCLEAR -> -3.45F;
        };
        if (section == Section.WARHEAD) return switch (yield) {
            case HIGH_EXPLOSIVE -> 12.9F;
            case HIGH_CAPACITY_HE -> 13.4F;
            case CONVENTIONAL -> 13.9F;
            case HEAVY_CONVENTIONAL -> 14.4F;
            case TACTICAL_NUCLEAR -> 14.9F;
            case STRATEGIC_NUCLEAR -> 15.4F;
            case HEAVY_NUCLEAR -> 15.9F;
        };
        return switch (yield) {
            case HIGH_EXPLOSIVE -> 0.725F;
            case HIGH_CAPACITY_HE, TACTICAL_NUCLEAR -> 0.475F;
            case CONVENTIONAL -> 0.225F;
            case HEAVY_CONVENTIONAL, STRATEGIC_NUCLEAR -> -0.025F;
            case HEAVY_NUCLEAR -> -0.275F;
        };
    }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final IcbmPayloadAppearance appearance, final IcbmLongRangeRenderContext.Lod detail,
        final int light) {
        render(pose, buffer, appearance, detail, light, 255);
    }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final IcbmPayloadAppearance appearance, final IcbmLongRangeRenderContext.Lod detail,
        final int light, final int alpha) {
        int sides = detail == IcbmLongRangeRenderContext.Lod.EXTREME ? 6 : 8;
        float radius = IcbmVisualGeometry.BODY_RADIUS;
        float bottom = IcbmVisualGeometry.BODY_BOTTOM;
        float top = IcbmVisualGeometry.BODY_TOP;
        for (int index = 0; index < sides; index++) {
            float angle = Mth.TWO_PI * index / sides;
            float nextAngle = Mth.TWO_PI * (index + 1) / sides;
            float x = radius * Mth.cos(angle);
            float z = radius * Mth.sin(angle);
            float nextX = radius * Mth.cos(nextAngle);
            float nextZ = radius * Mth.sin(nextAngle);
            quad(pose, buffer, x, bottom, z, x, top, z, nextX, top, nextZ,
                nextX, bottom, nextZ, appearance.bodyRed(), appearance.bodyGreen(),
                appearance.bodyBlue(), alpha, light,
                Mth.cos(angle), 0, Mth.sin(angle));
            triangle(pose, buffer, x, top, z, 0, IcbmVisualGeometry.NOSE_TIP, 0,
                nextX, top, nextZ, Math.min(255, appearance.bodyRed() + 18),
                Math.min(255, appearance.bodyGreen() + 18),
                Math.min(255, appearance.bodyBlue() + 16), alpha, light);
        }
        renderBand(pose, buffer, appearance, sides, light, alpha);
        if (detail == IcbmLongRangeRenderContext.Lod.EXTREME) return;
        if (appearance.cluster()) renderClusterCanisters(pose, buffer, appearance,
            light, alpha);
        renderNozzle(pose, buffer, sides, light, alpha);
        renderFins(pose, buffer, light, alpha);
    }

    private static void renderBand(final PoseStack.Pose pose, final VertexConsumer buffer,
        final IcbmPayloadAppearance appearance, final int sides, final int light, final int alpha) {
        float radius = IcbmVisualGeometry.BODY_RADIUS + 0.006F;
        int stripes = Math.max(1, appearance.stripeCount());
        for (int stripe = 0; stripe < stripes; stripe++) {
            float center = IcbmVisualGeometry.PAYLOAD_BAND_POSITION
                + (stripe - (stripes - 1) * 0.5F) * 0.18F;
            float halfHeight = appearance.cluster() && stripe == stripes - 1 ? 0.075F : 0.052F;
            for (int index = 0; index < sides; index++) {
                float angle = Mth.TWO_PI * index / sides;
                float next = Mth.TWO_PI * (index + 1) / sides;
                float x = radius * Mth.cos(angle);
                float z = radius * Mth.sin(angle);
                float nx = radius * Mth.cos(next);
                float nz = radius * Mth.sin(next);
                quad(pose, buffer, x, center - halfHeight, z, x, center + halfHeight, z,
                    nx, center + halfHeight, nz, nx, center - halfHeight, nz,
                    appearance.red(), appearance.green(), appearance.blue(), alpha, light,
                    Mth.cos(angle), 0, Mth.sin(angle));
            }
        }
    }

    private static void renderClusterCanisters(final PoseStack.Pose pose,
        final VertexConsumer buffer, final IcbmPayloadAppearance appearance,
        final int light, final int alpha) {
        float radius = IcbmVisualGeometry.BODY_RADIUS;
        float bottom = 0.20F, top = 1.08F, half = 0.105F;
        for (int index = 0; index < 4; index++) {
            float angle = Mth.TWO_PI * index / 4.0F;
            float cx = Mth.cos(angle) * (radius + half * 0.42F);
            float cz = Mth.sin(angle) * (radius + half * 0.42F);
            box(pose, buffer, cx - half, bottom, cz - half,
                cx + half, top, cz + half, appearance.red(), appearance.green(),
                appearance.blue(), alpha, light);
        }
    }

    private static void renderNozzle(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int sides, final int light, final int alpha) {
        float topRadius = IcbmVisualGeometry.BODY_RADIUS;
        float bottomRadius = IcbmVisualGeometry.BODY_RADIUS * 0.52F;
        float top = IcbmVisualGeometry.BODY_BOTTOM;
        float bottom = -IcbmVisualGeometry.TOTAL_VISUAL_HEIGHT * 0.5F;
        for (int index = 0; index < sides; index++) {
            float angle = Mth.TWO_PI * index / sides;
            float next = Mth.TWO_PI * (index + 1) / sides;
            quad(pose, buffer,
                topRadius * Mth.cos(angle), top, topRadius * Mth.sin(angle),
                bottomRadius * Mth.cos(angle), bottom, bottomRadius * Mth.sin(angle),
                bottomRadius * Mth.cos(next), bottom, bottomRadius * Mth.sin(next),
                topRadius * Mth.cos(next), top, topRadius * Mth.sin(next),
                30, 34, 38, alpha, light, Mth.cos(angle), 0, Mth.sin(angle));
        }
    }

    private static void renderFins(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int light, final int alpha) {
        float root = IcbmVisualGeometry.FIN_ROOT_POSITION;
        float radius = IcbmVisualGeometry.BODY_RADIUS;
        float embeddedRootRadius = radius - .025F;
        for (int fin = 0; fin < 4; fin++) {
            float angle = Mth.TWO_PI * fin / 4;
            float x = Mth.cos(angle);
            float z = Mth.sin(angle);
            quad(pose, buffer,
                x * embeddedRootRadius, root - 0.18F, z * embeddedRootRadius,
                x * (radius + IcbmVisualGeometry.FIN_SPAN), root - 0.14F,
                z * (radius + IcbmVisualGeometry.FIN_SPAN),
                x * (radius + IcbmVisualGeometry.FIN_SPAN * 0.72F), root + 0.48F,
                z * (radius + IcbmVisualGeometry.FIN_SPAN * 0.72F),
                x * embeddedRootRadius, root + 0.35F, z * embeddedRootRadius,
                39, 44, 49, alpha, light, x, 0, z);
        }
    }

    private static void triangle(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float ax, final float ay, final float az, final float bx, final float by, final float bz,
        final float cx, final float cy, final float cz, final int red, final int green, final int blue,
        final int alpha, final int light) {
        vertex(pose, buffer, ax, ay, az, 0, 1, red, green, blue, alpha, light, 0, 1, 0);
        vertex(pose, buffer, bx, by, bz, 0.5F, 0, red, green, blue, alpha, light, 0, 1, 0);
        vertex(pose, buffer, cx, cy, cz, 1, 1, red, green, blue, alpha, light, 0, 1, 0);
    }

    private static void box(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x1, final float y1, final float z1,
        final float x2, final float y2, final float z2,
        final int red, final int green, final int blue, final int alpha, final int light) {
        quad(pose, buffer, x1,y1,z1, x1,y2,z1, x2,y2,z1, x2,y1,z1,
            red,green,blue,alpha,light, 0,0,-1);
        quad(pose, buffer, x2,y1,z2, x2,y2,z2, x1,y2,z2, x1,y1,z2,
            red,green,blue,alpha,light, 0,0,1);
        quad(pose, buffer, x1,y1,z2, x1,y2,z2, x1,y2,z1, x1,y1,z1,
            red,green,blue,alpha,light, -1,0,0);
        quad(pose, buffer, x2,y1,z1, x2,y2,z1, x2,y2,z2, x2,y1,z2,
            red,green,blue,alpha,light, 1,0,0);
        quad(pose, buffer, x1,y2,z1, x1,y2,z2, x2,y2,z2, x2,y2,z1,
            red,green,blue,alpha,light, 0,1,0);
        quad(pose, buffer, x1,y1,z2, x1,y1,z1, x2,y1,z1, x2,y1,z2,
            red,green,blue,alpha,light, 0,-1,0);
    }

    private static void quad(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x1, final float y1, final float z1, final float x2, final float y2, final float z2,
        final float x3, final float y3, final float z3, final float x4, final float y4, final float z4,
        final int red, final int green, final int blue, final int alpha, final int light,
        final float normalX, final float normalY, final float normalZ) {
        vertex(pose, buffer, x1, y1, z1, 0, 1, red, green, blue, alpha, light, normalX, normalY, normalZ);
        vertex(pose, buffer, x2, y2, z2, 0, 0, red, green, blue, alpha, light, normalX, normalY, normalZ);
        vertex(pose, buffer, x3, y3, z3, 1, 0, red, green, blue, alpha, light, normalX, normalY, normalZ);
        vertex(pose, buffer, x4, y4, z4, 1, 1, red, green, blue, alpha, light, normalX, normalY, normalZ);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x, final float y, final float z, final float u, final float v,
        final int red, final int green, final int blue, final int alpha, final int light,
        final float normalX, final float normalY, final float normalZ) {
        buffer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
            .setNormal(pose, normalX, normalY, normalZ);
    }
}
