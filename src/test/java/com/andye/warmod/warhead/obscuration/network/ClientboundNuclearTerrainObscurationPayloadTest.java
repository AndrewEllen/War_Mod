package com.andye.warmod.warhead.obscuration.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class ClientboundNuclearTerrainObscurationPayloadTest {
    @Test
    void authoritativeMutationProgressRoundTrips() {
        ClientboundNuclearTerrainObscurationPayload expected =
            new ClientboundNuclearTerrainObscurationPayload(UUID.randomUUID(), 91L,
                12.5, 64.0, -8.5, 0x1234ABCDL, 1.8F,
                640.0F, 96.0F, 144.0F, 80.0F, false);
        assertTrue(expected.isWellFormed());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ClientboundNuclearTerrainObscurationPayload.STREAM_CODEC.encode(buffer, expected);
            buffer.readerIndex(0);
            assertEquals(expected,
                ClientboundNuclearTerrainObscurationPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
