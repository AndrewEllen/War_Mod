package com.andye.warmod.warhead.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.warhead.WarheadEffectProfile;
import com.andye.warmod.warhead.WarheadPayloadType;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class ClientboundWarheadImpactPayloadTest {
    @Test
    void authoritativeWindRoundTripsThroughPacketCodec() {
        ClientboundWarheadImpactPayload expected =
            new ClientboundWarheadImpactPayload(UUID.randomUUID(), 17L,
                WarheadNetworkState.IMPACTED, 2_400L, 12.5, 70.0, -33.25,
                2_380L, 0x4E55434C454152L, WarheadPayloadType.NUCLEAR,
                2.7F, 0.42F, -0.31F, WarheadEffectProfile.NUCLEAR);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            ClientboundWarheadImpactPayload.STREAM_CODEC.encode(buffer, expected);
            buffer.readerIndex(0);

            ClientboundWarheadImpactPayload decoded =
                ClientboundWarheadImpactPayload.STREAM_CODEC.decode(buffer);
            assertEquals(expected, decoded);
            assertTrue(decoded.isWellFormed());
        } finally {
            buffer.release();
        }
    }

    @Test
    void impossibleWindIsRejectedByValidation() {
        ClientboundWarheadImpactPayload payload =
            new ClientboundWarheadImpactPayload(UUID.randomUUID(), 0.0, 64.0, 0.0,
                10L, 20L, WarheadPayloadType.NUCLEAR, 2.5F,
                2.6F, 0.0F, WarheadEffectProfile.NUCLEAR);

        assertFalse(payload.isWellFormed());
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
