package com.andye.warmod.icbm;

import com.andye.warmod.WarMod;
import com.andye.warmod.icbm.network.ClientboundIcbmSeparationPayload;
import com.andye.warmod.icbm.network.IcbmVisualNetworking;
import com.andye.warmod.warhead.WarheadLaunchService;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative timing for a cinematic carrier that never loads or collides with route terrain. */
public final class IcbmFlightController {
	private final IcbmFlightPlan flightPlan;
	private boolean completed;

	public IcbmFlightController(final IcbmFlightPlan flightPlan) {
		this.flightPlan = flightPlan;
	}

	public IcbmFlightPlan flightPlan() { return this.flightPlan; }
	public boolean completed() { return this.completed; }

	public void tick(final ServerLevel level) {
		if (this.completed) return;
		long elapsed = level.getGameTime() - this.flightPlan.launchGameTime();
		if (elapsed < this.flightPlan.separationTick()) return;
		this.completed = true;

		ServerPlayer owner = null;
		if (level.getServer() != null) {
			ServerPlayer candidate = level.getServer().getPlayerList().getPlayer(this.flightPlan.ownerPlayerId());
			if (candidate != null && candidate.level() == level) owner = candidate;
		}
		Vec3 velocity = IcbmTrajectory.velocity(this.flightPlan, this.flightPlan.separationTick());
		Optional<WarheadLaunchService.LaunchResult> result = WarheadLaunchService.launchFromCarrier(
			level, owner, this.flightPlan.separationPosition(), this.flightPlan.intendedTarget(),
			this.flightPlan.visualSeed(), this.flightPlan.payloadType()
		);
		if (result.isEmpty()) {
			IcbmVisualNetworking.sendRemove(level, this.flightPlan.missileId(), this.flightPlan.launchPosition(), this.flightPlan.intendedTarget());
			if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("ICBM {} cancelled: terminal launch failure", this.flightPlan.missileId());
			return;
		}
		WarheadLaunchService.LaunchResult terminal = result.get();
		IcbmVisualNetworking.sendSeparation(level, new ClientboundIcbmSeparationPayload(
			this.flightPlan.missileId(), terminal.warheadId(), this.flightPlan.separationPosition(), velocity,
			level.getGameTime(), this.flightPlan.visualSeed(), this.flightPlan.payloadType()
		), this.flightPlan.launchPosition(), this.flightPlan.intendedTarget());
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
			"ICBM {} separated {} terminal warhead {} at {}", this.flightPlan.missileId(),
			this.flightPlan.payloadType().serializedName(), terminal.warheadId(), this.flightPlan.separationPosition()
		);
	}
}
