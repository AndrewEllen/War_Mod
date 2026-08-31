package com.andye.warmod.warhead.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class ClientboundWarheadDebrisPayloadTest {
    @Test
    void nuclearClassificationRoundTripsWithAuthoritativeVelocity() {
        ClientboundWarheadDebrisPayload expected = payload(true);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ClientboundWarheadDebrisPayload.STREAM_CODEC.encode(buffer, expected);
            buffer.readerIndex(0);

            ClientboundWarheadDebrisPayload decoded =
                ClientboundWarheadDebrisPayload.STREAM_CODEC.decode(buffer);
            assertEquals(expected, decoded);
            assertTrue(decoded.nuclear());
            assertTrue(decoded.isWellFormed());
        } finally {
            buffer.release();
        }
    }

    @Test
    void classificationCanBeCorrectedWithoutChangingEntries() {
        ClientboundWarheadDebrisPayload conventional = payload(false);
        ClientboundWarheadDebrisPayload nuclear = conventional.withNuclear(true);

        assertFalse(conventional.nuclear());
        assertTrue(nuclear.nuclear());
        assertEquals(conventional.entries(), nuclear.entries());
    }

    private static ClientboundWarheadDebrisPayload payload(final boolean nuclear) {
        ClientboundWarheadDebrisPayload.Part part =
            new ClientboundWarheadDebrisPayload.Part(1, (byte) 0, (byte) 0, (byte) 0);
        ClientboundWarheadDebrisPayload.Entry entry =
            new ClientboundWarheadDebrisPayload.Entry(
                3.0F, 4.0F, 5.0F,
                1.5F, 2.0F, -0.75F,
                0.01F, 0.02F, 0.03F,
                1.0F, 120, List.of(part));
        return new ClientboundWarheadDebrisPayload(UUID.randomUUID(),
            12.0, 70.0, -4.0, 900L, nuclear, List.of(entry));
    }
}
