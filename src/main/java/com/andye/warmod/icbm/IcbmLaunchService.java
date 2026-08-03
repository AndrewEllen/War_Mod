package com.andye.warmod.icbm;

import com.andye.warmod.WarMod;
import com.andye.warmod.icbm.network.ClientboundIcbmLaunchPayload;
import com.andye.warmod.icbm.network.IcbmVisualNetworking;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.silo.MissileSiloCollisionContext;
import com.andye.warmod.icbm.guidance.IcbmGuidanceProfile;
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
		return launch(level, player, target, payloadType, com.andye.warmod.warhead.WarheadDeliveryMode.SINGLE);
	}
	public static Optional<LaunchResult> launch(final ServerLevel level, final ServerPlayer player, final Vec3 target,
		final WarheadPayloadType payloadType, final com.andye.warmod.warhead.WarheadDeliveryMode deliveryMode) {
		Optional<LaunchResult> result = launchInternal(level, player, target, null, payloadType, true, true);
		result.ifPresent(launch -> com.andye.warmod.warhead.StrategicMissilePayloadRegistry.put(
			launch.flightPlan().missileId(), new com.andye.warmod.warhead.StrategicMissilePayload(payloadType, deliveryMode)));
		return result;
	}

	/** Immediate completion entry retained for already-loaded internal callers; commands use the pending manager. */
	public static Optional<LaunchResult> launchFromCommand(final ServerLevel level, final ServerPlayer player,
		final Vec3 target, final @Nullable Vec3 requestedLaunch, final WarheadPayloadType payloadType) {
		PreparedCommandLaunch prepared = prepareCommandLaunch(level, player, target, requestedLaunch, payloadType).orElse(null);
		if (prepared == null || !targetChunkLoaded(level, target) || !targetChunkLoaded(level, prepared.launchPosition()))
			return Optional.empty();
		IcbmPendingCommandLaunch request = new IcbmPendingCommandLaunch(prepared.requestId(), player.getUUID(),
			level.dimension(), target, requestedLaunch, prepared.launchPosition(), payloadType, level.getGameTime(),
			prepared.visualSeed(), java.util.Set.of());
		return completePendingCommandLaunch(level, player, request);
	}

	public static Optional<PreparedCommandLaunch> prepareCommandLaunch(final ServerLevel level, final ServerPlayer player,
		final Vec3 target, final @Nullable Vec3 requestedLaunch, final WarheadPayloadType payloadType) {
		if (level == null || player == null || payloadType == null || player.level() != level
			|| !validTargetCoordinate(level, target) || (requestedLaunch != null && !validRouteCoordinate(level, requestedLaunch)))
			return Optional.empty();
		UUID requestId = UUID.randomUUID();
		long seed = mix(requestId.getMostSignificantBits()
			^ Long.rotateLeft(requestId.getLeastSignificantBits(), 17) ^ payloadType.ordinal());
		Vec3 launch = requestedLaunch == null
			? virtualLaunchPosition(level, player, target, cloudHeight(level, target), seed, false)
			: requestedLaunch;
		if (!validRouteCoordinate(level, launch)) return Optional.empty();
		double horizontalDistance = launch.subtract(target).horizontalDistance();
		if (!Double.isFinite(horizontalDistance) || horizontalDistance < 1.0
			|| horizontalDistance > IcbmConstants.MAXIMUM_COMMAND_ROUTE_LENGTH) return Optional.empty();
		return Optional.of(new PreparedCommandLaunch(requestId, launch, seed));
	}

	public static boolean pendingRequestStillValid(final ServerLevel level, final IcbmPendingCommandLaunch request) {
		if (request == null || request.payloadType() == null || !level.dimension().equals(request.dimension())
			|| !validTargetCoordinate(level, request.target()) || !validRouteCoordinate(level, request.launchPosition())) return false;
		double horizontalDistance = request.launchPosition().subtract(request.target()).horizontalDistance();
		return Double.isFinite(horizontalDistance) && horizontalDistance >= 1.0
			&& horizontalDistance <= IcbmConstants.MAXIMUM_COMMAND_ROUTE_LENGTH;
	}

	public static Optional<LaunchResult> completePendingCommandLaunch(final ServerLevel level, final ServerPlayer player,
		final IcbmPendingCommandLaunch request) {
		if (player == null || player.level() != level || !player.getUUID().equals(request.playerId())
			|| !pendingRequestStillValid(level, request) || !targetChunkLoaded(level, request.target())
			|| !targetChunkLoaded(level, request.launchPosition())) return Optional.empty();
		IcbmFlightPlan plan = createFlightPlan(level, player, request.target(), request.launchPosition(),
			request.payloadType(), request.requestId(), request.visualSeed(), false).orElse(null);
		return acceptPlan(level, plan);
	}

	private static Optional<LaunchResult> launchInternal(final ServerLevel level, final ServerPlayer player,
		final Vec3 target, final @Nullable Vec3 requestedLaunch, final WarheadPayloadType payloadType,
		final boolean enforceStickRange, final boolean groundLevelTestingOrigin) {
		if (level == null || player == null || target == null || payloadType == null || player.level() != level
			|| !validTargetCoordinate(level, target) || (enforceStickRange
			&& (!targetChunkLoaded(level, target)
				|| player.getEyePosition().distanceTo(target) > WarheadConstants.TARGET_RANGE_BLOCKS + 0.001))
			|| (requestedLaunch != null && !validRouteCoordinate(level, requestedLaunch))) return Optional.empty();

		UUID id = UUID.randomUUID();
		long seed = mix(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17) ^ payloadType.ordinal());
		IcbmFlightPlan plan = createFlightPlan(level, player, target, requestedLaunch, payloadType, id, seed,
			groundLevelTestingOrigin).orElse(null);
		return acceptPlan(level, plan);
	}

	public static Optional<LaunchResult> launchFromSilo(final ServerLevel level, final @Nullable UUID ownerPlayerId,
		final @Nullable String ownerDisplayName, final Vec3 launchPosition, final Vec3 intendedTarget,
		final WarheadPayloadType payloadType, final MissileSiloCollisionContext collisionContext,
		final UUID siloId, final BlockPos siloCentre, final int guidanceTier) {
		if (level == null || launchPosition == null || intendedTarget == null || payloadType == null
			|| collisionContext == null || !validRouteCoordinate(level, launchPosition)
			|| !validTargetCoordinate(level, intendedTarget)) return Optional.empty();
		double horizontalDistance = launchPosition.subtract(intendedTarget).horizontalDistance();
		if (!Double.isFinite(horizontalDistance) || horizontalDistance < 1.0
			|| horizontalDistance > IcbmConstants.MAXIMUM_COMMAND_ROUTE_LENGTH) return Optional.empty();
		UUID missileId = UUID.randomUUID();
		long seed = mix(missileId.getMostSignificantBits() ^ Long.rotateLeft(missileId.getLeastSignificantBits(), 17)
			^ payloadType.ordinal());
		IcbmFlightPlan plan = createSiloFlightPlan(level, ownerPlayerId == null ? new UUID(0L, 0L) : ownerPlayerId,
			launchPosition, intendedTarget, payloadType, missileId, seed).orElse(null);
		IcbmGuidanceProfile guidance = plan == null ? null : new IcbmGuidanceProfile(siloId, siloCentre, guidanceTier,
			intendedTarget, plan.visualSeed());
		return acceptPlan(level, plan, collisionContext, guidance);
	}

	private static Optional<IcbmFlightPlan> createSiloFlightPlan(final ServerLevel level, final UUID ownerPlayerId,
		final Vec3 launch, final Vec3 target, final WarheadPayloadType payloadType, final UUID id, final long seed) {
		double cloudHeight = cloudHeight(level, launch);
		Vec3 horizontal = new Vec3(target.x - launch.x, 0.0, target.z - launch.z);
		double horizontalDistance = horizontal.length();
		if (horizontalDistance < 1.0 || horizontalDistance > IcbmConstants.MAXIMUM_COMMAND_ROUTE_LENGTH) return Optional.empty();
		horizontal = horizontal.scale(1.0 / horizontalDistance);
		double buildTop = level.dimensionType().minY() + level.dimensionType().height();
		double ceiling = Math.min(2048.0, Math.max(buildTop + 768.0,
			Math.max(target.y + 800.0, launch.y + 800.0)));
		double burnoutY = Math.min(ceiling - 80.0, Math.max(launch.y + IcbmConstants.PREFERRED_BURNOUT_HEIGHT_ABOVE_LAUNCH,
			Math.max(target.y + 420.0, cloudHeight + 96.0)));
		double separationY = Math.min(ceiling - 40.0, target.y + IcbmConstants.PREFERRED_SEPARATION_HEIGHT_ABOVE_TARGET);
		Vec3 burnout = new Vec3(launch.x, burnoutY, launch.z);
		double terminalVertical = Math.max(0.0, separationY - target.y);
		double terminalTravel = IcbmConstants.MAXIMUM_TERMINAL_TICKS * WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK;
		double maxOffset = Math.sqrt(Math.max(0.0, terminalTravel * terminalTravel - terminalVertical * terminalVertical));
		double separationOffset = Math.min(IcbmConstants.SEPARATION_HORIZONTAL_OFFSET, maxOffset);
		Vec3 separation = new Vec3(target.x - horizontal.x * separationOffset, separationY,
			target.z - horizontal.z * separationOffset);
		if (!validRouteCoordinate(level, burnout) || !validRouteCoordinate(level, separation)) return Optional.empty();
		double preferredApex = Math.min(ceiling, Math.max(cloudHeight + 400.0,
			Math.max(target.y + 560.0, launch.y + 520.0)));
		int coastTicks = chooseCoastTicks(burnout, separation, preferredApex, ceiling,
			Mth.clamp(horizontalDistance / IcbmConstants.MAXIMUM_COAST_TICKS * 0.70, 0.25, 2.8));
		if (coastTicks < 0) return Optional.empty();
		try {
			return Optional.of(new IcbmFlightPlan(id, ownerPlayerId, launch, burnout, separation, target,
				level.getGameTime(), IcbmConstants.IGNITION_TICKS, IcbmConstants.BOOST_TICKS, coastTicks, seed, payloadType));
		} catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}
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
		// Boost geometry is target-independent: lateral steering starts only after burnout.
		Vec3 burnout = new Vec3(launch.x, burnoutY, launch.z);
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
		double minimumHorizontalSpeed = groundLevelTestingOrigin ? 0.25 : Mth.clamp(
			horizontalDistance / IcbmConstants.MAXIMUM_COAST_TICKS * 0.70, 0.25, 2.8);
		int coastTicks = chooseCoastTicks(burnout, separation, preferredApexY, absoluteFlightCeiling,
			minimumHorizontalSpeed);
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

	public static Optional<IcbmFlightPlan> retargetFromBurnout(final ServerLevel level,
		final IcbmFlightPlan original, final Vec3 resolvedTarget) {
		if (!validBoostGeometry(original) || !validTargetCoordinate(level, resolvedTarget)
			|| original.launchPosition().distanceTo(resolvedTarget) > IcbmConstants.MAXIMUM_COMMAND_ROUTE_LENGTH)
			return Optional.empty();
		Vec3 horizontal = new Vec3(resolvedTarget.x - original.burnoutPosition().x, 0.0,
			resolvedTarget.z - original.burnoutPosition().z);
		double distance = horizontal.length();
		if (!Double.isFinite(distance) || distance < 1.0) return Optional.empty();
		horizontal = horizontal.scale(1.0 / distance);
		double ceiling = Math.min(2048.0, Math.max(level.dimensionType().minY() + level.dimensionType().height() + 768.0,
			Math.max(resolvedTarget.y + 800.0, original.burnoutPosition().y + 80.0)));
		double separationY = Math.min(ceiling - 40.0,
			resolvedTarget.y + IcbmConstants.PREFERRED_SEPARATION_HEIGHT_ABOVE_TARGET);
		double terminalVertical = Math.max(0.0, separationY - resolvedTarget.y);
		double terminalTravel = IcbmConstants.MAXIMUM_TERMINAL_TICKS * WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK;
		double maxOffset = Math.sqrt(Math.max(0.0, terminalTravel * terminalTravel - terminalVertical * terminalVertical));
		double separationOffset = Math.min(IcbmConstants.SEPARATION_HORIZONTAL_OFFSET, maxOffset);
		Vec3 separation = new Vec3(resolvedTarget.x - horizontal.x * separationOffset, separationY,
			resolvedTarget.z - horizontal.z * separationOffset);
		if (!validRouteCoordinate(level, separation)) return Optional.empty();
		double preferredApex = Math.min(ceiling, Math.max(cloudHeight(level, original.burnoutPosition()) + 400.0,
			Math.max(resolvedTarget.y + 560.0, original.launchPosition().y + 520.0)));
		int coastTicks = chooseCoastTicks(original.burnoutPosition(), separation, preferredApex, ceiling,
			Mth.clamp(distance / IcbmConstants.MAXIMUM_COAST_TICKS * 0.70, 0.25, 2.8));
		if (coastTicks < 0) return Optional.empty();
		try { return Optional.of(new IcbmFlightPlan(original.missileId(), original.ownerPlayerId(),
			original.launchPosition(), original.burnoutPosition(), separation, resolvedTarget,
			original.launchGameTime(), original.ignitionTicks(), original.boostTicks(), coastTicks,
			original.visualSeed(), original.payloadType())); }
		catch (IllegalArgumentException ignored) { return Optional.empty(); }
	}
	static boolean validBoostGeometry(final IcbmFlightPlan plan) {
		if (plan == null || !plan.launchPosition().isFinite() || !plan.burnoutPosition().isFinite()) return false;
		return Math.hypot(plan.burnoutPosition().x - plan.launchPosition().x, plan.burnoutPosition().z - plan.launchPosition().z)
			<= IcbmConstants.MAXIMUM_BOOST_HORIZONTAL_DRIFT_BLOCKS && plan.burnoutPosition().y > plan.launchPosition().y;
	}

	static boolean validTargetCoordinate(final ServerLevel level, final Vec3 position) {
		BlockPos block = position == null ? BlockPos.ZERO : BlockPos.containing(position);
		return validRouteCoordinate(level, position) && !level.isOutsideBuildHeight(block);
	}

	static boolean validRouteCoordinate(final ServerLevel level, final Vec3 position) {
		return position != null && position.isFinite() && Level.isInSpawnableBounds(BlockPos.containing(position))
			&& level.getWorldBorder().isWithinBounds(position);
	}

	static boolean targetChunkLoaded(final ServerLevel level, final Vec3 position) {
		return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(position.x), SectionPos.blockToSectionCoord(position.z));
	}

	private static Optional<LaunchResult> acceptPlan(final ServerLevel level, final @Nullable IcbmFlightPlan plan) {
		if (plan == null || !IcbmFlightControllerManager.add(level, plan)) return Optional.empty();
		IcbmVisualNetworking.sendLaunch(level, ClientboundIcbmLaunchPayload.fromPlan(plan), plan.ownerPlayerId());
		double apex = calculateApexY(plan.burnoutPosition(), IcbmTrajectory.coastInitialVelocity(plan), plan.coastTicks());
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
			"ICBM {} virtual launch accepted: launch={}, apex={}, separation={}", plan.missileId(),
			plan.launchPosition(), apex, plan.separationPosition());
		return Optional.of(new LaunchResult(plan, terminalTicks(plan)));
	}

	private static Optional<LaunchResult> acceptPlan(final ServerLevel level, final @Nullable IcbmFlightPlan plan,
		final MissileSiloCollisionContext collisionContext, final IcbmGuidanceProfile guidance) {
		if (plan == null || !IcbmFlightControllerManager.add(level, plan, collisionContext, guidance)) return Optional.empty();
		IcbmVisualNetworking.sendLaunch(level, ClientboundIcbmLaunchPayload.fromPlan(plan), plan.ownerPlayerId());
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
			"ICBM {} silo launch accepted: launch={}, burnout={}, separation={}", plan.missileId(),
			plan.launchPosition(), plan.burnoutPosition(), plan.separationPosition());
		return Optional.of(new LaunchResult(plan, terminalTicks(plan)));
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

	public record PreparedCommandLaunch(UUID requestId, Vec3 launchPosition, long visualSeed) { }
	public record LaunchResult(IcbmFlightPlan flightPlan, int terminalTicks) { }
}