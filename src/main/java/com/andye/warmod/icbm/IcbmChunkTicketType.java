package com.andye.warmod.icbm;

import com.andye.warmod.WarMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;

public final class IcbmChunkTicketType {
	public static final TicketType ICBM = Registry.register(
		BuiltInRegistries.TICKET_TYPE,
		Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "icbm_flight"),
		new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION)
	);

	private IcbmChunkTicketType() { }
	public static void register() { }
}