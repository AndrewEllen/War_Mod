package com.andye.warmod.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Generated from tools/visuals/blockbench/gameplay_catalog. Do not hand-edit. */
public final class BlockbenchGameplayMeshes {
    public enum Model { PISTOL_BULLET, RIFLE_BULLET, SNIPER_BULLET, FALLING_WARHEAD, ARTILLERY_SHELL, HE_ROCKET, ANTI_AIR_MISSILE_MK1, ANTI_AIR_MISSILE_MK2, ARTILLERY_FIXED, ARTILLERY_YAW, ARTILLERY_PITCH, RADAR_YAW, RADAR_PITCH }
    private BlockbenchGameplayMeshes() { }

    private static final Cube[] PISTOL_BULLET = {
        new Cube(-0.24F, -1.65F, -0.24F, 0.24F, 0.264F, 0.24F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.42F, -1.65F, -0.24F, 0.42F, 0.264F, 0.24F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.24F, -1.65F, -0.42F, 0.24F, 0.264F, 0.42F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, -1.65F, -0.36F, 0.36F, 0.264F, 0.36F, -0.3F, 0F, -0.3F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, -1.65F, -0.36F, 0.36F, 0.264F, 0.36F, 0.3F, 0F, -0.3F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, -1.65F, -0.36F, 0.36F, 0.264F, 0.36F, -0.3F, 0F, 0.3F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, -1.65F, -0.36F, 0.36F, 0.264F, 0.36F, 0.3F, 0F, 0.3F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.2112F, 0.264F, -0.2112F, 0.2112F, 0.858F, 0.2112F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.3612F, 0.264F, -0.2112F, 0.3612F, 0.858F, 0.2112F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.2112F, 0.264F, -0.3612F, 0.2112F, 0.858F, 0.3612F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, 0.264F, -0.36F, 0.36F, 0.858F, 0.36F, -0.2612F, 0F, -0.2612F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, 0.264F, -0.36F, 0.36F, 0.858F, 0.36F, 0.2612F, 0F, -0.2612F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, 0.264F, -0.36F, 0.36F, 0.858F, 0.36F, -0.2612F, 0F, 0.2612F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, 0.264F, -0.36F, 0.36F, 0.858F, 0.36F, 0.2612F, 0F, 0.2612F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.132F, 0.858F, -0.132F, 0.132F, 1.287F, 0.132F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.252F, 0.858F, -0.132F, 0.252F, 1.287F, 0.132F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.132F, 0.858F, -0.252F, 0.132F, 1.287F, 0.252F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.36F, 0.858F, -0.36F, 0.36F, 1.287F, 0.36F, -0.172F, 0F, -0.172F, 0F, 45F, 0F, 166, 138, 91, false),
        new Cube(-0.36F, 0.858F, -0.36F, 0.36F, 1.287F, 0.36F, 0.172F, 0F, -0.172F, 0F, 45F, 0F, 166, 138, 91, false),
        new Cube(-0.36F, 0.858F, -0.36F, 0.36F, 1.287F, 0.36F, -0.172F, 0F, 0.172F, 0F, 45F, 0F, 166, 138, 91, false),
        new Cube(-0.36F, 0.858F, -0.36F, 0.36F, 1.287F, 0.36F, 0.172F, 0F, 0.172F, 0F, 45F, 0F, 166, 138, 91, false),
        new Cube(-0.0924F, 1.287F, -0.0924F, 0.0924F, 1.65F, 0.0924F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.441F, -1.023F, -0.441F, 0.441F, -0.792F, 0.441F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false)
    };

    private static final Cube[] RIFLE_BULLET = {
        new Cube(-0.3F, -2.3F, -0.3F, 0.3F, 0.368F, 0.3F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.48F, -2.3F, -0.3F, 0.48F, 0.368F, 0.3F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.3F, -2.3F, -0.48F, 0.3F, 0.368F, 0.48F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, -2.3F, -0.36F, 0.36F, 0.368F, 0.36F, -0.36F, 0F, -0.36F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, -2.3F, -0.36F, 0.36F, 0.368F, 0.36F, 0.36F, 0F, -0.36F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, -2.3F, -0.36F, 0.36F, 0.368F, 0.36F, -0.36F, 0F, 0.36F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, -2.3F, -0.36F, 0.36F, 0.368F, 0.36F, 0.36F, 0F, 0.36F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.2628F, 0.368F, -0.2628F, 0.2628F, 1.196F, 0.2628F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.4128F, 0.368F, -0.2628F, 0.4128F, 1.196F, 0.2628F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.2628F, 0.368F, -0.4128F, 0.2628F, 1.196F, 0.4128F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, 0.368F, -0.36F, 0.36F, 1.196F, 0.36F, -0.3128F, 0F, -0.3128F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, 0.368F, -0.36F, 0.36F, 1.196F, 0.36F, 0.3128F, 0F, -0.3128F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, 0.368F, -0.36F, 0.36F, 1.196F, 0.36F, -0.3128F, 0F, 0.3128F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, 0.368F, -0.36F, 0.36F, 1.196F, 0.36F, 0.3128F, 0F, 0.3128F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.168F, 1.196F, -0.168F, 0.168F, 1.794F, 0.168F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.288F, 1.196F, -0.168F, 0.288F, 1.794F, 0.168F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.168F, 1.196F, -0.288F, 0.168F, 1.794F, 0.288F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.36F, 1.196F, -0.36F, 0.36F, 1.794F, 0.36F, -0.208F, 0F, -0.208F, 0F, 45F, 0F, 166, 138, 91, false),
        new Cube(-0.36F, 1.196F, -0.36F, 0.36F, 1.794F, 0.36F, 0.208F, 0F, -0.208F, 0F, 45F, 0F, 166, 138, 91, false),
        new Cube(-0.36F, 1.196F, -0.36F, 0.36F, 1.794F, 0.36F, -0.208F, 0F, 0.208F, 0F, 45F, 0F, 166, 138, 91, false),
        new Cube(-0.36F, 1.196F, -0.36F, 0.36F, 1.794F, 0.36F, 0.208F, 0F, 0.208F, 0F, 45F, 0F, 166, 138, 91, false),
        new Cube(-0.1056F, 1.794F, -0.1056F, 0.1056F, 2.3F, 0.1056F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.504F, -1.426F, -0.504F, 0.504F, -1.104F, 0.504F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.5088F, -2.07F, -0.5088F, 0.5088F, -1.748F, 0.5088F, 0F, 0F, 0F, 0F, 0F, 0F, 110, 139, 75, false)
    };

