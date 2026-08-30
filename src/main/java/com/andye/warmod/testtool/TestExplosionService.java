package com.andye.warmod.testtool;

import com.andye.warmod.acoustics.ModSoundEvents;
import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.fire.wind.FireWindEngine;
import com.andye.warmod.warhead.StrategicExplosionProfiles;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadDebrisSourceSampler;
import com.andye.warmod.warhead.WarheadExplosionWorkManager;
import com.andye.warmod.warhead.WarheadFootprintCalculator;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadPreImpactPreparationManager;
import com.andye.warmod.warhead.WarheadYield;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class TestExplosionService {
	private static final int UNPREPARED_DEBRIS_CHECK_BUDGET = 1_024;
	private static final WeightedList<ExplosionParticleInfo> DEFAULT_BLOCK_PARTICLES = WeightedList.<ExplosionParticleInfo>builder()
		.add(new ExplosionParticleInfo(ParticleTypes.POOF, 0.5F, 1.0F))
		.add(new ExplosionParticleInfo(ParticleTypes.SMOKE, 1.0F, 1.0F))
		.build();

	private TestExplosionService() {
	}

	public static List<WarheadExplosionDropContext.DestroyedBlock> createExplosion(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final Vec3 position
	) {
		return createExplosion(level, source, position, WarheadConstants.EXPLOSION_STRENGTH);
	}

	public static List<WarheadExplosionDropContext.DestroyedBlock> createExplosion(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final WarheadYield yield,
		final long seed
	) {
		return createExplosion(level, source, warheadId, position, yield, seed, false);
	}

	public static List<WarheadExplosionDropContext.DestroyedBlock> createExplosion(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final WarheadYield yield,
		final long seed,
		final boolean customFire
	) {
		if (level == null || warheadId == null || position == null || yield == null) throw new NullPointerException();
		if (!position.isFinite()) throw new IllegalArgumentException("Invalid explosion arguments");
		/*
		 * Capture the struck structure before staged crater removal begins. When
		 * terminal flight already prepared this exact impact, reuse that sample;
		 * otherwise take only a bounded synchronous prefix so debris cosmetics
		 * cannot stall the authoritative detonation.
		 */
		List<WarheadExplosionDropContext.DestroyedBlock> debris = captureDebris(
			level, warheadId, position, yield, seed);

		/*
		 * This explosion is about to mutate terrain observed by other in-flight
		 * missiles. Invalidate only the affected shared cache volume and re-queue
		 * overlapping preparations; deeper/untouched observations stay reusable.
		 */
		WarheadPreImpactPreparationManager.invalidateAround(
			level, warheadId, position, yield, preparationInvalidationRadius(yield));
		long craterWorkStarted = WarModPerformanceDiagnostics.begin();
		WarheadExplosionWorkManager.detonateWithoutDebrisSample(level, source, warheadId,
			position, yield, seed, customFire);
		WarModPerformanceDiagnostics.record(
			WarModPerformanceDiagnostics.Subsystem.CRATER_WORK_CREATION,
			craterWorkStarted);
		return debris;
	}

	/** Read-only debris capture used before a prepared bulk commit mutates terrain. */
	public static List<WarheadExplosionDropContext.DestroyedBlock> captureDebris(
		final ServerLevel level, final UUID warheadId, final Vec3 position,
		final WarheadYield yield, final long seed) {
		long debrisConsumeStarted = WarModPerformanceDiagnostics.begin();
		Optional<List<WarheadExplosionDropContext.DestroyedBlock>> preparedDebris =
			WarheadPreImpactPreparationManager.consume(level, warheadId, position, yield, seed);
		WarModPerformanceDiagnostics.record(
			WarModPerformanceDiagnostics.Subsystem.DEBRIS_SOURCE_CONSUME,
			debrisConsumeStarted);
		if (preparedDebris.isPresent()) return preparedDebris.get();
		long fallbackSamplingStarted = WarModPerformanceDiagnostics.begin();
		List<WarheadExplosionDropContext.DestroyedBlock> debris =
			WarheadDebrisSourceSampler.sampleBounded(level, position, yield, seed,
				UNPREPARED_DEBRIS_CHECK_BUDGET);
		WarModPerformanceDiagnostics.record(
			WarModPerformanceDiagnostics.Subsystem.FALLBACK_DEBRIS_SAMPLING,
			fallbackSamplingStarted);
		return debris;
	}

	public static List<WarheadExplosionDropContext.DestroyedBlock> createExplosion(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final WarheadPayloadType payloadType,
		final long seed
	) {
		return createExplosion(level, source, warheadId, position, WarheadYield.defaultFor(payloadType), seed);
	}

	/** Compatibility bridge retained for older call sites. */
	public static List<WarheadExplosionDropContext.DestroyedBlock> createExplosion(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final UUID warheadId,
		final Vec3 position,
		final float strength,
		final long seed
	) {
		if (level == null || warheadId == null || position == null) throw new NullPointerException();
		if (!position.isFinite() || !Float.isFinite(strength) || strength <= 0.0F) {
			throw new IllegalArgumentException("Invalid explosion arguments");
		}
		if (strength >= WarheadConstants.EXPLOSION_STRENGTH) {
			WarheadYield yield = StrategicExplosionProfiles.fromLegacyStrength(strength).yield();
			return createExplosion(level, source, warheadId, position, yield, seed);
		}
		return createExplosion(level, source, position, strength);
	}

	/**
	 * Small anti-air and utility explosions still use vanilla. Strategic and
	 * configurable test yields use the custom no-drop engine above.
	 */
	public static List<WarheadExplosionDropContext.DestroyedBlock> createExplosion(
		final ServerLevel level,
		final @Nullable ServerPlayer source,
		final Vec3 position,
		final float strength
	) {
		if (level == null || position == null) throw new NullPointerException();
		if (!position.isFinite() || !Float.isFinite(strength) || strength <= 0.0F) {
			throw new IllegalArgumentException("Invalid explosion arguments");
		}
		FireWindEngine.addExplosionImpulse(level, position,
			12.0 + strength * 7.0, Math.min(1.35, 0.20 + strength * 0.085),
			Math.min(96, 28 + Math.round(strength * 3.0F)));
		WarheadExplosionDropContext.enter();
		try {
			level.explode(source, Explosion.getDefaultDamageSource(level, source), null,
				position.x, position.y, position.z, strength, false, Level.ExplosionInteraction.TNT,
				ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, DEFAULT_BLOCK_PARTICLES,
				BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSoundEvents.SILENT));
			return WarheadExplosionDropContext.exitAndCollect();
		} catch (RuntimeException | Error failure) {
			WarheadExplosionDropContext.abort();
			throw failure;
		}
	}

	private static double preparationInvalidationRadius(final WarheadYield yield) {
		return WarheadFootprintCalculator.calculate(yield.payloadType(), yield,
			Vec3.ZERO).maximumMutationRadius();
	}
}
