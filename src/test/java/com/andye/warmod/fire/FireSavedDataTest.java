package com.andye.warmod.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class FireSavedDataTest {
    @Test
    void surfaceDeadlineBudgetAndLockRoundTripThroughSavedCodec() {
        FireSavedData.Entry entry = new FireSavedData.Entry(new BlockPos(4, 70, -9),
            Direction.UP.ordinal(), 0.5F, 1.0F, 0.5F, 0.0F,
            FirePhase.DECAYING.ordinal(), 0.14F, 0.42F, 0.0F, 260,
            99L, 1_000L, 12L, true, false, 1_325L, 1_240L, 7.5F, true);

        var encoded = FireSavedData.Entry.CODEC.encodeStart(JsonOps.INSTANCE, entry)
            .getOrThrow();
        FireSavedData.Entry decoded = FireSavedData.Entry.CODEC
            .parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(entry, decoded);
        assertEquals(1_325L, decoded.hardBurnEndTick());
        assertEquals(7.5F, decoded.remainingExternalHeatBudget());
        assertTrue(decoded.surfaceBurnLocked());
    }
}
