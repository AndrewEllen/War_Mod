package com.andye.warmod.mixin.client;

import com.andye.warmod.firearm.FirearmType;
import com.andye.warmod.item.FirearmItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class FirearmAimFovMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void warMod$aimDownFirearmSights(final float partialTick,
        final CallbackInfoReturnable<Float> callback) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem()
            || !(player.getUseItem().getItem() instanceof FirearmItem firearm)) return;
        FirearmType type = firearm.firearmType();
        if (type.scoped()) return;
        callback.setReturnValue(callback.getReturnValue()
            * (type == FirearmType.PISTOL ? 0.82F : 0.68F));
    }
}
