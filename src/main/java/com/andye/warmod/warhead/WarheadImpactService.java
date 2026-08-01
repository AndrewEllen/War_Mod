package com.andye.warmod.warhead;

import com.andye.warmod.acoustics.AcousticEngine;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.testtool.TestExplosionService;
import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class WarheadImpactService {
	private WarheadImpactService() {
	}

	public static void impact(
		final ServerLevel level,
		final ServerPlayer owner,
		final UUID warheadId,
		final Vec3 impactPosition,
		final long visualSeed
	) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(warheadId, "warheadId");
		Objects.requireNonNull(impactPosition, "impactPosition");
		if (!impactPosition.isFinite()) {
			throw new IllegalArgumentException("impactPosition must be finite");
		}

		WarheadVisualNetworking.sendImpact(
			level,
			new ClientboundWarheadImpactPayload(
				warheadId,
				impactPosition.x,
				impactPosition.y,
				impactPosition.z,
				level.getGameTime(),
				visualSeed,
				1.0F
			),
			impactPosition
		);
		TestExplosionService.createExplosion(level, owner, impactPosition);
		AcousticEngine.playSound(
			level,
			impactPosition,
			AcousticSounds.LARGE_EXPLOSION_ID,
			SoundSource.BLOCKS,
			1.0F,
			1.0F
		);
	}
}