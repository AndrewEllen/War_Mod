package com.andye.warmod.mixin.client;

import com.andye.warmod.firearm.client.ClientFirearmInput;
import com.andye.warmod.rocket.client.ClientRocketLauncherInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftFirearmInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void warMod$startFirearmAttack(final CallbackInfoReturnable<Boolean> callback) {
        if (ClientRocketLauncherInput.trigger() || ClientFirearmInput.trigger(false))
            callback.setReturnValue(true);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void warMod$continueFirearmAttack(final boolean attacking,
        final CallbackInfo callback) {
        if (attacking && (ClientRocketLauncherInput.trigger()
            || ClientFirearmInput.trigger(true))) callback.cancel();
    }
}
