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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class IcbmLaunchService {
	private IcbmLaunchService() { }

	/** Stick launch: retains the existing 1,000-block loaded-target contract. */
	public static Optional<LaunchResult> launch(final ServerLevel level, final ServerPlayer player, final Vec3 target,
		final WarheadPayloadType payloadType) {
		return launchInternal(level, player, target, null, payloadType, true, true);
	}

	/** Command launch: target first, with an optional exact virtual launch coordinate. */
	public static Optional<LaunchResult> launchFromCommand(final ServerLevel level, final ServerPlayer player,
		final Vec3 target, final @Nullable Vec3 requestedLaunch, final WarheadPayloadType payloadType) {
		return launchInternal(level, player, target, requestedLaunch, payloadType, false, false);
	}

	private static Optional<LaunchResult> launchInternal(final ServerLevel level, final ServerPlayer player,
		final Vec3 target, final @Nullable Vec3 requestedLaunch, final WarheadPayloadType payloadType,
		final boolean enforceStickRange, final boolean groundLevelTestingOrigin) {
		if (level == null || player == null || target == null || payloadType == null || player.level() != level
			|| !validTarget(level, target) || (enforceStickRange
			&& player.getEyePosition().distanceTo(target) > WarheadConstants.TARGET_RANGE_BLOCKS + 0.001)
			|| (requestedLaunch != null && !validRouteCoordinate(level, requestedLaunch))) return Optional.empty();

		UUID id = UUID.randomUUID();
		long seed = mix(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17) ^ payloadType.ordinal());
		IcbmFlightPlan plan = createFlightPlan(level, player, target, requestedLaunch, payloadType, id, seed,
			groundLevelTestingOrigin).orElse(null);
		if (plan == null || !IcbmFlightControllerManager.add(level, plan)) return Optional.empty();

		IcbmVisualNetworking.sendLaunch(level, ClientboundIcbmLaunchPayload.fromPlan(plan), plan.ownerPlayerId());
		double apex = calculateApexY(plan.burnoutPosition(), IcbmTrajectory.coastInitialVelocity(plan), plan.coastTicks());
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
			"ICBM {} virtual launch accepted: launch={}, apex={}, separation={}", id, plan.launchPosition(), apex,
			plan.separationPosition()
		);
		int terminalTicks = terminalTicks(plan);
		return Optional.of(new LaunchResult(plan, terminalTicks));
	}

	private static Optional<IcbmFlightPlan> createFlightPlan(final ServerLevel level, final ServerPlayer player,
		final Vec3 target, final @Nullable Vec3 requestedLaunch, final WarheadPayloadType payloadType,
		final UUID id, final long seed, final boolean groundLevelTestingOrigin) {
		double cloudHeight = cloudHeight(level, target);
		Vec3 launch = requestedLaunch == null
			? virtualLaunchPosition(level, player, target, cloudHeight, seed, groundLevelTestingOrigin)
			: requestedLaunch;
		if (!validRouteCoordinate(level, launch)) return Optional.empty();
		Vec3 horizontal = new Vec3(target.x - launch.x, 0.0, target.z - launch.z);
		double horizontalDistance = horizontal.length();
		if (horizontalDistance < 1.0 || horizontalDistance > IcbmConstants.MAXIMUM_COMMAND_ROUTE_LENGTH) return Optional.empty();
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
		double verticalTravel = Math.max(0.0, separationY - target.y);
		double maximumTerminalTravel = IcbmConstants.MAXIMUM_TERMINAL_TICKS
			* WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK;
		double maximumHorizontalOffset = Math.sqrt(Math.max(0.0,
			maximumTerminalTravel * maximumTerminalTravel - verticalTravel * verticalTravel));
		double separationOffset = Math.min(IcbmConstants.SEPARATION_HORIZONTAL_OFFSET, maximumHorizontalOffset);
		Vec3 separation = new Vec3(target.x - horizontal.x * separationOffset,
			separationY, target.z - horizontal.z * separationOffset);
		if (!validRouteCoordinate(level, burnout) || !validRouteCoordinate(level, separation)) return Optional.empty();

		double preferredApexY = Math.min(absoluteFlightCeiling, Math.max(cloudHeight + 400.0,
			Math.max(target.y + 560.0, launch.y + 520.0)));
		int coastTicks = chooseCoastTicks(burnout, separation, preferredApexY, absoluteFlightCeiling,
			groundLevelTestingOrigin ? 0.25 : 2.8);
		if (coastTicks < 0) return Optional.empty();
		try {
			return Optional.of(new IcbmFlightPlan(id, player.getUUID(), launch, burnout, separation, target,
				level.getGameTime(), IcbmConstants.IGNITION_TICKS, IcbmConstants.BOOST_TICKS, coastTicks, seed, payloadType));
		} catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}
	}

	private static Vec3 virtualLaunchPosition(final ServerLevel level, final ServerPlayer player,
		final Vec3 target, final double cloudHeight, final long seed, final boolean groundLevelTestingOrigin) {
		Vec3 look = horizontalLook(player);
		Vec3 backward = look.scale(-1.0);
		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		SplittableRandom random = new SplittableRandom(seed ^ 0x5649525455414C4CL);
		double distance = random.nextDouble(IcbmConstants.MINIMUM_VIRTUAL_LAUNCH_DISTANCE,
			IcbmConstants.MAXIMUM_VIRTUAL_LAUNCH_DISTANCE);
		double side = random.nextDouble(-IcbmConstants.MAXIMUM_VIRTUAL_SIDE_OFFSET,
			IcbmConstants.MAXIMUM_VIRTUAL_SIDE_OFFSET);
		Vec3 xz = player.position().add(backward.scale(distance)).add(right.scale(side));
		double launchY = groundLevelTestingOrigin
			? player.getY() + 2.75
			: Math.max(player.getY() + 72.0, Math.max(target.y + 72.0, cloudHeight + 32.0));
		launchY = Mth.clamp(launchY, level.dimensionType().minY() + 3.0, 1536.0);
		return new Vec3(xz.x, launchY, xz.z);
	}

	private static Vec3 horizontalLook(final ServerPlayer player) {
		Vec3 look = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
		return look.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
	}

	private static int chooseCoastTicks(final Vec3 burnout, final Vec3 separation, final double preferredApexY,
		final double ceiling, final double minimumHorizontalSpeed) {
		int best = -1;
		double bestScore = Double.POSITIVE_INFINITY;
		for (int ticks = IcbmConstants.MINIMUM_COAST_TICKS; ticks <= IcbmConstants.MAXIMUM_COAST_TICKS; ticks++) {
			Vec3 initial = coastInitialVelocity(burnout, separation, ticks);
			Vec3 ending = initial.add(0.0, -IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED * ticks, 0.0);
			double apex = calculateApexY(burnout, initial, ticks);
			double horizontalSpeed = initial.horizontalDistance();
			if (!initial.isFinite() || !ending.isFinite() || !Double.isFinite(apex) || apex > ceiling
				|| ending.y > -0.30 || horizontalSpeed < minimumHorizontalSpeed || horizontalSpeed > 48.0) continue;
			double preferredSpeed = Math.min(28.0, Math.max(4.4, separation.subtract(burnout).horizontalDistance() / 300.0));
			double score = Math.abs(apex - preferredApexY) + Math.abs(horizontalSpeed - preferredSpeed) * 20.0
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

	private static boolean validTarget(final ServerLevel level, final Vec3 position) {
		BlockPos block = BlockPos.containing(position);
		return validRouteCoordinate(level, position) && !level.isOutsideBuildHeight(block) && loaded(level, position);
	}

	private static boolean validRouteCoordinate(final ServerLevel level, final Vec3 position) {
		return position != null && position.isFinite() && Level.isInSpawnableBounds(BlockPos.containing(position))
			&& level.getWorldBorder().isWithinBounds(position);
	}

	private static boolean loaded(final ServerLevel level, final Vec3 position) {
		return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(position.x), SectionPos.blockToSectionCoord(position.z));
	}

	private static int terminalTicks(final IcbmFlightPlan plan) {
		return Mth.clamp((int)Math.ceil(plan.separationPosition().distanceTo(plan.intendedTarget())
			/ WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK), IcbmConstants.MINIMUM_TERMINAL_TICKS,
			IcbmConstants.MAXIMUM_TERMINAL_TICKS);
	}

	private static long mix(long value) {
		value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L; value ^= value >>> 27;
		value *= 0x94D049BB133111EBL; return value ^ (value >>> 31);
	}

	public record LaunchResult(IcbmFlightPlan flightPlan, int terminalTicks) { }
}