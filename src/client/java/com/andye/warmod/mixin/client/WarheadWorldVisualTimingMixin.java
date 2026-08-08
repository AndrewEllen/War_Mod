package com.andye.warmod.mixin.client;

import com.andye.warmod.warhead.client.ImpactVisualState;
import com.andye.warmod.warhead.client.WarheadVisualTiming;
import com.andye.warmod.warhead.client.render.WarheadWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies the faster nuclear clock only to render extraction, not gameplay timing. */
@Mixin(WarheadWorldRenderer.class)
public abstract class WarheadWorldVisualTimingMixin {
    @Redirect(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lcom/andye/warmod/warhead/client/ImpactVisualState;ageTicks(JD)D"
        )
    )
    private static double warMod$visualImpactAge(final ImpactVisualState state,
        final long gameTime, final double partialTick) {
        double physicalAge = state.ageTicks(gameTime, partialTick);
        return WarheadVisualTiming.age(state.payloadType(), physicalAge);
    }

    /** The return-wave sound remains tied to physical propagation time. */
    @ModifyArg(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lcom/andye/warmod/warhead/client/render/WarheadWorldRenderer;playReturnWaveSound(Lnet/minecraft/client/multiplayer/ClientLevel;Lcom/andye/warmod/warhead/client/ImpactVisualState;DLnet/minecraft/world/phys/Vec3;)V"
        ),
        index = 2
    )
    private static double warMod$physicalReturnWaveSoundAge(final double visualAge) {
        return visualAge / WarheadVisualTiming.NUCLEAR_TIME_SCALE;
    }
}
