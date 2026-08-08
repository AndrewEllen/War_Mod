package com.andye.warmod.mixin.client;

import com.andye.warmod.WarMod;
import com.andye.warmod.compat.iris.IrisParticleQuadCollector;
import com.andye.warmod.icbm.client.render.IcbmRenderPipelines;
import com.andye.warmod.warhead.client.render.IrisShaderState;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes War Mod's billboard-only visual passes through Minecraft's real 26.2
 * particle submission path while an Iris shader pack is active.
 */
@Mixin(SubmitNodeStorage.class)
public abstract class IrisParticleSubmitMixin {
    private static final SingleQuadParticle.Layer PARTICLE_MASK = layer(
        Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_2.png"));
    private static final SingleQuadParticle.Layer EXPLOSION_MASK = layer(
        Identifier.fromNamespaceAndPath("minecraft", "textures/particle/explosion_0.png"));
    private static final SingleQuadParticle.Layer ICBM_SMOKE = layer(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/effect/icbm_smoke.png"));
    private static boolean warnedUnsupportedGeometry;

    @Inject(method = "submitCustomGeometry", at = @At("HEAD"), cancellable = true)
    private void warMod$routeIrisBillboards(final PoseStack poseStack,
        final RenderType renderType,
        final SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer,
        final CallbackInfo ci) {
        if (!IrisShaderState.active()) return;
        SingleQuadParticle.Layer layer = particleLayer(renderType);
        if (layer == null) return;

        QuadParticleRenderState particles = new QuadParticleRenderState();
        IrisParticleQuadCollector collector = new IrisParticleQuadCollector(particles, layer);
        customGeometryRenderer.render(poseStack.last(), collector);
        if (!collector.finish()) {
            if (!warnedUnsupportedGeometry) {
                warnedUnsupportedGeometry = true;
                WarMod.LOGGER.warn(
                    "Iris particle bridge received non-billboard geometry; retaining the custom-geometry fallback");
            }
            return;
        }

        if (!particles.isEmpty()) {
            ((SubmitNodeStorage) (Object) this).submitQuadParticleGroup(particles);
        }
        ci.cancel();
    }

    private static SingleQuadParticle.Layer particleLayer(final RenderType renderType) {
        if (renderType == WarheadRenderPipelines.GROUND_DUST
            || renderType == WarheadRenderPipelines.HEAVY_SMOKE
            || renderType == WarheadRenderPipelines.HEAVY_SMOKE_CORE
            || renderType == WarheadRenderPipelines.NUCLEAR_SMOKE
            || renderType == WarheadRenderPipelines.FIREBALL_CORE
            || renderType == WarheadRenderPipelines.FIREBALL_HOT
            || renderType == WarheadRenderPipelines.FIREBALL_COOL) {
            return PARTICLE_MASK;
        }
        if (renderType == WarheadRenderPipelines.EXPLOSION_PUFF
            || renderType == WarheadRenderPipelines.NUCLEAR_FLASH) {
            return EXPLOSION_MASK;
        }
        if (renderType == IcbmRenderPipelines.SMOKE) return ICBM_SMOKE;
        return null;
    }

    private static SingleQuadParticle.Layer layer(final Identifier texture) {
        return new SingleQuadParticle.Layer(true, texture, RenderPipelines.TRANSLUCENT_PARTICLE);
    }
}
