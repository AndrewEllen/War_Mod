package com.andye.warmod.warhead.client.render;

/**
 * Compatibility registration point retained for older client startup code.
 *
 * <p>The former implementation spawned vanilla EXPLOSION_EMITTER particles on
 * END_CLIENT_TICK. Those particles lived outside War Mod's renderer, ignored
 * effect/frustum/overlap culling and multiplied linearly during bombardments.
 * The same explosion artwork is already submitted by
 * {@link GroundDustFrontRenderer#renderExplosionFlecks} through the custom
 * world renderer, so no separate vanilla emitter is required.</p>
 */
public final class ShockwaveVanillaParticleEmitter {
    private static boolean registered;

    private ShockwaveVanillaParticleEmitter() { }

    public static void register() {
        if (registered) return;
        registered = true;
    }
}
