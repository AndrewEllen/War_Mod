package com.andye.warmod.mixin.client;

import com.andye.warmod.diagnostics.client.ClientPerformanceTelemetry;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Separates vanilla particle extraction/counts from War Mod's GPU VFX path. */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineTelemetryMixin {
    @Shadow @Final private Map<ParticleRenderType, ParticleGroup<?>> particles;
    @Unique private long warMod$extractionStarted;

    @Inject(method = "extract", at = @At("HEAD"))
    private void warMod$beginExtraction(final ParticlesRenderState renderState,
        final Frustum frustum, final Camera camera, final float partialTick,
        final CallbackInfo callback) {
        warMod$extractionStarted = System.nanoTime();
    }

    @Inject(method = "extract", at = @At("RETURN"))
    private void warMod$finishExtraction(final ParticlesRenderState renderState,
        final Frustum frustum, final Camera camera, final float partialTick,
        final CallbackInfo callback) {
        long count = 0L;
        for (ParticleGroup<?> group : particles.values()) count += group.size();
        ClientPerformanceTelemetry.recordVanillaParticleExtractionNanos(
            Math.max(0L, System.nanoTime() - warMod$extractionStarted), count);
    }
}
