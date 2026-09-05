package com.andye.warmod.icbm.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.icbm.client.render.IcbmLaunchGroundSmokePolicy;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class IcbmLaunchGroundSmokeManagerTest {
    @Test
    void cloudOutlivesMinimumCarrierSeparationAndExpiresByItsOwnClock() {
        var cloud = new IcbmLaunchGroundSmokeManager.LaunchCloud(UUID.randomUUID(),
            new Vec3(12.5, 64.0, -8.5), 7L, 100.0, 1.0F,
            IcbmLaunchGroundSmokePolicy.ICBM_LOBES,
            new double[IcbmLaunchGroundSmokePolicy.ICBM_LOBES]);

        assertTrue(cloud.elapsed(420.5) > 320.0);
        assertFalse(cloud.expired(420.0),
            "carrier removal at the minimum separation time must not remove launch smoke");
        assertTrue(cloud.expired(520.0));
    }
}
