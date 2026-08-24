package com.andye.warmod.firearm.client;

import com.andye.warmod.firearm.FirearmType;
import com.andye.warmod.firearm.network.ServerboundFirearmTriggerPayload;
import com.andye.warmod.item.FirearmItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Converts the vanilla attack key into an authoritative firearm trigger. */
public final class ClientFirearmInput {
    private static long lastTriggerTick = Long.MIN_VALUE / 2;
    private static boolean scopedAttackDown;

    private ClientFirearmInput() { }

    public static boolean trigger(final boolean held) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
            || !(minecraft.player.getMainHandItem().getItem() instanceof FirearmItem firearm))
            return false;
        FirearmType type = firearm.firearmType();
        if (!held || type.automatic()) {
            long tick = minecraft.player.tickCount;
            if (tick != lastTriggerTick) {
                lastTriggerTick = tick;
                ServerboundFirearmTriggerPayload payload =
                    new ServerboundFirearmTriggerPayload(true);
                if (ClientPlayNetworking.canSend(payload.type()))
                    ClientPlayNetworking.send(payload);
            }
        }
        if (minecraft.gameMode != null) minecraft.gameMode.stopDestroyBlock();
        return true;
    }

    /** Vanilla consumes the attack key while an item is actively being used.
     * Poll only that scoped-use case; ordinary firearm attacks stay on the mixin path. */
    public static void tick(final Minecraft minecraft) {
        boolean scopedUse = minecraft.player != null
            && minecraft.player.isUsingItem()
            && minecraft.player.getMainHandItem().getItem() instanceof FirearmItem firearm
            && firearm.firearmType().scoped()
            && minecraft.player.getUseItem() == minecraft.player.getMainHandItem();
        boolean attackDown = scopedUse && minecraft.options.keyAttack.isDown();
        if (attackDown) trigger(scopedAttackDown);
        scopedAttackDown = attackDown;
    }
}
