package com.andye.warmod.mixin.client;

import com.andye.warmod.item.ModItems;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class SniperScopeMixin {
    @Inject(method = "isScoping", at = @At("HEAD"), cancellable = true)
    private void warMod$sniperScope(final CallbackInfoReturnable<Boolean> callback) {
        Player player = (Player) (Object) this;
        if (player.isUsingItem() && player.getUseItem().is(ModItems.SNIPER_RIFLE))
            callback.setReturnValue(true);
    }
}