    private static final Cube[] SNIPER_BULLET = {
        new Cube(-0.4F, -2.9F, -0.4F, 0.4F, 0.464F, 0.4F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.58F, -2.9F, -0.4F, 0.58F, 0.464F, 0.4F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.4F, -2.9F, -0.58F, 0.4F, 0.464F, 0.58F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, -2.9F, -0.36F, 0.36F, 0.464F, 0.36F, -0.46F, 0F, -0.46F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, -2.9F, -0.36F, 0.36F, 0.464F, 0.36F, 0.46F, 0F, -0.46F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, -2.9F, -0.36F, 0.36F, 0.464F, 0.36F, -0.46F, 0F, 0.46F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, -2.9F, -0.36F, 0.36F, 0.464F, 0.36F, 0.46F, 0F, 0.46F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.3488F, 0.464F, -0.3488F, 0.3488F, 1.508F, 0.3488F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.4988F, 0.464F, -0.3488F, 0.4988F, 1.508F, 0.3488F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.3488F, 0.464F, -0.4988F, 0.3488F, 1.508F, 0.4988F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, 0.464F, -0.36F, 0.36F, 1.508F, 0.36F, -0.3988F, 0F, -0.3988F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, 0.464F, -0.36F, 0.36F, 1.508F, 0.36F, 0.3988F, 0F, -0.3988F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, 0.464F, -0.36F, 0.36F, 1.508F, 0.36F, -0.3988F, 0F, 0.3988F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.36F, 0.464F, -0.36F, 0.36F, 1.508F, 0.36F, 0.3988F, 0F, 0.3988F, 0F, 45F, 0F, 195, 164, 90, false),
        new Cube(-0.228F, 1.508F, -0.228F, 0.228F, 2.262F, 0.228F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.348F, 1.508F, -0.228F, 0.348F, 2.262F, 0.228F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.228F, 1.508F, -0.348F, 0.228F, 2.262F, 0.348F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.36F, 1.508F, -0.36F, 0.36F, 2.262F, 0.36F, -0.268F, 0F, -0.268F, 0F, 45F, 0F, 166, 138, 91, false),
        new Cube(-0.36F, 1.508F, -0.36F, 0.36F, 2.262F, 0.36F, 0.268F, 0F, -0.268F, 0F, 45F, 0F, 166, 138, 91, false),
        new Cube(-0.36F, 1.508F, -0.36F, 0.36F, 2.262F, 0.36F, -0.268F, 0F, 0.268F, 0F, 45F, 0F, 166, 138, 91, false),
        new Cube(-0.36F, 1.508F, -0.36F, 0.36F, 2.262F, 0.36F, 0.268F, 0F, 0.268F, 0F, 45F, 0F, 166, 138, 91, false),
        new Cube(-0.1276F, 2.262F, -0.1276F, 0.1276F, 2.9F, 0.1276F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.609F, -1.798F, -0.609F, 0.609F, -1.392F, 0.609F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.1392F, 2.436F, -0.1392F, 0.1392F, 3.364F, 0.1392F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.6264F, -1.218F, -0.6264F, 0.6264F, -0.812F, 0.6264F, 0F, 0F, 0F, 0F, 0F, 0F, 214, 82, 60, false)
    };

