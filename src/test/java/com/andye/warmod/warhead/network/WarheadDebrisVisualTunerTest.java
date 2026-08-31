package com.andye.warmod.warhead.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.warhead.ConventionalDebrisBallistics;
import com.andye.warmod.warhead.WarheadYield;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class WarheadDebrisVisualTunerTest {
    @Test
    void ungroupedConventionalFragmentStillClearsTheOpaqueCore() {
        WarheadYield yield = WarheadYield.HIGH_EXPLOSIVE;
        ClientboundWarheadDebrisPayload.Entry entry = entry(0.0F, 0.02F);
        ClientboundWarheadDebrisPayload input = payload(false, entry);

        ClientboundWarheadDebrisPayload tuned = WarheadDebrisVisualTuner.tune(
            input, yield.visualScale(), false);

        ClientboundWarheadDebrisPayload.Entry result = tuned.entries().getFirst();
        double finalRadius = ConventionalDebrisBallistics.outwardDisplacement(
            result.velocityX(), ConventionalDebrisBallistics.OPAQUE_CORE_END_TICK);
        assertTrue(finalRadius + 1.0E-6 >=
            ConventionalDebrisBallistics.opaqueCoreRadius(yield.visualScale()) + 1.0);
        assertEquals(entry.velocityY(), result.velocityY());
        assertEquals(entry.velocityZ(), result.velocityZ());
    }

    @Test
    void ungroupedNuclearFragmentIsNotRetuned() {
        ClientboundWarheadDebrisPayload.Entry entry = entry(0.0F, 0.02F);
        ClientboundWarheadDebrisPayload input = payload(true, entry);

        ClientboundWarheadDebrisPayload tuned = WarheadDebrisVisualTuner.tune(
            input, WarheadYield.TACTICAL_NUCLEAR.visualScale(), true);

        assertEquals(input, tuned);
    }

    private static ClientboundWarheadDebrisPayload payload(final boolean nuclear,
        final ClientboundWarheadDebrisPayload.Entry entry) {
        return new ClientboundWarheadDebrisPayload(UUID.randomUUID(), 0.0, 64.0, 0.0,
            10L, nuclear, List.of(entry));
    }

    private static ClientboundWarheadDebrisPayload.Entry entry(final float offsetX,
        final float velocityX) {
        return new ClientboundWarheadDebrisPayload.Entry(offsetX, 0.0F, 0.0F,
            velocityX, 0.8F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 180,
            List.of(new ClientboundWarheadDebrisPayload.Part(1, (byte) 0, (byte) 0,
                (byte) 0)));
    }
}
