package com.andye.warmod.testtool;

import com.andye.warmod.acoustics.ModSoundEvents;
import java.util.Objects;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class TestExplosionService {
	private static final WeightedList<ExplosionParticleInfo> DEFAULT_BLOCK_PARTICLES = WeightedList.<ExplosionParticleInfo>builder()
		.add(new ExplosionParticleInfo(ParticleTypes.POOF, 0.5F, 1.0F))
		.add(new ExplosionParticleInfo(ParticleTypes.SMOKE, 1.0F, 1.0F))
		.build();

	private TestExplosionService() {
	}

	public static void createExplosion(final ServerLevel level, final ServerPlayer sourcePlayer, final Vec3 position) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(sourcePlayer, "sourcePlayer");
		Objects.requireNonNull(position, "position");
		if (!position.isFinite()) {
			throw new IllegalArgumentException("position must be finite");
		}

		level.explode(
			sourcePlayer,
			Explosion.getDefaultDamageSource(level, sourcePlayer),
			null,
			position.x,
			position.y,
			position.z,
			6.0F,
			false,
			Level.ExplosionInteraction.TNT,
			ParticleTypes.EXPLOSION,
			ParticleTypes.EXPLOSION_EMITTER,
			DEFAULT_BLOCK_PARTICLES,
			BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSoundEvents.SILENT)
		);
	}
}
