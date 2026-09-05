package com.andye.warmod.fire.client.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class FireWorldRendererVisibilityTest {
    @Test
    void terrainBoundaryUsesNegotiatedChunkDistanceRatherThanProjectionZoom() {
        assertTrue(FireWorldRenderer.supportWithinRenderDistance(0, 0, 255, 0, 0, 15));
        assertFalse(FireWorldRenderer.supportWithinRenderDistance(0, 0, 257, 0, 0, 15));
    }

    @Test
    void nearbyParentBoundsRemainEligibleAtTheTerrainEdge() {
        // An aggregate whose occupied support reaches the final rendered chunk
        // must not disappear merely because its centroid lies just outside it.
        assertTrue(FireWorldRenderer.supportWithinRenderDistance(0, 0, 264, 0, 8, 15));
        assertFalse(FireWorldRenderer.supportWithinRenderDistance(0, 0, 266, 0, 8, 15));
    }
}
