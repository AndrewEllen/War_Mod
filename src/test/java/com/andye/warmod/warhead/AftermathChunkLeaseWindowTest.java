package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class AftermathChunkLeaseWindowTest {
    private static final Vec3 CENTER = new Vec3(8.0, 64.0, 8.0);

    @Test
    void circleIntersectionKeepsAxisChunksAndRejectsSquareCorners() {
        assertTrue(AftermathChunkLeaseWindow.chunkIntersectsCircle(
            2, 0, CENTER.x, CENTER.z, 32.0));
        assertFalse(AftermathChunkLeaseWindow.chunkIntersectsCircle(
            2, 2, CENTER.x, CENTER.z, 32.0));
    }

    @Test
    void annularWindowReleasesChunksSafelyBehindCompletedRadius() {
        Set<ChunkPos> initial = AftermathChunkLeaseWindow.chunks(CENTER, 0.0, 64.0);
        Set<ChunkPos> advanced = AftermathChunkLeaseWindow.chunks(CENTER, 40.0, 80.0);

        assertTrue(initial.contains(new ChunkPos(0, 0)));
        assertFalse(advanced.contains(new ChunkPos(0, 0)));
        assertTrue(advanced.stream().allMatch(chunk ->
            AftermathChunkLeaseWindow.chunkIntersectsCircle(chunk.x(), chunk.z(),
                CENTER.x, CENTER.z, 80.0)));
    }

    @Test
    void stagedWindowIsSmallerThanLeasingTheWholeAftermath() {
        Set<ChunkPos> staged = AftermathChunkLeaseWindow.chunks(CENTER, 288.0, 384.0);
        Set<ChunkPos> whole = AftermathChunkLeaseWindow.chunks(CENTER, 0.0, 480.0);

        assertTrue(staged.size() < whole.size());
        assertTrue(staged.size() < 2_048);
    }
}
