package com.andye.warmod.mixin.client;

import com.andye.warmod.warhead.client.ClientWarheadVisualManager;
import com.andye.warmod.warhead.client.ImpactVisualState;
import com.andye.warmod.warhead.client.WarheadVisualTiming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps terrain shockfront sampling ahead of the accelerated nuclear visual front. */
@Mixin(ClientWarheadVisualManager.class)
public abstract class WarheadTerrainVisualTimingMixin {
    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/andye/warmod/warhead/client/ImpactVisualState;ageTicks(JD)D"
        )
    )
    private double warMod$terrainVisualAge(final ImpactVisualState state,
        final long gameTime, final double partialTick) {
        double physicalAge = state.ageTicks(gameTime, partialTick);
        return WarheadVisualTiming.age(state.payloadType(), physicalAge);
    }
}
