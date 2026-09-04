package com.andye.warmod.phalanx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Block-built close-in weapon system with separate traverse, elevation, and barrel spin. */
public final class PhalanxTurretMesh {
    private PhalanxTurretMesh() { }

    public static void renderStaticBase(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light, final boolean enabled) {
        // Broad bolt-down plinth and stepped machinery column.
        box(pose, buffer, -.08F, .02F, -.08F, 1.08F, .16F, 1.08F,
            42, 48, 50, light);
        box(pose, buffer, .04F, .16F, .04F, .96F, .35F, .96F,
            57, 64, 66, light);
        box(pose, buffer, .13F, .34F, .13F, .87F, .72F, .87F,
            68, 75, 76, light);
        box(pose, buffer, .22F, .70F, .22F, .78F, 1.04F, .78F,
            79, 87, 88, light);
        // Maintenance panel, caution strip, and four anchor blocks.
        box(pose, buffer, .25F, .43F, .868F, .75F, .63F, .90F,
            32, 38, 40, light);
        box(pose, buffer, .30F, .47F, .902F, .67F, .52F, .924F,
            181, 130, 38, 15728880);
        box(pose, buffer, .70F, .55F, .903F, .76F, .61F, .925F,
            enabled ? 46 : 132, enabled ? 192 : 55, enabled ? 83 : 50, 15728880);
        box(pose, buffer, -.13F, .02F, -.13F, .10F, .12F, .10F,
            36, 42, 44, light);
        box(pose, buffer, .90F, .02F, -.13F, 1.13F, .12F, .10F,
            36, 42, 44, light);
        box(pose, buffer, -.13F, .02F, .90F, .10F, .12F, 1.13F,
            36, 42, 44, light);
        box(pose, buffer, .90F, .02F, .90F, 1.13F, .12F, 1.13F,
            36, 42, 44, light);
    }

    public static void renderYawHousing(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light) {
        // Armoured traverse body with left ammunition module and rear sensor mast.
        box(pose, buffer, -.58F, -.15F, -.43F, .58F, .31F, .46F,
            77, 85, 86, light);
        box(pose, buffer, -.69F, -.06F, -.31F, -.43F, .43F, .34F,
            55, 62, 64, light);
        box(pose, buffer, .43F, -.06F, -.31F, .69F, .43F, .34F,
            55, 62, 64, light);
        box(pose, buffer, -.48F, .27F, -.38F, .48F, .48F, .25F,
            88, 95, 95, light);
        // Blocky search/track array replaces a rounded naval radome.
        box(pose, buffer, -.35F, .23F, -.67F, .35F, .52F, -.39F,
            39, 47, 46, light);
        box(pose, buffer, -.29F, .29F, -.69F, .29F, .46F, -.675F,
            29, 38, 34, light);
        for (int column = 0; column < 4; column++) {
            float x = -.245F + column * .16F;
            box(pose, buffer, x, .325F, -.696F, x + .08F, .405F, -.675F,
                101, 116, 94, light);
        }
        // Ochre service stripe makes the front/back orientation obvious.
        box(pose, buffer, -.36F, -.10F, .462F, .36F, -.03F, .486F,
            184, 130, 38, 15728880);
    }

    public static void renderCradle(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light) {
        box(pose, buffer, -.33F, -.21F, -.25F, .33F, .21F, .43F,
            62, 69, 70, light);
        box(pose, buffer, -.24F, -.16F, .38F, .24F, .16F, .72F,
            43, 50, 52, light);
        box(pose, buffer, -.42F, -.30F, -.12F, -.30F, .30F, .28F,
            91, 98, 96, light);
        box(pose, buffer, .30F, -.30F, -.12F, .42F, .30F, .28F,
            91, 98, 96, light);
    }

