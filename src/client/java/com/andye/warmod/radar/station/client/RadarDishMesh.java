package com.andye.warmod.radar.station.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Block-built rotating phased-array head for the radar station.
 *
 * <p>The former circular reflector read as a smooth satellite dish and made its
 * fixed tilt look like a second rotation axis. This deliberately uses cuboids,
 * exposed ribs, and a tiled rectangular aperture so it remains unmistakably a
 * Minecraft field radar while sweeping only around the vertical mast.</p>
 */
public final class RadarDishMesh {
    private static final int FRAME_R = 55;
    private static final int FRAME_G = 63;
    private static final int FRAME_B = 66;
    private static final int PANEL_R = 35;
    private static final int PANEL_G = 43;
    private static final int PANEL_B = 40;

    private RadarDishMesh() { }

    public static void renderMount(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int light, final boolean warning) {
        // Turntable, gearbox and a broad U-shaped yoke seated on the mast.
        box(pose, buffer, -.38F, -.10F, -.38F, .38F, .05F, .38F,
            38, 44, 47, light);
        box(pose, buffer, -.29F, .03F, -.27F, .29F, .20F, .27F,
            66, 73, 73, light);
        box(pose, buffer, -.42F, .16F, -.24F, -.25F, .68F, .24F,
            FRAME_R, FRAME_G, FRAME_B, light);
        box(pose, buffer, .25F, .16F, -.24F, .42F, .68F, .24F,
            FRAME_R, FRAME_G, FRAME_B, light);
        box(pose, buffer, -.34F, .55F, -.20F, .34F, .72F, .20F,
            71, 79, 80, light);
        // Visible bearing caps reinforce that this is a vertical turntable.
        box(pose, buffer, -.48F, .38F, -.12F, -.40F, .56F, .12F,
            173, 126, 38, 15728880);
        box(pose, buffer, .40F, .38F, -.12F, .48F, .56F, .12F,
            warning ? 218 : 173, warning ? 54 : 126, warning ? 38 : 38, 15728880);
    }

    // Local array forward is +Z, up is +Y and right is +X.
    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int light, final boolean warning) {
        renderArrayFrame(pose, buffer, light);
        renderApertureTiles(pose, buffer, light, warning);
        renderRearStructure(pose, buffer, light, warning);
    }

    private static void renderArrayFrame(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light) {
        // A 2.6 x 1.45 block slab with a stepped military enclosure.
        box(pose, buffer, -1.30F, -.72F, -.12F, 1.30F, .72F, .12F,
            45, 52, 53, light);
        box(pose, buffer, -1.36F, -.79F, -.16F, 1.36F, -.64F, .16F,
            FRAME_R, FRAME_G, FRAME_B, light);
        box(pose, buffer, -1.36F, .64F, -.16F, 1.36F, .79F, .16F,
            FRAME_R, FRAME_G, FRAME_B, light);
        box(pose, buffer, -1.36F, -.64F, -.16F, -1.20F, .64F, .16F,
            FRAME_R, FRAME_G, FRAME_B, light);
        box(pose, buffer, 1.20F, -.64F, -.16F, 1.36F, .64F, .16F,
            FRAME_R, FRAME_G, FRAME_B, light);
        // Warm identification band gives the structure a readable front at range.
        box(pose, buffer, -1.18F, -.62F, .121F, 1.18F, -.54F, .145F,
            181, 132, 43, 15728880);
    }

    private static void renderApertureTiles(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light, final boolean warning) {
        final int columns = 8;
        final int rows = 4;
        final float left = -1.16F;
        final float bottom = -.49F;
        final float tileWidth = .275F;
        final float tileHeight = .255F;
        final float gap = .014F;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float x1 = left + column * (tileWidth + gap);
                float y1 = bottom + row * (tileHeight + gap);
                int lift = ((column + row) & 1) == 0 ? 4 : 0;
                box(pose, buffer, x1, y1, .126F,
                    x1 + tileWidth, y1 + tileHeight, .154F,
                    PANEL_R + lift, PANEL_G + lift, PANEL_B + lift, light);
                // Small receiver elements keep the face legible without curves.
                float cx = x1 + tileWidth * .5F;
                float cy = y1 + tileHeight * .5F;
                box(pose, buffer, cx - .025F, cy - .025F, .155F,
                    cx + .025F, cy + .025F, .174F,
                    111, 126, 104, light);
            }
        }
        box(pose, buffer, 1.06F, .50F, .155F, 1.16F, .59F, .19F,
            warning ? 228 : 57, warning ? 49 : 183, warning ? 32 : 81, 15728880);
    }

    private static void renderRearStructure(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light, final boolean warning) {
        // Rear electronics spine and four square braces; no rounded reflector shell.
        box(pose, buffer, -.60F, -.33F, -.37F, .60F, .33F, -.12F,
            37, 43, 46, light);
        box(pose, buffer, -.24F, -.47F, -.55F, .24F, .47F, -.35F,
            52, 60, 62, light);
        box(pose, buffer, -.96F, -.57F, -.25F, -.82F, .57F, -.12F,
            72, 79, 78, light);
        box(pose, buffer, .82F, -.57F, -.25F, .96F, .57F, -.12F,
            72, 79, 78, light);
        box(pose, buffer, -.82F, -.10F, -.29F, .82F, .10F, -.12F,
            67, 74, 74, light);
        box(pose, buffer, -.10F, -.57F, -.29F, .10F, .57F, -.12F,
            67, 74, 74, light);
        box(pose, buffer, -.11F, -.11F, -.68F, .11F, .11F, -.52F,
            warning ? 200 : 144, warning ? 51 : 105, 36, light);
    }

    private static void box(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x1, final float y1, final float z1,
        final float x2, final float y2, final float z2,
        final int red, final int green, final int blue, final int light) {
        quad(pose, buffer, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1,
            red, green, blue, light);
        quad(pose, buffer, x2, y1, z2, x1, y1, z2, x1, y2, z2, x2, y2, z2,
            red, green, blue, light);
        quad(pose, buffer, x1, y1, z2, x1, y1, z1, x1, y2, z1, x1, y2, z2,
            red, green, blue, light);
        quad(pose, buffer, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1,
            red, green, blue, light);
        quad(pose, buffer, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2,
            red, green, blue, light);
        quad(pose, buffer, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1,
            red, green, blue, light);
    }

    private static void quad(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float ax, final float ay, final float az,
        final float bx, final float by, final float bz,
        final float cx, final float cy, final float cz,
        final float dx, final float dy, final float dz,
        final int red, final int green, final int blue, final int light) {
        vertex(pose, buffer, ax, ay, az, red, green, blue, light);
        vertex(pose, buffer, bx, by, bz, red, green, blue, light);
        vertex(pose, buffer, cx, cy, cz, red, green, blue, light);
        vertex(pose, buffer, dx, dy, dz, red, green, blue, light);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x, final float y, final float z,
        final int red, final int green, final int blue, final int light) {
        buffer.addVertex(pose, x, y, z)
            .setColor(red, green, blue, 255)
            .setUv(0, 0)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0, 1, 0);
    }
}
