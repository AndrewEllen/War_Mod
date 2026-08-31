package com.andye.warmod.fire.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.network.ClientboundFireStatePayload;
import com.andye.warmod.fire.network.FireVisualBand;
import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class ClientFireVisualPageTransactionTest {
    @Test
    void omittedPageCannotBecomeACompleteRemovalSnapshot() {
        ClientboundFireStatePayload first = page(0, cell(10L), List.of(90L));
        ClientboundFireStatePayload last = page(2, cell(12L), List.of(92L));
        ClientFireVisualManager.PendingPageTransaction transaction =
            new ClientFireVisualManager.PendingPageTransaction(first);

        transaction.accept(first);
        transaction.accept(last);
        transaction.accept(first); // A duplicated page cannot fake completion.

        assertFalse(transaction.complete());
        assertFalse(transaction.matches(new ClientboundFireStatePayload(50L, 8L,
            99L, 1, 3, FireVisualBand.COMPLETE_MASK, List.of(cell(11L)),
            List.of(), false, List.of())));
    }

    @Test
    void outOfOrderPagesMergeExactlyOnceAfterTheTransactionIsComplete() {
        ClientboundFireStatePayload first = page(0, cell(10L), List.of(90L));
        ClientFireVisualManager.PendingPageTransaction transaction =
            new ClientFireVisualManager.PendingPageTransaction(first);
        transaction.accept(page(2, cell(12L), List.of(92L)));
        transaction.accept(first);
        assertFalse(transaction.complete());

        transaction.accept(page(1, cell(11L), List.of(91L)));

        assertTrue(transaction.complete());
        ClientboundFireStatePayload merged = transaction.merge();
        assertEquals(List.of(10L, 11L, 12L), merged.cells().stream()
            .map(ClientboundFireStatePayload.CellEntry::id).toList());
        assertEquals(List.of(90L, 91L, 92L), merged.removedCellIds());
        assertEquals(FireVisualBand.COMPLETE_MASK, merged.completeBandMask());
        assertEquals(1, merged.pageCount());
        assertEquals(0, merged.pageIndex());
    }

    private static ClientboundFireStatePayload page(final int pageIndex,
        final ClientboundFireStatePayload.CellEntry cell, final List<Long> removed) {
        return new ClientboundFireStatePayload(50L, 7L, 70L, pageIndex, 3,
            FireVisualBand.COMPLETE_MASK, List.of(cell), removed,
            pageIndex == 0, List.of());
    }

    private static ClientboundFireStatePayload.CellEntry cell(final long id) {
        return new ClientboundFireStatePayload.CellEntry(id, id + 1L,
            (byte)FireVisualBand.PATCH.wireId(), 1, (int)id, 64, 0,
            id, 64.5, 0.5, 0.4F, 0.8F, 0.4F, 1L,
            0.8F, 1.2F, 0.4F, 0.9F, 0.8F, 0.7F, 0.6F,
            0.0F, 0.1F, 0.0F, 1, id * 31L,
            (byte)Direction.UP.ordinal(), (byte)FirePhase.FLAMING.ordinal(), 4L);
    }
}