    public static void renderBarrels(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light, final float spinDegrees) {
        double phase = Math.toRadians(spinDegrees);
        float radius = .115F;
        for (int index = 0; index < 6; index++) {
            double angle = phase + index * Math.PI / 3.0;
            float x = (float)Math.cos(angle) * radius;
            float y = (float)Math.sin(angle) * radius;
            box(pose, buffer, x - .025F, y - .025F, .60F,
                x + .025F, y + .025F, 1.72F,
                31, 37, 39, light);
        }
        // Central spindle and two retainers keep the six barrels from reading as loose rods.
        box(pose, buffer, -.055F, -.055F, .42F, .055F, .055F, 1.69F,
            48, 54, 55, light);
        box(pose, buffer, -.18F, -.18F, .69F, .18F, .18F, .79F,
            63, 69, 69, light);
        box(pose, buffer, -.18F, -.18F, 1.38F, .18F, .18F, 1.48F,
            63, 69, 69, light);
    }

    public static void renderMuzzleFlash(final PoseStack.Pose pose,
        final VertexConsumer buffer, final int light) {
        int fullBright = 0xF000F0;
        box(pose, buffer, -.13F, -.13F, 1.73F, .13F, .13F, 1.96F,
            255, 130, 28, fullBright);
        box(pose, buffer, -.065F, -.065F, 1.95F, .065F, .065F, 2.16F,
            255, 244, 178, fullBright);
    }

    private static void box(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x1, final float y1, final float z1,
        final float x2, final float y2, final float z2,
        final int red, final int green, final int blue, final int light) {
        int material = materialFor(red, green, blue);
        quad(pose,buffer,x1,y1,z1,x2,y1,z1,x2,y2,z1,x1,y2,z1,0,0,-1,material,red,green,blue,light);
        quad(pose,buffer,x2,y1,z2,x1,y1,z2,x1,y2,z2,x2,y2,z2,0,0,1,material,red,green,blue,light);
        quad(pose,buffer,x1,y1,z2,x1,y1,z1,x1,y2,z1,x1,y2,z2,-1,0,0,material,red,green,blue,light);
        quad(pose,buffer,x2,y1,z1,x2,y1,z2,x2,y2,z2,x2,y2,z1,1,0,0,material,red,green,blue,light);
        quad(pose,buffer,x1,y1,z2,x2,y1,z2,x2,y1,z1,x1,y1,z1,0,-1,0,material,red,green,blue,light);
        quad(pose,buffer,x1,y2,z1,x2,y2,z1,x2,y2,z2,x1,y2,z2,0,1,0,material,red,green,blue,light);
    }

    private static void quad(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float ax, final float ay, final float az,
        final float bx, final float by, final float bz,
        final float cx, final float cy, final float cz,
        final float dx, final float dy, final float dz,
        final float nx, final float ny, final float nz, final int material,
        final int red, final int green, final int blue, final int light) {
        float inset=.004F, tile=1F/3F;
        float u0=(material%3)*tile+inset, v0=(material/3)*tile+inset;
        float u1=(material%3+1)*tile-inset, v1=(material/3+1)*tile-inset;
        vertex(pose,buffer,ax,ay,az,u0,v1,nx,ny,nz,red,green,blue,light);
        vertex(pose,buffer,bx,by,bz,u1,v1,nx,ny,nz,red,green,blue,light);
        vertex(pose,buffer,cx,cy,cz,u1,v0,nx,ny,nz,red,green,blue,light);
        vertex(pose,buffer,dx,dy,dz,u0,v0,nx,ny,nz,red,green,blue,light);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x, final float y, final float z, final float u, final float v,
        final float nx, final float ny, final float nz,
        final int red, final int green, final int blue, final int light) {
        buffer.addVertex(pose,x,y,z)
            .setColor(red,green,blue,255)
            .setUv(u,v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose,nx,ny,nz);
    }

    private static int materialFor(final int red, final int green, final int blue) {
        if (red > 145 && green < 155) return 6; // warning paint
        if (green > red + 18) return 2; // olive paint / indicator
        if (red > 130 && green > 95 && blue < 75) return 5; // brass/ochre
        return red + green + blue > 205 ? 4 : 1; // brushed steel or gunmetal
    }
}
