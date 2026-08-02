package com.andye.warmod.icbm;

import com.andye.warmod.WarMod;
import com.andye.warmod.icbm.network.ClientboundIcbmLaunchPayload;
import com.andye.warmod.icbm.network.IcbmVisualNetworking;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class IcbmLaunchService {
	private IcbmLaunchService() { }

	public static Optional<LaunchResult> launch(final ServerLevel level, final ServerPlayer player, final Vec3 target,
		final WarheadPayloadType payloadType) {
		if (level == null || player == null || target == null || payloadType == null || player.level() != level
			|| !target.isFinite() || player.getEyePosition().distanceTo(target) > WarheadConstants.TARGET_RANGE_BLOCKS + 0.001
			|| !loaded(level, target)) return Optional.empty();

		UUID id = UUID.randomUUID();
		long seed = mix(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17) ^ payloadType.ordinal());
		Optional<IcbmFlightPlan> preferred = createFlightPlan(level, player, target, payloadType, id, seed, false);
		IcbmFlightPlan plan = preferred.orElseGet(() -> createFlightPlan(level, player, target, payloadType, id, seed, true).orElse(null));
		if (plan == null || !IcbmFlightControllerManager.add(level, plan)) return Optional.empty();

		IcbmVisualNetworking.sendLaunch(level, ClientboundIcbmLaunchPayload.fromPlan(plan));
		double apex = calculateApexY(plan.burnoutPosition(), IcbmTrajectory.coastInitialVelocity(plan), plan.coastTicks());
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
			"ICBM {} launched from virtual origin {}, payload={}, apex={}, separation={}", id, plan.launchPosition(),
			payloadType.serializedName(), apex, plan.separationPosition()
		);
		int terminalTicks = Mth.clamp((int)Math.ceil(plan.separationPosition().distanceTo(target)
			/ WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK), IcbmConstants.MINIMUM_TERMINAL_TICKS, IcbmConstants.MAXIMUM_TERMINAL_TICKS);
		return Optional.of(new LaunchResult(plan, terminalTicks));
	}

	private static Optional<IcbmFlightPlan> createFlightPlan(final ServerLevel level, final ServerPlayer player,
		final Vec3 target, final WarheadPayloadType payloadType, final UUID id, final long seed, final boolean closeFallback) {
		double cloudHeight = cloudHeight(level, player.position());
		Vec3 launch = closeFallback ? closeLaunchPosition(level, player).orElse(null)
			: virtualLaunchPosition(level, player, target, cloudHeight, seed).orElse(null);
		if (launch == null) return Optional.empty();

		Vec3 horizontal = new Vec3(target.x - launch.x, 0.0, target.z - launch.z);
		double horizontalDistance = horizontal.length();
		if (horizontalDistance < 1.0) return Optional.empty();
		horizontal = horizontal.scale(1.0 / horizontalDistance);

		double dimensionBuildTop = level.dimensionType().minY() + level.dimensionType().height();
		double absoluteFlightCeiling = Math.min(2048.0, Math.max(dimensionBuildTop + 768.0,
			Math.max(target.y + 800.0, launch.y + 800.0)));
		double burnoutY = Math.min(absoluteFlightCeiling - 80.0, Math.max(
			launch.y + IcbmConstants.PREFERRED_BURNOUT_HEIGHT_ABOVE_LAUNCH,
			Math.max(target.y + 420.0, cloudHeight + 240.0)
		));
		double separationY = Math.min(absoluteFlightCeiling - 40.0,
			target.y + IcbmConstants.PREFERRED_SEPARATION_HEIGHT_ABOVE_TARGET);
		if (!Double.isFinite(burnoutY) || !Double.isFinite(separationY)
			|| burnoutY < launch.y + IcbmConstants.MINIMUM_BURNOUT_HEIGHT_ABOVE_LAUNCH
			|| separationY < target.y + IcbmConstants.MINIMUM_SEPARATION_HEIGHT_ABOVE_TARGET) return Optional.empty();

		double burnoutHorizontal = Mth.clamp(horizontalDistance * 0.28, 180.0, 480.0);
		Vec3 burnout = new Vec3(launch.x + horizontal.x * burnoutHorizontal, burnoutY,
			launch.z + horizontal.z * burnoutHorizontal);
		Vec3 separation = new Vec3(target.x - horizontal.x * IcbmConstants.SEPARATION_HORIZONTAL_OFFSET,
			separationY, target.z - horizontal.z * IcbmConstants.SEPARATION_HORIZONTAL_OFFSET);
		if (!burnout.isFinite() || !separation.isFinite()) return Optional.empty();

		double preferredApexY = Math.min(absoluteFlightCeiling, Math.max(cloudHeight + 400.0,
			Math.max(target.y + 560.0, launch.y + 520.0)));
		int coastTicks = chooseCoastTicks(burnout, separation, preferredApexY, absoluteFlightCeiling);
		if (coastTicks < 0) return Optional.empty();
		try {
			return Optional.of(new IcbmFlightPlan(id, player.getUUID(), launch, burnout, separation, target,
				level.getGameTime(), IcbmConstants.IGNITION_TICKS, IcbmConstants.BOOST_TICKS, coastTicks, seed, payloadType));
		} catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}
	}

	private static Optional<Vec3> virtualLaunchPosition(final ServerLevel level, final ServerPlayer player,
		final Vec3 target, final double cloudHeight, final long seed) {
		Vec3 look = horizontalLook(player);
		Vec3 backward = look.scale(-1.0);
		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		SplittableRandom random = new SplittableRandom(seed ^ 0x5649525455414C4CL);
		double distance = random.nextDouble(IcbmConstants.MINIMUM_VIRTUAL_LAUNCH_DISTANCE,
			IcbmConstants.MAXIMUM_VIRTUAL_LAUNCH_DISTANCE);
		double side = random.nextDouble(-IcbmConstants.MAXIMUM_VIRTUAL_SIDE_OFFSET,
			IcbmConstants.MAXIMUM_VIRTUAL_SIDE_OFFSET);
		Vec3 xz = player.position().add(backward.scale(distance)).add(right.scale(side));
		if (loaded(level, xz)) {
			int x = Mth.floor(xz.x), z = Mth.floor(xz.z);
			int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			for (int lift = 0; lift <= 64; lift += 4) {
				Vec3 candidate = new Vec3(x + 0.5, surface + 0.25 + lift, z + 0.5);
				if (clear(level, candidate, lift == 0)) return Optional.of(candidate);
			}
			return Optional.empty();
		}
		double safeY = Math.max(player.getY() + 64.0, Math.max(target.y + 64.0, cloudHeight + 24.0));
		safeY = Mth.clamp(safeY, level.dimensionType().minY() + 32.0, 1536.0);
		return Optional.of(new Vec3(xz.x, safeY, xz.z));
	}

	private static Optional<Vec3> closeLaunchPosition(final ServerLevel level, final ServerPlayer player) {
		Vec3 xz = player.position().add(horizontalLook(player).scale(-IcbmConstants.FINAL_FALLBACK_LAUNCH_DISTANCE));
		if (!loaded(level, xz)) return Optional.empty();
		int x = Mth.floor(xz.x), z = Mth.floor(xz.z);
		Vec3 candidate = new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 0.25, z + 0.5);
		return clear(level, candidate, true) ? Optional.of(candidate) : Optional.empty();
	}

	private static Vec3 horizontalLook(final ServerPlayer player) {
		Vec3 look = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
		return look.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
	}

	private static int chooseCoastTicks(final Vec3 burnout, final Vec3 separation, final double preferredApexY,
		final double ceiling) {
		int best = -1;
		double bestScore = Double.POSITIVE_INFINITY;
		for (int ticks = IcbmConstants.MINIMUM_COAST_TICKS; ticks <= IcbmConstants.MAXIMUM_COAST_TICKS; ticks++) {
			Vec3 initial = coastInitialVelocity(burnout, separation, ticks);
			Vec3 ending = initial.add(0.0, -IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED * ticks, 0.0);
			double apex = calculateApexY(burnout, initial, ticks);
			double horizontalSpeed = initial.horizontalDistance();
			if (!initial.isFinite() || !ending.isFinite() || !Double.isFinite(apex) || apex > ceiling
				|| ending.y > -0.30 || horizontalSpeed < 2.8 || horizontalSpeed > 6.0) continue;
			double score = Math.abs(apex - preferredApexY) + Math.abs(horizontalSpeed - 4.4) * 20.0
				+ Math.abs(ticks - 270) * 0.03;
			if (score < bestScore) { bestScore = score; best = ticks; }
		}
		return best;
	}

	private static Vec3 coastInitialVelocity(final Vec3 burnout, final Vec3 separation, final int ticks) {
		Vec3 gravity = new Vec3(0.0, -IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED, 0.0);
		return separation.subtract(burnout).subtract(gravity.scale(0.5 * ticks * ticks)).scale(1.0 / ticks);
	}

	private static double calculateApexY(final Vec3 burnout, final Vec3 velocity, final int coastTicks) {
		double age = Mth.clamp(velocity.y / IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED, 0.0, (double)coastTicks);
		return burnout.y + velocity.y * age - 0.5 * IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED * age * age;
	}

	private static double cloudHeight(final ServerLevel level, final Vec3 position) {
		try { return level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT, position).doubleValue(); }
		catch (RuntimeException ignored) { return level.dimensionType().minY() + 192.0; }
	}

	private static boolean clear(final ServerLevel level, final Vec3 candidate, final boolean requireGround) {
		BlockPos base = BlockPos.containing(candidate.x, candidate.y, candidate.z);
		if (requireGround) {
			BlockPos ground = base.below();
			if (level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()
				|| !level.getFluidState(ground).isEmpty()) return false;
		}
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy < 7; dy++) {
			BlockPos position = base.offset(dx, dy, dz);
			if (!level.getBlockState(position).getCollisionShape(level, position).isEmpty()
				|| !level.getFluidState(position).isEmpty()) return false;
		}
		return true;
	}

	private static boolean loaded(final ServerLevel level, final Vec3 position) {
		return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(position.x), SectionPos.blockToSectionCoord(position.z));
	}

	private static long mix(long value) {
		value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L; value ^= value >>> 27;
		value *= 0x94D049BB133111EBL; return value ^ (value >>> 31);
	}

	public record LaunchResult(IcbmFlightPlan flightPlan, int terminalTicks) { }
}