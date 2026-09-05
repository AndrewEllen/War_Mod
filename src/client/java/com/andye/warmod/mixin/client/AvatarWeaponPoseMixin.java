package com.andye.warmod.mixin.client;

import com.andye.warmod.item.FirearmItem;
import com.andye.warmod.item.RocketLauncherItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps long guns shouldered instead of hanging from the player's lowered arm. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarWeaponPoseMixin {
    @Inject(
        method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void warMod$shoulderWeapons(final Avatar avatar,
        final ItemStack itemInHand, final InteractionHand hand,
        final CallbackInfoReturnable<HumanoidModel.ArmPose> callback) {
        if (itemInHand.getItem() instanceof FirearmItem
            || itemInHand.getItem() instanceof RocketLauncherItem) {
            callback.setReturnValue(HumanoidModel.ArmPose.BOW_AND_ARROW);
        }
    }
}
