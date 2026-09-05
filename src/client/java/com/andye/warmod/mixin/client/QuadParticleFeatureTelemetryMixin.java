package com.andye.warmod.mixin.client;

import com.andye.warmod.diagnostics.client.ClientPerformanceTelemetry;
import java.util.List;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measures vanilla quad-particle render submission independently of GPU VFX. */
@Mixin(QuadParticleFeatureRenderer.class)
abstract class QuadParticleFeatureTelemetryMixin {
    @Unique private long warMod$renderStarted;

    @Inject(method = "executeGroup", at = @At("HEAD"))
    private void warMod$beginRender(final FeatureFrameContext context, final int group,
        final List<QuadParticleFeatureRenderer.Submit> submits, final boolean translucent,
        final CallbackInfo callback) {
        warMod$renderStarted = System.nanoTime();
    }

    @Inject(method = "executeGroup", at = @At("RETURN"))
    private void warMod$finishRender(final FeatureFrameContext context, final int group,
        final List<QuadParticleFeatureRenderer.Submit> submits, final boolean translucent,
        final CallbackInfo callback) {
        ClientPerformanceTelemetry.recordVanillaParticleRenderNanos(
            Math.max(0L, System.nanoTime() - warMod$renderStarted));
    }
}
