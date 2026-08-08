package com.andye.warmod.mixin.client;

import com.andye.warmod.compat.DistantHorizonsCompat;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererCleanupMixin {
    @Inject(method = "close", at = @At("RETURN"))
    private void warmod$closeCompatibilityRenderResources(final CallbackInfo ci) {
        DistantHorizonsCompat.close();
    }
}
