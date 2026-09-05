package com.andye.warmod.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class FireVisualStateIndexTest {
    @Test
    void incrementalQueryRetainsDormantSmokeUntilExplicitRemoval() {
        FireVisualStateIndex index = new FireVisualStateIndex();
        FireCellSnapshot source = new FireCellSnapshot(9L,
            FireSurfaceAnchor.center(new BlockPos(8, 64, 8), Direction.UP),
            0.9F, 0.8F, 0.7F, 0.75F, FirePhase.FLAMING,
            33L, 0L, new Vec3(0.1, 0.0, 0.0));
        index.upsert(source);
        assertEquals(1, index.size());
        assertEquals(source, index.query(new Vec3(8, 64, 8), 32.0, 10L).getFirst());

        index.markDormant(9L, 10L, 110L);
        FireCellSnapshot fading = index.query(new Vec3(8, 64, 8), 32.0, 60L)
            .getFirst();
        assertTrue(fading.intensity() < source.intensity());
        assertTrue(fading.smoke() > 0.0F);

        index.remove(9L);
        assertTrue(index.query(new Vec3(8, 64, 8), 32.0, 80L).isEmpty());
    }
}
