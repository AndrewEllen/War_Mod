package com.andye.warmod.firearm.client;

import com.andye.warmod.firearm.FirearmType;
import com.andye.warmod.firearm.network.ServerboundFirearmTriggerPayload;
import com.andye.warmod.item.FirearmItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Converts the vanilla attack key into an authoritative firearm trigger. */
public final class ClientFirearmInput {
    private static long lastAutomaticTick = Long.MIN_VALUE / 2;

    private ClientFirearmInput() { }

    public static boolean trigger(final boolean held) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
            || !(minecraft.player.getMainHandItem().getItem() instanceof FirearmItem firearm))
            return false;
        FirearmType type = firearm.firearmType();
        if (!held || type.automatic()) {
            long tick = minecraft.player.tickCount;
            if (!held || tick != lastAutomaticTick) {
                lastAutomaticTick = tick;
                ServerboundFirearmTriggerPayload payload =
                    new ServerboundFirearmTriggerPayload(true);
                if (ClientPlayNetworking.canSend(payload.type()))
                    ClientPlayNetworking.send(payload);
            }
        }
        if (minecraft.gameMode != null) minecraft.gameMode.stopDestroyBlock();
        return true;
    }
}
