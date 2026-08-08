package com.andye.warmod.mixin.client;

import com.andye.warmod.WarMod;
import com.andye.warmod.compat.iris.IrisParticleQuadCollector;
import com.andye.warmod.icbm.client.render.IcbmRenderPipelines;
import com.andye.warmod.warhead.client.render.IrisShaderState;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
    private static final Identifier PARTICLE_MASK = Identifier.fromNamespaceAndPath(
        "minecraft", "particle/big_smoke_2");
    private static final Identifier EXPLOSION_MASK = Identifier.fromNamespaceAndPath(
        "minecraft", "particle/explosion_0");
    private static boolean warnedUnsupportedGeometry;
    private static boolean warnedMissingSprite;

    @Inject(method = "submitCustomGeometry", at = @At("HEAD"), cancellable = true)
    private void warMod$routeIrisBillboards(final PoseStack poseStack,
        final RenderType renderType,
        final SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer,
        final CallbackInfo ci) {
        if (!IrisShaderState.active()) return;
        TextureAtlasSprite sprite = particleSprite(renderType);
        if (sprite == null) return;

        QuadParticleRenderState particles = new QuadParticleRenderState();
        IrisParticleQuadCollector collector = new IrisParticleQuadCollector(particles, sprite);
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

    private static TextureAtlasSprite particleSprite(final RenderType renderType) {
        Identifier spriteId;
        if (renderType == WarheadRenderPipelines.GROUND_DUST
            || renderType == WarheadRenderPipelines.HEAVY_SMOKE
            || renderType == WarheadRenderPipelines.HEAVY_SMOKE_CORE
            || renderType == WarheadRenderPipelines.NUCLEAR_SMOKE
            || renderType == WarheadRenderPipelines.FIREBALL_CORE
            || renderType == WarheadRenderPipelines.FIREBALL_HOT
            || renderType == WarheadRenderPipelines.FIREBALL_COOL
            || renderType == IcbmRenderPipelines.SMOKE) {
            spriteId = PARTICLE_MASK;
        } else if (renderType == WarheadRenderPipelines.EXPLOSION_PUFF
            || renderType == WarheadRenderPipelines.NUCLEAR_FLASH) {
            spriteId = EXPLOSION_MASK;
        } else {
            return null;
        }

        try {
            TextureAtlas atlas = Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(TextureAtlas.LOCATION_PARTICLES);
            TextureAtlasSprite sprite = atlas.getSprite(spriteId);
            if (sprite == atlas.missingSprite()) {
                warnMissingSprite(spriteId);
                return null;
            }
            return sprite;
        } catch (RuntimeException exception) {
            if (!warnedMissingSprite) {
                warnedMissingSprite = true;
                WarMod.LOGGER.warn(
                    "Iris particle bridge could not resolve the Minecraft particle atlas; retaining custom geometry",
                    exception);
            }
            return null;
        }
    }

    private static void warnMissingSprite(final Identifier spriteId) {
        if (warnedMissingSprite) return;
        warnedMissingSprite = true;
        WarMod.LOGGER.warn(
            "Iris particle bridge could not resolve particle sprite {}; retaining custom geometry",
            spriteId);
    }
}
