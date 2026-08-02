package com.andye.warmod.silo;

import com.andye.warmod.WarMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;

public final class MissileSiloChunkTicketType {
    public static final TicketType SILO = Registry.register(BuiltInRegistries.TICKET_TYPE,
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "missile_silo"),
        new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));

    private MissileSiloChunkTicketType() {
    }

    public static void register() {
    }
}
