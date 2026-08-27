package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NuclearShellCouplingContractTest {
    @Test
    void productionPathUsesExactShellBatchesAndNoGlobalPhaseDrain() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("user.dir"),
            "src/main/java/com/andye/warmod/warhead/WarheadGlassShockwaveManager.java"));

        assertTrue(source.contains("record NuclearShellMutationBatch"));
        assertTrue(source.contains("preparation.shellDiscoveryComplete(coupledShell)"));
        assertTrue(source.contains("preparation.takeExactPhase"));
        assertTrue(source.contains("if (!pendingBiomeQuarts.isEmpty()) return"));
        assertTrue(source.contains("if (!pendingSurfaceFire.isEmpty()) return"));
        assertFalse(source.contains("takeThrough("));
        assertFalse(source.contains("preparedPhaseCursor"));
    }

    @Test
    void biomeMutationsAreAccumulatedPerSectionBeforeChunkResend() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("user.dir"),
            "src/main/java/com/andye/warmod/warhead/WarheadGlassShockwaveManager.java"));

        assertTrue(source.contains("Map<BiomeSectionKey, Long> pendingBiomeQuarts"));
        assertTrue(source.contains("flushBiomeMutations"));
        assertTrue(source.contains("resendBiomesForChunks(changed)"));
    }
}
