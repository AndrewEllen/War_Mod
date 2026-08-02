package com.andye.warmod.silo;

import com.andye.warmod.warhead.WarheadImpactService;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MissileSiloDetonationService {
    private MissileSiloDetonationService() {
    }

    public static void detonateAt(final ServerLevel level, final @Nullable UUID ownerId, final UUID payloadId,
        final UUID radarRootId, final Vec3 impactPosition, final long visualSeed,
        final WarheadPayloadType payloadType) {
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null && owner.level() != level) owner = null;
        WarheadImpactService.detonateAt(level, owner, payloadId, radarRootId, impactPosition, visualSeed, payloadType);
    }
}
