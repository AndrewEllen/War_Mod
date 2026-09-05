package com.andye.warmod.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

/** Per-stack configuration for the custom fire placement tool. */
public record FireDebugConfig(float intensity, int size) {
    public static final float MIN_INTENSITY = 0.10F;
    public static final float MAX_INTENSITY = 1.00F;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 12;
    public static final FireDebugConfig DEFAULT = new FireDebugConfig(0.45F, 1);

    public static final Codec<FireDebugConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.FLOAT.fieldOf("intensity").forGetter(FireDebugConfig::intensity),
            Codec.INT.fieldOf("size").forGetter(FireDebugConfig::size)
        ).apply(instance, FireDebugConfig::new));

    public FireDebugConfig {
        intensity = Mth.clamp(Float.isFinite(intensity) ? intensity : DEFAULT.intensity,
            MIN_INTENSITY, MAX_INTENSITY);
        size = Mth.clamp(size, MIN_SIZE, MAX_SIZE);
    }

    public FireDebugConfig withIntensity(final float value) {
        return new FireDebugConfig(value, size);
    }

    public FireDebugConfig withSize(final int value) {
        return new FireDebugConfig(intensity, value);
    }

    public String summary() {
        return Math.round(intensity * 100.0F) + "% intensity | Size " + size;
    }
}
