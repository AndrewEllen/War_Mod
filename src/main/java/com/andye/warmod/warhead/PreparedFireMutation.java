package com.andye.warmod.warhead;

public record PreparedFireMutation(int x, int y, int z, boolean crater,
    boolean tree, boolean customFire, float intensity, long seed) { }
