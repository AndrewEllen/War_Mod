package com.andye.warmod.warhead.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GroundVfxOwnershipContractTest {
    @Test
    void conventionalSurfaceFrontHasOneSemanticOwner() throws IOException {
        String conventional = source("src/client/java/com/andye/warmod/warhead/"
            + "client/render/ConventionalBlastParticleRenderer.java");
        String world = source("src/client/java/com/andye/warmod/warhead/"
            + "client/render/WarheadWorldRenderer.java");
        String ground = source("src/client/java/com/andye/warmod/warhead/"
            + "client/render/GroundDustFrontRenderer.java");

        for (String retired : new String[] {"SURFACE_KEY_MASK", "MATERIAL_FRONT",
            "SURFACE_FRONT", "EXPLOSION_FRONT", "renderSurfaceFront",
            "renderSurfaceExplosionPuffs", "emitSurfaceFront", "lastSurfaceTick"})
            assertFalse(conventional.contains(retired), retired + " must remain retired");
        assertEquals(1, occurrences(world, "GroundDustFrontRenderer.render("));
        assertFalse(world.contains("renderExplosionFlecks"));
        assertTrue(ground.contains("case NEAR -> 256"));
        assertTrue(ground.contains("case MEDIUM -> 128"));
        assertTrue(ground.contains("case FAR -> 64"));
        assertFalse(ground.contains("int puffs"));
    }

    @Test
    void terrainObscurationUsesOnlyTheReplacementCustomPipeline() throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));
        String renderer = source("src/client/java/com/andye/warmod/warhead/client/"
            + "obscuration/NuclearTerrainObscurationRenderer.java");
        assertTrue(renderer.contains("NuclearTerrainObscurationRenderPipelines.terrainDust()"));
        assertTrue(renderer.contains("ParticleType.TERRAIN_DUST"));
        assertTrue(renderer.contains("VisualLayer.TERRAIN_OBSCURATION"));
        assertFalse(renderer.contains("net.minecraft.client.particle"));
        assertFalse(Files.exists(root.resolve("src/client/java/com/andye/warmod/warhead/"
            + "client/curtain/NuclearDestructionCurtainRenderer.java")));
        assertFalse(Files.exists(root.resolve("src/main/java/com/andye/warmod/warhead/"
            + "curtain/NuclearDestructionCurtainEmitter.java")));
    }

    @Test
    void cpuAndGpuSmokeShroudShareComplementaryOpticalEnvelope() throws IOException {
        String world = source("src/client/java/com/andye/warmod/warhead/"
            + "client/render/WarheadWorldRenderer.java");
        assertTrue(world.contains("float cpuSmokeShroudWeight = cpuImpactLayerWeight("));
        assertTrue(world.contains("if (cpuSmokeShroudWeight > 0.001F)"));
        assertTrue(world.contains("OpticalEnvelopeVertexConsumer.scale(buffer,"));
        assertTrue(world.contains("cpuSmokeShroudWeight"));
        assertTrue(world.contains("ConventionalBlastVisualV5.renderSmokeShroud"));
        assertTrue(world.contains("vfx.submitLayer(VisualLayer.SMOKE_SHROUD"));
    }

    private static String source(final String relative) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir"), relative));
    }

    private static int occurrences(final String source, final String value) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(value, index)) >= 0;
            index += value.length()) count++;
        return count;
    }
}
