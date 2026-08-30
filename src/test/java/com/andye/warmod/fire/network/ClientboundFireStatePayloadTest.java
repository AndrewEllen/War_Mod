package com.andye.warmod.fire.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.fire.FirePhase;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class ClientboundFireStatePayloadTest {
    @Test
    void completeRepresentationRoundTripsThroughCodec() {
        ClientboundFireStatePayload payload = new ClientboundFireStatePayload(42L, 7L,
            FireVisualBand.COMPLETE_MASK,
            List.of(new ClientboundFireStatePayload.CellEntry(1L, 99L,
                (byte) FireVisualBand.LOCAL.wireId(), 4, 2, 3, 4,
                9.5, 13.0, 17.5, 1.5F, 2.0F, 1.5F,
                0x0000_0000_0000_00C3L, 4.5F, 6.25F, 0.9F, 0.7F, 8.0F,
                0.6F, 0.1F, 0.0F, -0.1F, 7, 4L,
                (byte) Direction.UP.ordinal(), (byte) FirePhase.FLAMING.ordinal(), 3L)),
            List.of(91L, 92L),
            true, List.of(new ClientboundFireStatePayload.EmberEntry(5L,
                1.0, 2.0, 3.0, 0.1F, 0.2F, 0.3F, 0.0F, 0.1F, 0.0F,
                0.7F, 6L, 40L, 80)));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ClientboundFireStatePayload.STREAM_CODEC.encode(buffer, payload);
            buffer.readerIndex(0);
            ClientboundFireStatePayload decoded =
                ClientboundFireStatePayload.STREAM_CODEC.decode(buffer);

            assertEquals(payload, decoded);
            assertEquals(FireVisualBand.COMPLETE_MASK, decoded.completeBandMask());
            assertEquals(0x0000_0000_0000_00C3L,
                decoded.cells().getFirst().occupancyMask());
            assertEquals(FireVisualBand.LOCAL,
                decoded.cells().getFirst().toCell().band());
            assertEquals(99L, decoded.cells().getFirst().toCell().parentId());
            assertEquals(List.of(91L, 92L), decoded.removedCellIds());
            assertTrue(decoded.isWellFormed());
        } finally {
            buffer.release();
        }
    }
}
