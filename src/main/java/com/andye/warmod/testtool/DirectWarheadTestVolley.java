package com.andye.warmod.testtool;

import com.andye.warmod.item.component.IcbmTestDeliveryMode;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadLaunchService;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Shared direct-from-above launcher used by the conventional and nuclear test sticks. */
public final class DirectWarheadTestVolley {
	private DirectWarheadTestVolley() {
	}

	public static List<WarheadLaunchService.LaunchResult> launch(
		final ServerLevel level,
		final ServerPlayer owner,
		final Vec3 intendedTarget,
		final WarheadPayloadType payloadType,
		final IcbmTestDeliveryMode mode
	) {
		if (mode == IcbmTestDeliveryMode.SINGLE) {
			return WarheadLaunchService.launch(level, owner, intendedTarget, payloadType)
				.map(List::of)
				.orElseGet(List::of);
		}

		ArrayList<WarheadLaunchService.LaunchResult> launches = new ArrayList<>(4);
		long rotationSeed = level.getGameTime()
			^ owner.getUUID().getMostSignificantBits()
			^ Long.rotateLeft(owner.getUUID().getLeastSignificantBits(), 21);
		double rotation = ((rotationSeed >>> 11) & 65535L) / 65535.0 * Math.PI * 2.0;
		for (int index = 0; index < 4; index++) {
			double angle = rotation + index * Math.PI * 0.5;
			double radius = 7.5;
			Vec3 target = intendedTarget.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
			if (owner.getEyePosition().distanceTo(target) > WarheadConstants.TARGET_RANGE_BLOCKS) {
				target = intendedTarget;
			}
			WarheadLaunchService.launch(level, owner, target, payloadType).ifPresent(launches::add);
		}
		return List.copyOf(launches);
	}
}
