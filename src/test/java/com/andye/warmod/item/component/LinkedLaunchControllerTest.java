package com.andye.warmod.item.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

final class LinkedLaunchControllerTest {
    @Test
    void persistentLinkRoundTripsWithIdentityAndDimension() {
        LinkedLaunchController link = new LinkedLaunchController(
            Level.OVERWORLD,
            new BlockPos(120, 68, -45),
            UUID.randomUUID()
        );

        var encoded = LinkedLaunchController.CODEC
            .encodeStart(JsonOps.INSTANCE, link)
            .getOrThrow();
        LinkedLaunchController decoded = LinkedLaunchController.CODEC
            .parse(JsonOps.INSTANCE, encoded)
            .getOrThrow();

        assertEquals(link, decoded);
        assertTrue(decoded.isValid());
    }
}
