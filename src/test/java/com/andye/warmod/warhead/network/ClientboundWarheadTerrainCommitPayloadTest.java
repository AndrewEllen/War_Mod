package com.andye.warmod.warhead.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class ClientboundWarheadTerrainCommitPayloadTest {
    @Test
    void trackedChunkCompletionMarkerRoundTripsAllDiagnosticCounts() {
        ClientboundWarheadTerrainCommitPayload expected =
            new ClientboundWarheadTerrainCommitPayload(UUID.randomUUID(), 3L,
                21, 18, 47, 12_450, 318, 9_200L);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ClientboundWarheadTerrainCommitPayload.STREAM_CODEC.encode(buffer, expected);
            buffer.readerIndex(0);
            ClientboundWarheadTerrainCommitPayload decoded =
                ClientboundWarheadTerrainCommitPayload.STREAM_CODEC.decode(buffer);
            assertEquals(expected, decoded);
            assertTrue(decoded.isWellFormed());
        } finally {
            buffer.release();
        }
    }

    @Test
    void negativeMutationCountsAreRejected() {
        assertFalse(new ClientboundWarheadTerrainCommitPayload(UUID.randomUUID(),
            1L, 1, 1, 1, -1, 0, 0L).isWellFormed());
    }
}
