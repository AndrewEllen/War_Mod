package com.andye.warmod.warhead;

import com.andye.warmod.WarMod;
import com.andye.warmod.entity.IncomingWarheadEntity;
import com.andye.warmod.entity.ModEntityTypes;
import com.andye.warmod.warhead.network.ClientboundWarheadLaunchPayload;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;

public final class WarheadLaunchService {
	private WarheadLaunchService() {
	}

	public static Optional<LaunchResult> launch(
		final ServerLevel level,
		final ServerPlayer owner,
		final Vec3 intendedTarget
	) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(intendedTarget, "intendedTarget");
		if (!intendedTarget.isFinite() || owner.level() != level || owner.getEyePosition().distanceTo(intendedTarget) > WarheadConstants.TARGET_RANGE_BLOCKS + 0.001) {
			return Optional.empty();
		}
		if (!isChunkLoaded(level, intendedTarget)) {
			return Optional.empty();
		}

		UUID warheadId = UUID.randomUUID();
		long visualSeed = warheadId.getMostSignificantBits() ^ Long.rotateLeft(warheadId.getLeastSignificantBits(), 17);
		Vec3 startPosition = calculateStartPosition(level, intendedTarget, visualSeed).orElse(null);
		if (startPosition == null) {
			return Optional.empty();
		}

		double trajectoryDistance = startPosition.distanceTo(intendedTarget);
		int flightTicks = clampFlightTicks((int)Math.ceil(trajectoryDistance / WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK));
		long launchGameTime = level.getGameTime();
		IncomingWarheadEntity entity = new IncomingWarheadEntity(
			ModEntityTypes.INCOMING_WARHEAD,
			level,
			warheadId,
			owner.getUUID(),
			startPosition,
			intendedTarget,
			launchGameTime,
			flightTicks,
			visualSeed
		);
		if (!level.addFreshEntity(entity)) {
			return Optional.empty();
		}

		ClientboundWarheadLaunchPayload payload = new ClientboundWarheadLaunchPayload(
			warheadId,
			startPosition.x,
			startPosition.y,
			startPosition.z,
			intendedTarget.x,
			intendedTarget.y,
			intendedTarget.z,
			launchGameTime,
			flightTicks,
			visualSeed
		);
		WarheadVisualNetworking.sendLaunch(level, payload, intendedTarget);

		if (SharedConstants.IS_RUNNING_IN_IDE) {
			WarMod.LOGGER.info(
				"Warhead {} launched: start={}, target={}, flight={}",
				warheadId,
				startPosition,
				intendedTarget,
				flightTicks
			);
		}
		return Optional.of(new LaunchResult(warheadId, startPosition, intendedTarget, flightTicks, visualSeed));
	}

	private static Optional<Vec3> calculateStartPosition(final ServerLevel level, final Vec3 target, final long visualSeed) {
		float cloudHeight;
		try {
			cloudHeight = level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT, target).floatValue();
		} catch (RuntimeException ignored) {
			cloudHeight = Float.NaN;
		}

		double desiredY = target.y + WarheadConstants.PREFERRED_SPAWN_HEIGHT_ABOVE_TARGET;
		if (Float.isFinite(cloudHeight)) {
			desiredY = Math.max(desiredY, cloudHeight + 8.0);
		}
		double maximumY = level.dimensionType().minY() + level.dimensionType().height() - 2.0;
		double minimumY = target.y + WarheadConstants.MINIMUM_SPAWN_HEIGHT_ABOVE_TARGET;
		double startY = desiredY <= maximumY ? desiredY : maximumY;
		if (maximumY < target.y + 1.0) {
			return Optional.empty();
		}
		if (maximumY >= minimumY) {
			startY = Math.max(minimumY, Math.min(desiredY, maximumY));
		}
		if (!Double.isFinite(startY) || startY <= target.y) {
			return Optional.empty();
		}

		SplittableRandom random = new SplittableRandom(visualSeed);
		double angle = random.nextDouble(0.0, Math.PI * 2.0);
		double radius = random.nextDouble(4.0, 10.0);
		Vec3 offsetStart = new Vec3(
			target.x + Math.cos(angle) * radius,
			startY,
			target.z + Math.sin(angle) * radius
		);
		if (isChunkLoaded(level, offsetStart)) {
			return Optional.of(offsetStart);
		}

		Vec3 directStart = new Vec3(target.x, startY, target.z);
		return isChunkLoaded(level, directStart) ? Optional.of(directStart) : Optional.empty();
	}

	private static int clampFlightTicks(final int estimatedTicks) {
		return Math.max(
			WarheadConstants.MINIMUM_FLIGHT_TICKS,
			Math.min(WarheadConstants.MAXIMUM_FLIGHT_TICKS, estimatedTicks)
		);
	}

	private static boolean isChunkLoaded(final ServerLevel level, final Vec3 position) {
		return level.getChunkSource().hasChunk(
			SectionPos.blockToSectionCoord(position.x),
			SectionPos.blockToSectionCoord(position.z)
		);
	}

	public record LaunchResult(
		UUID warheadId,
		Vec3 startPosition,
		Vec3 intendedTarget,
		int flightTicks,
		long visualSeed
	) {
	}
}