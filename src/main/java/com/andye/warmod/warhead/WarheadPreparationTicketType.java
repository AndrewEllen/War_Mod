package com.andye.warmod.warhead;

import com.andye.warmod.WarMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;

/** Full-chunk loading without entity or block simulation. */
public final class WarheadPreparationTicketType {
    public static final TicketType WARHEAD_PREPARATION = Registry.register(
        BuiltInRegistries.TICKET_TYPE,
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_preparation"),
        new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING)
    );

    private WarheadPreparationTicketType() { }

    public static void register() { }
}
