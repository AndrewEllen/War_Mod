package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the gameplay invariant that preprocessing can never hold physical impact. */
final class NuclearImpactGateContractTest {
    @Test
    void everyNuclearDeliveryPathIsFreeOfTerrainReadinessHolds() throws IOException {
        assertOmits("entity/IncomingWarheadEntity.java", "terrainReady(");
        assertOmits("entity/ArtilleryWarheadEntity.java", "terrainReady(", "pendingImpact");
        assertOmits("entity/RocketProjectileEntity.java", "terrainReady(", "pendingImpact");
        assertOmits("entity/TimedWarheadTntEntity.java", "allPrepared(", "fuse == 1");
        assertOmits("icbm/IcbmFlightController.java", "collisionTerrainReady(",
            "pendingCollision");
    }

    @Test
    void observableImpactIsDispatchedBeforeTerrainSeal() throws IOException {
        String source = source("warhead/WarheadImpactService.java");
        int visual = source.indexOf("WarheadVisualNetworking.sendImpact(level, visualPayload");
        int sound = source.indexOf("AcousticEngine.playSoundAtTime", visual);
        int entityBlast = source.indexOf("WarheadExplosionWorkManager.detonateEntitiesOnly",
            visual);
        int terrainSeal = source.indexOf("WarheadPreparationCoordinator.sealImpact", visual);
        assertTrue(visual >= 0 && sound > visual && entityBlast > sound
            && terrainSeal > entityBlast,
            "visual, sound and entity blast must be dispatched before terrain sealing");
    }

    private static void assertOmits(final String relative,
        final String... forbidden) throws IOException {
        String source = source(relative);
        for (String value : forbidden) {
            assertFalse(source.contains(value), () -> relative
                + " reintroduced forbidden impact gate " + value);
        }
    }

    private static String source(final String relative) throws IOException {
        return Files.readString(Path.of("src", "main", "java", "com", "andye",
            "warmod").resolve(relative));
    }
}
