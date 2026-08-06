package com.andye.warmod.warhead.client;

import java.util.Collection;
import net.minecraft.client.Minecraft;

/**
 * Legacy vanilla-particle emitter retained only as a binary/source compatibility
 * shell. Stage 8 v2 routes every explosion visual through the packed custom
 * renderer, so this class deliberately emits nothing. This prevents the old
 * FLAME/LAVA/EXPLOSION and terrain-coloured block-particle layers from being
 * drawn over the new single-mask particle field.
 */
@Deprecated(forRemoval = true)
public final class ImpactParticleEmitter {
    public void emit(final Minecraft client, final Collection<ImpactVisualState> impacts,
        final long gameTime, final int particlesAlreadySpawned) {
        // Intentionally empty: custom packed rendering is the sole explosion path.
    }

    public void clear() {
        // No legacy particle budget remains to clear.
    }
}
