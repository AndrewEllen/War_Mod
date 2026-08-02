package com.andye.warmod.radar;

import com.andye.warmod.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class RadarAccess {
	private RadarAccess() { }
	public static boolean hasRadarAccess(final ServerPlayer player) {
		return player != null && (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || player.getInventory().contains(ModItems.RADAR.getDefaultInstance())
			|| player.getMainHandItem().is(ModItems.RADAR) || player.getOffhandItem().is(ModItems.RADAR));
	}
}
