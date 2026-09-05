package com.andye.warmod.warhead.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.andye.warmod.warhead.network.ClientboundWarheadClientControlPayload.Action;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class ClientboundWarheadClientControlPayloadTest {
    @Test
    void everyActionRoundTripsThroughPacketCodec() {
        for (Action action : Action.values()) {
            RegistryFriendlyByteBuf buffer = buffer();
            try {
                ClientboundWarheadClientControlPayload expected =
                    new ClientboundWarheadClientControlPayload(action, 1.25F);
                ClientboundWarheadClientControlPayload.STREAM_CODEC.encode(buffer, expected);
                buffer.readerIndex(0);

                assertEquals(expected,
                    ClientboundWarheadClientControlPayload.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void unknownActionIdIsRejected() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeVarInt(999);
            buffer.writeFloat(0.0F);
            buffer.readerIndex(0);

            assertThrows(IllegalArgumentException.class,
                () -> ClientboundWarheadClientControlPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
