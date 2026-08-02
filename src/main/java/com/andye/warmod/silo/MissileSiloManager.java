package com.andye.warmod.silo;

import com.andye.warmod.WarMod;
import com.andye.warmod.block.MissileSiloBlock;
import com.andye.warmod.block.MissileSiloStructure;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;

public final class MissileSiloManager {
    private static final Set<ServerLevel> RESTORED = java.util.Collections.synchronizedSet(new HashSet<>());
    private static boolean registered;

    private MissileSiloManager() {
    }

    public static void registerLifecycle() {
        if (registered) return;
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (RESTORED.add(level)) MissileSiloChunkTicketManager.restoreSavedSilos(level);
            if ((level.getGameTime() & 63L) == 0L) MissileSiloChunkTicketManager.validateLoadedSilos(level);
            MissileSiloLaunchService.tick(level);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> MissileSiloLaunchService.stop());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            MissileSiloChunkTicketManager.clearRuntimeState();
            RESTORED.clear();
        });
        registered = true;
    }

    public static boolean canPlace(final ServerLevel level) {
        return MissileSiloSavedData.get(level).size() < MissileSiloConstants.MAX_FORCE_LOADED_SILOS_PER_LEVEL;
    }

    public static boolean register(final ServerLevel level, final MissileSiloBlockEntity silo) {
        if (!MissileSiloChunkTicketManager.registerSilo(level, silo.siloId(), silo.getBlockPos(), silo.facing())) return false;
        UUID owner = silo.ownerPlayerId() == null ? new UUID(0L, 0L) : silo.ownerPlayerId();
        MissileSiloSavedData.get(level).put(new MissileSiloRecord(silo.siloId(), silo.getBlockPos(), owner,
            silo.ownerDisplayName()));
        if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("Silo {} placed at {}, footprintChunks={}",
            silo.siloId(), silo.getBlockPos(), MissileSiloStructure.footprintChunks(silo.getBlockPos(), silo.facing()).size());
        return true;
    }

    public static void unregister(final ServerLevel level, final MissileSiloBlockEntity silo) {
        MissileSiloLaunchService.cancel(level, silo.siloId(), "silo removed");
        MissileSiloChunkTicketManager.unregisterSilo(level, silo.siloId());
        MissileSiloSavedData.get(level).remove(silo.siloId());
        if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("Silo {} removed, tickets released", silo.siloId());
    }
}
