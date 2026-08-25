package com.andye.warmod.fire.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.fire.FirePhase;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class ClientboundFireStatePayloadTest {
    @Test
    void completeRepresentationRoundTripsThroughCodec() {
        ClientboundFireStatePayload payload = new ClientboundFireStatePayload(42L, 7L,
            true, List.of(new ClientboundFireStatePayload.Entry(1L, 2L, (byte) 1,
                0.5F, 0.6F, 0.7F, 0.8F, 0.9F, 0.4F, 0.3F,
                FirePhase.FLAMING, 3L, 4L, 0.1F, 0.0F, -0.1F)),
            List.of(), true, List.of(new ClientboundFireStatePayload.EmberEntry(5L,
                1.0, 2.0, 3.0, 0.1F, 0.2F, 0.3F, 0.0F, 0.1F, 0.0F,
                0.7F, 6L, 40L, 80)), true,
            List.of(new ClientboundFireStatePayload.SmokeClusterEntry(8L,
                4.0, 5.0, 6.0, 0.6F, 0.7F, 8.0F, 0.1F, 0.0F, 0.1F,
                9L, 12)));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ClientboundFireStatePayload.STREAM_CODEC.encode(buffer, payload);
            buffer.readerIndex(0);
            ClientboundFireStatePayload decoded =
                ClientboundFireStatePayload.STREAM_CODEC.decode(buffer);

            assertEquals(payload, decoded);
            assertTrue(decoded.complete());
        } finally {
            buffer.release();
        }
    }
}
