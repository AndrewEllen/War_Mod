package com.andye.warmod.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

final class FireEmberSavedDataTest {
    @Test
    void emberLineageDeadlineRoundTripsAndLegacyEntriesDefaultSafely() {
        FireEmberSavedData.Entry entry = new FireEmberSavedData.Entry(
            1.0, 70.0, -2.0, 0.1, 0.2, -0.1, 0.7F, 42L, 160, 15_400L);
        var encoded = FireEmberSavedData.Entry.CODEC.encodeStart(JsonOps.INSTANCE, entry)
            .getOrThrow();
        assertEquals(entry, FireEmberSavedData.Entry.CODEC
            .parse(JsonOps.INSTANCE, encoded).getOrThrow());

        var legacy = encoded.getAsJsonObject().deepCopy();
        legacy.remove("root_expiry_tick");
        assertEquals(Long.MAX_VALUE, FireEmberSavedData.Entry.CODEC
            .parse(JsonOps.INSTANCE, legacy).getOrThrow().rootExpiryTick());
    }
}
