package com.andye.warmod.fire;

import com.andye.warmod.fire.wind.FireWindImpulse;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FireWindImpulseTest {
    @Test
    void pressureWaveSustainsOutwardMotionThenReturnsInward() {
        FireWindImpulse impulse = new FireWindImpulse(Vec3.ZERO, 120, 2, 100, 300, true);
        Vec3 east = new Vec3(30, 0, 0);
        assertEquals(Vec3.ZERO, impulse.sample(east, 100));
        assertTrue(impulse.sample(east, 110).x > 0.1);
        assertTrue(impulse.sample(east, 130).x > 0.1);
        assertTrue(impulse.sample(east, 200).x > 0.1);
        assertTrue(impulse.sample(east, 350).x < -0.1);
        assertTrue(impulse.sample(east, 400).x < -0.1);
        assertEquals(Vec3.ZERO, impulse.sample(new Vec3(121, 0, 0), 130));
        assertTrue(impulse.expired(100 + impulse.effectiveDuration() + 1));
    }

    @Test
    void conventionalExplosionsNeverPullParticlesInward() {
        FireWindImpulse impulse = new FireWindImpulse(Vec3.ZERO, 120, 2, 100, 90);
        Vec3 east = new Vec3(30, 0, 0);
        for (int tick = 0; tick < 600; tick++)
            assertTrue(impulse.sample(east, tick).dot(east) >= 0.0,
                "Conventional pressure must remain outward at tick " + tick);
        assertTrue(impulse.sample(east, 150).x > 0.1);
        assertEquals(Vec3.ZERO, impulse.sample(east, 300));
    }

    @Test
    void clientPayloadPreservesNuclearPressureMode() {
        FireWindImpulse nuclear = new FireWindImpulse(Vec3.ZERO, 120, 2, 100, 300, true);
        assertEquals(nuclear, com.andye.warmod.fire.network.ClientboundFireWindImpulsePayload
            .from(nuclear).impulse());
    }
}