    private static final Cube[] FALLING_WARHEAD = {
        new Cube(-1.76F, -7F, -1.76F, 1.76F, 4.4F, 1.76F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-2.1F, -7F, -1.76F, 2.1F, 4.4F, 1.76F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-1.76F, -7F, -2.1F, 1.76F, 4.4F, 2.1F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -7F, -0.36F, 0.36F, 4.4F, 0.36F, -1.86F, 0F, -1.86F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -7F, -0.36F, 0.36F, 4.4F, 0.36F, 1.86F, 0F, -1.86F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -7F, -0.36F, 0.36F, 4.4F, 0.36F, -1.86F, 0F, 1.86F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -7F, -0.36F, 0.36F, 4.4F, 0.36F, 1.86F, 0F, 1.86F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-1.526F, 4.4F, -1.526F, 1.526F, 5.8F, 1.526F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-1.806F, 4.4F, -1.526F, 1.806F, 5.8F, 1.526F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-1.526F, 4.4F, -1.806F, 1.526F, 5.8F, 1.806F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, 4.4F, -0.36F, 0.36F, 5.8F, 0.36F, -1.606F, 0F, -1.606F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, 4.4F, -0.36F, 0.36F, 5.8F, 0.36F, 1.606F, 0F, -1.606F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, 4.4F, -0.36F, 0.36F, 5.8F, 0.36F, -1.606F, 0F, 1.606F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, 4.4F, -0.36F, 0.36F, 5.8F, 0.36F, 1.606F, 0F, 1.606F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-1.018F, 5.8F, -1.018F, 1.018F, 7F, 1.018F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-1.218F, 5.8F, -1.018F, 1.218F, 7F, 1.018F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-1.018F, 5.8F, -1.218F, 1.018F, 7F, 1.218F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 5.8F, -0.36F, 0.36F, 7F, 0.36F, -1.078F, 0F, -1.078F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 5.8F, -0.36F, 0.36F, 7F, 0.36F, 1.078F, 0F, -1.078F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 5.8F, -0.36F, 0.36F, 7F, 0.36F, -1.078F, 0F, 1.078F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 5.8F, -0.36F, 0.36F, 7F, 0.36F, 1.078F, 0F, 1.078F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.462F, 7F, -0.462F, 0.462F, 8.05F, 0.462F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-2.26F, -6F, -2.26F, 2.26F, -5.25F, 2.26F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-2.16F, -0.28F, -2.16F, 2.16F, 1.12F, 2.16F, 0F, 0F, 0F, 0F, 0F, 0F, 214, 82, 60, false),
        new Cube(-0.68F, -9.2F, -0.68F, 0.68F, -4.3F, 0.68F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.92F, -9.2F, -0.68F, 0.92F, -4.3F, 0.68F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.68F, -9.2F, -0.92F, 0.68F, -4.3F, 0.92F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -9.2F, -0.36F, 0.36F, -4.3F, 0.36F, -0.76F, 0F, -0.76F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -9.2F, -0.36F, 0.36F, -4.3F, 0.36F, 0.76F, 0F, -0.76F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -9.2F, -0.36F, 0.36F, -4.3F, 0.36F, -0.76F, 0F, 0.76F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -9.2F, -0.36F, 0.36F, -4.3F, 0.36F, 0.76F, 0F, 0.76F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.24F, -8F, -3.55F, 0.24F, -4F, -2.1F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.24F, -8F, 2.1F, 0.24F, -4F, 3.55F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-3.55F, -8F, -0.24F, -2.1F, -4F, 0.24F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(2.1F, -8F, -0.24F, 3.55F, -4F, 0.24F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.7F, 2.4F, -2.22F, 0.7F, 4F, -2.08F, 0F, 0F, 0F, 0F, 0F, 0F, 69, 199, 196, true)
    };

    private static final Cube[] ARTILLERY_SHELL = {
        new Cube(-1.21F, -4.4F, -1.21F, 1.21F, 1.8F, 1.21F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-1.55F, -4.4F, -1.21F, 1.55F, 1.8F, 1.21F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-1.21F, -4.4F, -1.55F, 1.21F, 1.8F, 1.55F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -4.4F, -0.36F, 0.36F, 1.8F, 0.36F, -1.31F, 0F, -1.31F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -4.4F, -0.36F, 0.36F, 1.8F, 0.36F, 1.31F, 0F, -1.31F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -4.4F, -0.36F, 0.36F, 1.8F, 0.36F, -1.31F, 0F, 1.31F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -4.4F, -0.36F, 0.36F, 1.8F, 0.36F, 1.31F, 0F, 1.31F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-1.053F, 1.8F, -1.053F, 1.053F, 3.2F, 1.053F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-1.333F, 1.8F, -1.053F, 1.333F, 3.2F, 1.053F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-1.053F, 1.8F, -1.333F, 1.053F, 3.2F, 1.333F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, 1.8F, -0.36F, 0.36F, 3.2F, 0.36F, -1.133F, 0F, -1.133F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, 1.8F, -0.36F, 0.36F, 3.2F, 0.36F, 1.133F, 0F, -1.133F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, 1.8F, -0.36F, 0.36F, 3.2F, 0.36F, -1.133F, 0F, 1.133F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, 1.8F, -0.36F, 0.36F, 3.2F, 0.36F, 1.133F, 0F, 1.133F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.699F, 3.2F, -0.699F, 0.699F, 4.4F, 0.699F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.899F, 3.2F, -0.699F, 0.899F, 4.4F, 0.699F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.699F, 3.2F, -0.899F, 0.699F, 4.4F, 0.899F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 3.2F, -0.36F, 0.36F, 4.4F, 0.36F, -0.759F, 0F, -0.759F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 3.2F, -0.36F, 0.36F, 4.4F, 0.36F, 0.759F, 0F, -0.759F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 3.2F, -0.36F, 0.36F, 4.4F, 0.36F, -0.759F, 0F, 0.759F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 3.2F, -0.36F, 0.36F, 4.4F, 0.36F, 0.759F, 0F, 0.759F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.341F, 4.4F, -0.341F, 0.341F, 5.45F, 0.341F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-1.71F, -3.4F, -1.71F, 1.71F, -2.65F, 1.71F, 0F, 0F, 0F, 0F, 0F, 0F, 195, 164, 90, false),
        new Cube(-1.61F, -0.176F, -1.61F, 1.61F, 0.704F, 1.61F, 0F, 0F, 0F, 0F, 0F, 0F, 214, 82, 60, false)
    };

    private static final Cube[] HE_ROCKET = {
        new Cube(-1.73F, -8F, -1.73F, 1.73F, 5.2F, 1.73F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-2.05F, -8F, -1.73F, 2.05F, 5.2F, 1.73F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-1.73F, -8F, -2.05F, 1.73F, 5.2F, 2.05F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -8F, -0.36F, 0.36F, 5.2F, 0.36F, -1.81F, 0F, -1.81F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -8F, -0.36F, 0.36F, 5.2F, 0.36F, 1.81F, 0F, -1.81F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -8F, -0.36F, 0.36F, 5.2F, 0.36F, -1.81F, 0F, 1.81F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -8F, -0.36F, 0.36F, 5.2F, 0.36F, 1.81F, 0F, 1.81F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-1.606F, 4.1F, -1.606F, 1.606F, 7.6F, 1.606F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-1.886F, 4.1F, -1.606F, 1.886F, 7.6F, 1.606F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-1.606F, 4.1F, -1.886F, 1.606F, 7.6F, 1.886F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, 4.1F, -0.36F, 0.36F, 7.6F, 0.36F, -1.686F, 0F, -1.686F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, 4.1F, -0.36F, 0.36F, 7.6F, 0.36F, 1.686F, 0F, -1.686F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, 4.1F, -0.36F, 0.36F, 7.6F, 0.36F, -1.686F, 0F, 1.686F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, 4.1F, -0.36F, 0.36F, 7.6F, 0.36F, 1.686F, 0F, 1.686F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-1.318F, 7.6F, -1.318F, 1.318F, 9.1F, 1.318F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-1.558F, 7.6F, -1.318F, 1.558F, 9.1F, 1.318F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-1.318F, 7.6F, -1.558F, 1.318F, 9.1F, 1.558F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 7.6F, -0.36F, 0.36F, 9.1F, 0.36F, -1.388F, 0F, -1.388F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 7.6F, -0.36F, 0.36F, 9.1F, 0.36F, 1.388F, 0F, -1.388F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 7.6F, -0.36F, 0.36F, 9.1F, 0.36F, -1.388F, 0F, 1.388F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 7.6F, -0.36F, 0.36F, 9.1F, 0.36F, 1.388F, 0F, 1.388F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.824F, 9.1F, -0.824F, 0.824F, 10.4F, 0.824F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.984F, 9.1F, -0.824F, 0.984F, 10.4F, 0.824F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.824F, 9.1F, -0.984F, 0.824F, 10.4F, 0.984F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 9.1F, -0.36F, 0.36F, 10.4F, 0.36F, -0.864F, 0F, -0.864F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 9.1F, -0.36F, 0.36F, 10.4F, 0.36F, 0.864F, 0F, -0.864F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 9.1F, -0.36F, 0.36F, 10.4F, 0.36F, -0.864F, 0F, 0.864F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 9.1F, -0.36F, 0.36F, 10.4F, 0.36F, 0.864F, 0F, 0.864F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.451F, 10.4F, -0.451F, 0.451F, 11.3F, 0.451F, 0F, 0F, 0F, 0F, 0F, 0F, 69, 199, 196, true),
        new Cube(-2.23F, 5.45F, -2.23F, 2.23F, 6.2F, 2.23F, 0F, 0F, 0F, 0F, 0F, 0F, 214, 82, 60, false),
        new Cube(-2.23F, -4.4F, -2.23F, 2.23F, -3.65F, 2.23F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-2.23F, 3.2F, -2.23F, 2.23F, 4.1F, 2.23F, 0F, 0F, 0F, 0F, 0F, 0F, 214, 82, 60, false),
        new Cube(-0.3F, -7.2F, -4.05F, 0.3F, -3.6F, -1.97F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.3F, -7.2F, 1.97F, 0.3F, -3.6F, 4.05F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-4.05F, -7.2F, -0.3F, -1.97F, -3.6F, 0.3F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(1.97F, -7.2F, -0.3F, 4.05F, -3.6F, 0.3F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-1.421F, -10F, -1.421F, 1.421F, -7.8F, 1.421F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-1.681F, -10F, -1.421F, 1.681F, -7.8F, 1.421F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-1.421F, -10F, -1.681F, 1.421F, -7.8F, 1.681F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -10F, -0.36F, 0.36F, -7.8F, 0.36F, -1.501F, 0F, -1.501F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -10F, -0.36F, 0.36F, -7.8F, 0.36F, 1.501F, 0F, -1.501F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -10F, -0.36F, 0.36F, -7.8F, 0.36F, -1.501F, 0F, 1.501F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -10F, -0.36F, 0.36F, -7.8F, 0.36F, 1.501F, 0F, 1.501F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-1.03F, -11F, -1.03F, 1.03F, -9.2F, 1.03F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-1.23F, -11F, -1.03F, 1.23F, -9.2F, 1.03F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-1.03F, -11F, -1.23F, 1.03F, -9.2F, 1.23F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, -11F, -0.36F, 0.36F, -9.2F, 0.36F, -1.09F, 0F, -1.09F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, -11F, -0.36F, 0.36F, -9.2F, 0.36F, 1.09F, 0F, -1.09F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, -11F, -0.36F, 0.36F, -9.2F, 0.36F, -1.09F, 0F, 1.09F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, -11F, -0.36F, 0.36F, -9.2F, 0.36F, 1.09F, 0F, 1.09F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.492F, -11.2F, -0.492F, 0.492F, -9.1F, 0.492F, 0F, 0F, 0F, 0F, 0F, 0F, 69, 199, 196, true)
    };

    private static final Cube[] ANTI_AIR_MISSILE_MK1 = {
        new Cube(-0.92F, -10.5F, -0.92F, 0.92F, 7F, 0.92F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-1.24F, -10.5F, -0.92F, 1.24F, 7F, 0.92F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-0.92F, -10.5F, -1.24F, 0.92F, 7F, 1.24F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -10.5F, -0.36F, 0.36F, 7F, 0.36F, -1F, 0F, -1F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -10.5F, -0.36F, 0.36F, 7F, 0.36F, 1F, 0F, -1F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -10.5F, -0.36F, 0.36F, 7F, 0.36F, -1F, 0F, 1F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -10.5F, -0.36F, 0.36F, 7F, 0.36F, 1F, 0F, 1F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.8608F, 5.9F, -0.8608F, 0.8608F, 9.4F, 0.8608F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-1.1408F, 5.9F, -0.8608F, 1.1408F, 9.4F, 0.8608F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.8608F, 5.9F, -1.1408F, 0.8608F, 9.4F, 1.1408F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, 5.9F, -0.36F, 0.36F, 9.4F, 0.36F, -0.9408F, 0F, -0.9408F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, 5.9F, -0.36F, 0.36F, 9.4F, 0.36F, 0.9408F, 0F, -0.9408F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, 5.9F, -0.36F, 0.36F, 9.4F, 0.36F, -0.9408F, 0F, 0.9408F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, 5.9F, -0.36F, 0.36F, 9.4F, 0.36F, 0.9408F, 0F, 0.9408F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.7024F, 9.4F, -0.7024F, 0.7024F, 10.9F, 0.7024F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.9424F, 9.4F, -0.7024F, 0.9424F, 10.9F, 0.7024F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.7024F, 9.4F, -0.9424F, 0.7024F, 10.9F, 0.9424F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 9.4F, -0.36F, 0.36F, 10.9F, 0.36F, -0.7724F, 0F, -0.7724F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 9.4F, -0.36F, 0.36F, 10.9F, 0.36F, 0.7724F, 0F, -0.7724F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 9.4F, -0.36F, 0.36F, 10.9F, 0.36F, -0.7724F, 0F, 0.7724F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 9.4F, -0.36F, 0.36F, 10.9F, 0.36F, 0.7724F, 0F, 0.7724F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.4352F, 10.9F, -0.4352F, 0.4352F, 12.2F, 0.4352F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.5952F, 10.9F, -0.4352F, 0.5952F, 12.2F, 0.4352F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.4352F, 10.9F, -0.5952F, 0.4352F, 12.2F, 0.5952F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 10.9F, -0.36F, 0.36F, 12.2F, 0.36F, -0.4752F, 0F, -0.4752F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 10.9F, -0.36F, 0.36F, 12.2F, 0.36F, 0.4752F, 0F, -0.4752F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 10.9F, -0.36F, 0.36F, 12.2F, 0.36F, -0.4752F, 0F, 0.4752F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 10.9F, -0.36F, 0.36F, 12.2F, 0.36F, 0.4752F, 0F, 0.4752F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.2728F, 12.2F, -0.2728F, 0.2728F, 13.1F, 0.2728F, 0F, 0F, 0F, 0F, 0F, 0F, 69, 199, 196, true),
        new Cube(-1.42F, 7.25F, -1.42F, 1.42F, 8F, 1.42F, 0F, 0F, 0F, 0F, 0F, 0F, 110, 139, 75, false),
        new Cube(-1.42F, -6.9F, -1.42F, 1.42F, -6.15F, 1.42F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.3F, -9.7F, -3.49F, 0.3F, -5.1F, -1.16F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.3F, -9.7F, 1.16F, 0.3F, -5.1F, 3.49F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-3.49F, -9.7F, -0.3F, -1.16F, -5.1F, 0.3F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(1.16F, -9.7F, -0.3F, 3.49F, -5.1F, 0.3F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.7568F, -12.5F, -0.7568F, 0.7568F, -10.3F, 0.7568F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-1.0168F, -12.5F, -0.7568F, 1.0168F, -10.3F, 0.7568F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.7568F, -12.5F, -1.0168F, 0.7568F, -10.3F, 1.0168F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -12.5F, -0.36F, 0.36F, -10.3F, 0.36F, -0.8368F, 0F, -0.8368F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -12.5F, -0.36F, 0.36F, -10.3F, 0.36F, 0.8368F, 0F, -0.8368F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -12.5F, -0.36F, 0.36F, -10.3F, 0.36F, -0.8368F, 0F, 0.8368F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -12.5F, -0.36F, 0.36F, -10.3F, 0.36F, 0.8368F, 0F, 0.8368F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.544F, -13.5F, -0.544F, 0.544F, -11.7F, 0.544F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.744F, -13.5F, -0.544F, 0.744F, -11.7F, 0.544F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.544F, -13.5F, -0.744F, 0.544F, -11.7F, 0.744F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, -13.5F, -0.36F, 0.36F, -11.7F, 0.36F, -0.604F, 0F, -0.604F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, -13.5F, -0.36F, 0.36F, -11.7F, 0.36F, 0.604F, 0F, -0.604F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, -13.5F, -0.36F, 0.36F, -11.7F, 0.36F, -0.604F, 0F, 0.604F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, -13.5F, -0.36F, 0.36F, -11.7F, 0.36F, 0.604F, 0F, 0.604F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.2976F, -13.7F, -0.2976F, 0.2976F, -11.6F, 0.2976F, 0F, 0F, 0F, 0F, 0F, 0F, 69, 199, 196, true)
    };

    private static final Cube[] ANTI_AIR_MISSILE_MK2 = {
        new Cube(-1.13F, -13F, -1.13F, 1.13F, 8.6F, 1.13F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-1.45F, -13F, -1.13F, 1.45F, 8.6F, 1.13F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-1.13F, -13F, -1.45F, 1.13F, 8.6F, 1.45F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -13F, -0.36F, 0.36F, 8.6F, 0.36F, -1.21F, 0F, -1.21F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -13F, -0.36F, 0.36F, 8.6F, 0.36F, 1.21F, 0F, -1.21F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -13F, -0.36F, 0.36F, 8.6F, 0.36F, -1.21F, 0F, 1.21F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-0.36F, -13F, -0.36F, 0.36F, 8.6F, 0.36F, 1.21F, 0F, 1.21F, 0F, 45F, 0F, 89, 99, 79, false),
        new Cube(-1.054F, 7.5F, -1.054F, 1.054F, 11F, 1.054F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-1.334F, 7.5F, -1.054F, 1.334F, 11F, 1.054F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-1.054F, 7.5F, -1.334F, 1.054F, 11F, 1.334F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, 7.5F, -0.36F, 0.36F, 11F, 0.36F, -1.134F, 0F, -1.134F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, 7.5F, -0.36F, 0.36F, 11F, 0.36F, 1.134F, 0F, -1.134F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, 7.5F, -0.36F, 0.36F, 11F, 0.36F, -1.134F, 0F, 1.134F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, 7.5F, -0.36F, 0.36F, 11F, 0.36F, 1.134F, 0F, 1.134F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.862F, 11F, -0.862F, 0.862F, 12.5F, 0.862F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-1.102F, 11F, -0.862F, 1.102F, 12.5F, 0.862F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.862F, 11F, -1.102F, 0.862F, 12.5F, 1.102F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 11F, -0.36F, 0.36F, 12.5F, 0.36F, -0.932F, 0F, -0.932F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 11F, -0.36F, 0.36F, 12.5F, 0.36F, 0.932F, 0F, -0.932F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 11F, -0.36F, 0.36F, 12.5F, 0.36F, -0.932F, 0F, 0.932F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 11F, -0.36F, 0.36F, 12.5F, 0.36F, 0.932F, 0F, 0.932F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.536F, 12.5F, -0.536F, 0.536F, 13.8F, 0.536F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.696F, 12.5F, -0.536F, 0.696F, 13.8F, 0.536F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.536F, 12.5F, -0.696F, 0.536F, 13.8F, 0.696F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 12.5F, -0.36F, 0.36F, 13.8F, 0.36F, -0.576F, 0F, -0.576F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 12.5F, -0.36F, 0.36F, 13.8F, 0.36F, 0.576F, 0F, -0.576F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 12.5F, -0.36F, 0.36F, 13.8F, 0.36F, -0.576F, 0F, 0.576F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, 12.5F, -0.36F, 0.36F, 13.8F, 0.36F, 0.576F, 0F, 0.576F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.319F, 13.8F, -0.319F, 0.319F, 14.7F, 0.319F, 0F, 0F, 0F, 0F, 0F, 0F, 69, 199, 196, true),
        new Cube(-1.63F, 8.85F, -1.63F, 1.63F, 9.6F, 1.63F, 0F, 0F, 0F, 0F, 0F, 0F, 71, 127, 165, false),
        new Cube(-1.63F, -9.4F, -1.63F, 1.63F, -8.65F, 1.63F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-1.63F, -4.8F, -1.63F, 1.63F, -4F, 1.63F, 0F, 0F, 0F, 0F, 0F, 0F, 71, 127, 165, false),
        new Cube(-1.61F, -8F, -0.22F, -1.37F, 7.8F, 0.22F, 0F, 0F, 0F, 0F, 0F, 0F, 71, 127, 165, false),
        new Cube(-0.3F, -12.2F, -4.15F, 0.3F, -6.4F, -1.37F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.3F, -12.2F, 1.37F, 0.3F, -6.4F, 4.15F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-4.15F, -12.2F, -0.3F, -1.37F, -6.4F, 0.3F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(1.37F, -12.2F, -0.3F, 4.15F, -6.4F, 0.3F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.18F, 7.6F, -2.8F, 0.18F, 9.8F, -1.41F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.18F, 7.6F, 1.41F, 0.18F, 9.8F, 2.8F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-2.8F, 7.6F, -0.18F, -1.41F, 9.8F, 0.18F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(1.41F, 7.6F, -0.18F, 2.8F, 9.8F, 0.18F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-0.929F, -15F, -0.929F, 0.929F, -12.8F, 0.929F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-1.189F, -15F, -0.929F, 1.189F, -12.8F, 0.929F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.929F, -15F, -1.189F, 0.929F, -12.8F, 1.189F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -15F, -0.36F, 0.36F, -12.8F, 0.36F, -1.009F, 0F, -1.009F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -15F, -0.36F, 0.36F, -12.8F, 0.36F, 1.009F, 0F, -1.009F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -15F, -0.36F, 0.36F, -12.8F, 0.36F, -1.009F, 0F, 1.009F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.36F, -15F, -0.36F, 0.36F, -12.8F, 0.36F, 1.009F, 0F, 1.009F, 0F, 45F, 0F, 32, 41, 43, false),
        new Cube(-0.67F, -16F, -0.67F, 0.67F, -14.2F, 0.67F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.87F, -16F, -0.67F, 0.87F, -14.2F, 0.67F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.67F, -16F, -0.87F, 0.67F, -14.2F, 0.87F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, -16F, -0.36F, 0.36F, -14.2F, 0.36F, -0.73F, 0F, -0.73F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, -16F, -0.36F, 0.36F, -14.2F, 0.36F, 0.73F, 0F, -0.73F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, -16F, -0.36F, 0.36F, -14.2F, 0.36F, -0.73F, 0F, 0.73F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.36F, -16F, -0.36F, 0.36F, -14.2F, 0.36F, 0.73F, 0F, 0.73F, 0F, 45F, 0F, 96, 71, 53, false),
        new Cube(-0.348F, -16.2F, -0.348F, 0.348F, -14.1F, 0.348F, 0F, 0F, 0F, 0F, 0F, 0F, 69, 199, 196, true)
    };

    private static final Cube[] ARTILLERY_FIXED = {
        new Cube(-16F, 0F, -13F, -10F, 4F, 13F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(10F, 0F, -13F, 16F, 4F, 13F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-15F, 4F, -10F, -11F, 6F, 10F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(11F, 4F, -10F, 15F, 6F, 10F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-10F, 4F, -12F, 10F, 7F, -7F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-10F, 4F, 5F, 10F, 7F, 12F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-11F, 3F, -8F, 11F, 8F, 8F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-7F, 7F, -7F, 7F, 9F, 7F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-14F, 1F, -14F, -11F, 3F, -12F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(11F, 1F, -14F, 14F, 3F, -12F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-16.3F, 0.7F, -10.5F, -9.7F, 3.6F, -6.5F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-16.3F, 0.7F, -4.3F, -9.7F, 3.6F, -0.3F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-16.3F, 0.7F, 2F, -9.7F, 3.6F, 6F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-16.3F, 0.7F, 8F, -9.7F, 3.6F, 12F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(9.7F, 0.7F, -10.5F, 16.3F, 3.6F, -6.5F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(9.7F, 0.7F, -4.3F, 16.3F, 3.6F, -0.3F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(9.7F, 0.7F, 2F, 16.3F, 3.6F, 6F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(9.7F, 0.7F, 8F, 16.3F, 3.6F, 12F, 0F, 0F, 0F, 0F, 0F, 0F, 96, 71, 53, false),
        new Cube(-19F, -1F, 7F, -14F, 1.2F, 14F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(14F, -1F, 7F, 19F, 1.2F, 14F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-14F, 1F, 9F, -10F, 3.2F, 18F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(10F, 1F, 9F, 14F, 3.2F, 18F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false)
    };

    private static final Cube[] ARTILLERY_YAW = {
        new Cube(-9F, 8F, -8F, 9F, 11F, 8F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-8F, 10F, -7F, 8F, 16F, 7F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-7F, 11F, -10F, 7F, 15F, -6F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-7F, 11F, 6F, 7F, 15F, 11F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-10F, 12F, -5F, -6F, 17F, 3F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(6F, 12F, -5F, 10F, 17F, 3F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-3F, 16F, 1F, 3F, 17F, 6F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(4F, 15F, -6F, 7F, 20F, -2F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(4.4F, 16.2F, -6.3F, 6.6F, 18.7F, -6F, 0F, 0F, 0F, 0F, 0F, 0F, 69, 199, 196, true),
        new Cube(-8.2F, 11.7F, 3F, -8F, 14.2F, 6.5F, 0F, 0F, 0F, 0F, 0F, 0F, 214, 82, 60, false)
    };

    private static final Cube[] ARTILLERY_PITCH = {
        new Cube(-4.8F, 11F, -2F, 4.8F, 17F, 8F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-6F, 11F, -6F, 6F, 17F, -1F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-3.3F, 15F, -18F, -1.2F, 17F, -4F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(1.2F, 15F, -18F, 3.3F, 17F, -4F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-2.8F, 12.1F, -20F, 2.8F, 16.2F, -5F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-1.35F, 13F, -38F, 1.35F, 15.4F, -17F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-2.8F, 12.2F, -43F, 2.8F, 16F, -37.5F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-3F, 13F, -41.8F, -2.4F, 15.2F, -38.8F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(2.4F, 13F, -41.8F, 3F, 15.2F, -38.8F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-0.75F, 13.45F, -43.5F, 0.75F, 14.95F, -42.7F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false)
    };

    private static final Cube[] RADAR_YAW = {
        new Cube(-7F, 16F, -7F, 7F, 19F, 7F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-5F, 18F, -5F, 5F, 23F, 5F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-8F, 21F, -2F, -4F, 27F, 2F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(4F, 21F, -2F, 8F, 27F, 2F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-5F, 18F, 4F, 5F, 22F, 9F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-3F, 19F, 8.9F, 3F, 21.5F, 9.2F, 0F, 0F, 0F, 0F, 0F, 0F, 89, 99, 79, false),
        new Cube(-8.3F, 23F, -2.3F, 8.3F, 25.3F, 2.3F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false)
    };

    private static final Cube[] RADAR_PITCH = {
        new Cube(-5F, 20F, -0.7F, 5F, 30F, 0.7F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-9F, 21F, -0.5F, -5F, 29F, 0.5F, -5F, 25F, 0F, 0F, -10F, 0F, 166, 138, 91, false),
        new Cube(5F, 21F, -0.5F, 9F, 29F, 0.5F, 5F, 25F, 0F, 0F, 10F, 0F, 166, 138, 91, false),
        new Cube(-12F, 22F, -0.35F, -9F, 28F, 0.35F, -9F, 25F, 0F, 0F, -20F, 0F, 166, 138, 91, false),
        new Cube(9F, 22F, -0.35F, 12F, 28F, 0.35F, 9F, 25F, 0F, 0F, 20F, 0F, 166, 138, 91, false),
        new Cube(-12F, 29.7F, -0.9F, 12F, 30.5F, 0.9F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-12F, 19.5F, -0.9F, 12F, 20.3F, 0.9F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-12.5F, 20F, -0.9F, -11.7F, 30F, 0.9F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(11.7F, 20F, -0.9F, 12.5F, 30F, 0.9F, 0F, 0F, 0F, 0F, 0F, 0F, 166, 138, 91, false),
        new Cube(-7F, 24F, -7F, -6F, 25F, 0F, -6.5F, 24.5F, 0F, 0F, 8F, 0F, 32, 41, 43, false),
        new Cube(6F, 24F, -7F, 7F, 25F, 0F, 6.5F, 24.5F, 0F, 0F, -8F, 0F, 32, 41, 43, false),
        new Cube(-2F, 23F, -10F, 2F, 27F, -7F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-2.6F, 22.4F, -11F, 2.6F, 27.6F, -9.8F, 0F, 0F, 0F, 0F, 0F, 0F, 32, 41, 43, false),
        new Cube(-1.6F, 23.4F, -11.2F, 1.6F, 26.6F, -10.9F, 0F, 0F, 0F, 0F, 0F, 0F, 69, 199, 196, true)
    };

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Model model, final float scale, final float originX, final float originY,
        final float originZ, final int light) {
        for (Cube cube : cubes(model)) cube.render(pose, buffer, scale, originX, originY, originZ, light);
    }

    private static Cube[] cubes(final Model model) {
        return switch (model) {
            case PISTOL_BULLET -> PISTOL_BULLET;
            case RIFLE_BULLET -> RIFLE_BULLET;
            case SNIPER_BULLET -> SNIPER_BULLET;
            case FALLING_WARHEAD -> FALLING_WARHEAD;
            case ARTILLERY_SHELL -> ARTILLERY_SHELL;
            case HE_ROCKET -> HE_ROCKET;
            case ANTI_AIR_MISSILE_MK1 -> ANTI_AIR_MISSILE_MK1;
            case ANTI_AIR_MISSILE_MK2 -> ANTI_AIR_MISSILE_MK2;
            case ARTILLERY_FIXED -> ARTILLERY_FIXED;
            case ARTILLERY_YAW -> ARTILLERY_YAW;
            case ARTILLERY_PITCH -> ARTILLERY_PITCH;
            case RADAR_YAW -> RADAR_YAW;
            case RADAR_PITCH -> RADAR_PITCH;
        };
    }

    private static final class Cube {
        private final float x0, y0, z0, x1, y1, z1, ox, oy, oz;
        private final float sinX, cosX, sinY, cosY, sinZ, cosZ;
        private final int red, green, blue;
        private final boolean emissive;

        private Cube(float x0, float y0, float z0, float x1, float y1, float z1,
            float ox, float oy, float oz, float rotationX, float rotationY, float rotationZ,
            int red, int green, int blue, boolean emissive) {
            this.x0=x0; this.y0=y0; this.z0=z0; this.x1=x1; this.y1=y1; this.z1=z1;
            this.ox=ox; this.oy=oy; this.oz=oz;
            float rx=(float)Math.toRadians(rotationX), ry=(float)Math.toRadians(rotationY), rz=(float)Math.toRadians(rotationZ);
            sinX=(float)Math.sin(rx); cosX=(float)Math.cos(rx);
            sinY=(float)Math.sin(ry); cosY=(float)Math.cos(ry);
            sinZ=(float)Math.sin(rz); cosZ=(float)Math.cos(rz);
            this.red=red; this.green=green; this.blue=blue; this.emissive=emissive;
        }

        private void render(PoseStack.Pose pose, VertexConsumer buffer, float scale,
            float originX, float originY, float originZ, int light) {
            quad(pose,buffer,x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0,0,0,-1,scale,originX,originY,originZ,light);
            quad(pose,buffer,x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1,0,0,1,scale,originX,originY,originZ,light);
            quad(pose,buffer,x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1,-1,0,0,scale,originX,originY,originZ,light);
            quad(pose,buffer,x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0,1,0,0,scale,originX,originY,originZ,light);
            quad(pose,buffer,x0,y1,z0,x1,y1,z0,x1,y1,z1,x0,y1,z1,0,1,0,scale,originX,originY,originZ,light);
            quad(pose,buffer,x0,y0,z1,x1,y0,z1,x1,y0,z0,x0,y0,z0,0,-1,0,scale,originX,originY,originZ,light);
        }

        private void quad(PoseStack.Pose pose, VertexConsumer buffer,
            float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,
            float nx,float ny,float nz,float scale,float originX,float originY,float originZ,int light) {
            vertex(pose,buffer,ax,ay,az,nx,ny,nz,scale,originX,originY,originZ,light);
            vertex(pose,buffer,bx,by,bz,nx,ny,nz,scale,originX,originY,originZ,light);
            vertex(pose,buffer,cx,cy,cz,nx,ny,nz,scale,originX,originY,originZ,light);
            vertex(pose,buffer,dx,dy,dz,nx,ny,nz,scale,originX,originY,originZ,light);
        }

        private void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x,float y,float z,
            float nx,float ny,float nz,float scale,float originX,float originY,float originZ,int light) {
            float px=x-ox, py=y-oy, pz=z-oz;
            float py1=py*cosX-pz*sinX, pz1=py*sinX+pz*cosX;
            float px2=px*cosY+pz1*sinY, pz2=-px*sinY+pz1*cosY;
            float px3=px2*cosZ-py1*sinZ, py3=px2*sinZ+py1*cosZ;
            float nny=ny*cosX-nz*sinX, nnz=ny*sinX+nz*cosX;
            float nnx2=nx*cosY+nnz*sinY, nnz2=-nx*sinY+nnz*cosY;
            float nnx=nnx2*cosZ-nny*sinZ, nny2=nnx2*sinZ+nny*cosZ;
            buffer.addVertex(pose,(px3+ox-originX)*scale,(py3+oy-originY)*scale,(pz2+oz-originZ)*scale)
                .setColor(red,green,blue,255).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(emissive?0xF000F0:light).setNormal(pose,nnx,nny2,nnz2);
        }
    }
}
