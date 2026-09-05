package com.andye.warmod.block.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LaunchControllerRedstonePolicyTest {
    @Test
    void onlyUnpoweredToPoweredTransitionTriggers() {
        assertFalse(LaunchControllerBlockEntity.isRisingEdge(0, 0));
        assertTrue(LaunchControllerBlockEntity.isRisingEdge(0, 1));
        assertTrue(LaunchControllerBlockEntity.isRisingEdge(0, 15));
        assertFalse(LaunchControllerBlockEntity.isRisingEdge(1, 1));
        assertFalse(LaunchControllerBlockEntity.isRisingEdge(4, 15));
        assertFalse(LaunchControllerBlockEntity.isRisingEdge(15, 0));
    }
}
