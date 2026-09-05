package com.andye.warmod.rocket.client;

import com.andye.warmod.WarMod;
import com.andye.warmod.item.RocketLauncherItem;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** A compact aiming ring that appears only while the launcher is held to aim. */
public final class RocketLauncherReticle {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(
        WarMod.MOD_ID, "rocket_launcher_reticle");
    private static boolean registered;

    private RocketLauncherReticle() { }

    public static void register() {
        if (registered) return;
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, ID,
            (graphics, deltaTracker) -> {
                var player = Minecraft.getInstance().player;
                if (player == null || !player.isUsingItem()
                    || !(player.getUseItem().getItem() instanceof RocketLauncherItem)) return;
                int x = graphics.guiWidth() / 2;
                int y = graphics.guiHeight() / 2;
                int color = 0xffd9e7d4;
                graphics.outline(x - 9, y - 9, 19, 19, color);
                graphics.fill(x - 1, y - 13, x + 1, y - 7, color);
                graphics.fill(x - 1, y + 8, x + 1, y + 14, color);
                graphics.fill(x - 13, y - 1, x - 7, y + 1, color);
                graphics.fill(x + 8, y - 1, x + 14, y + 1, color);
            });
        registered = true;
    }
}
