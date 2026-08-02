package com.andye.warmod.silo;

import com.andye.warmod.item.component.TargetCoordinates;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public record MissileSiloLaunchRequest(UUID requestId, UUID siloId, BlockPos siloCentre,
    MissileSiloLaunchTrigger trigger, @Nullable UUID triggeringPlayerId, @Nullable String triggeringPlayerName,
    TargetCoordinates target, WarheadPayloadType payloadType, long creationGameTime, Set<ChunkPos> temporaryTickets) {
}
