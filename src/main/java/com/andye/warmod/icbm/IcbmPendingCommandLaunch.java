package com.andye.warmod.icbm;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadYield;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record IcbmPendingCommandLaunch(
	UUID requestId,
	UUID playerId,
	ResourceKey<Level> dimension,
	Vec3 target,
	@Nullable Vec3 requestedLaunch,
	Vec3 launchPosition,
	WarheadPayloadType payloadType,
	WarheadYield yield,
	WarheadDeliveryMode deliveryMode,
	boolean customFire,
	long creationGameTime,
	long visualSeed,
	Set<ChunkPos> temporaryTickets
) {
	public IcbmPendingCommandLaunch {
		temporaryTickets = Set.copyOf(temporaryTickets);
	}
}
