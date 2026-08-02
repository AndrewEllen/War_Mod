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

/** Server-authoritative timing for a cinematic carrier that never collides with route terrain. */
public final class IcbmFlightController {
	private final IcbmFlightPlan flightPlan;
	private final IcbmChunkTicketController chunkTickets;
	private boolean separated;
	private boolean completed;
	private long cleanupElapsed = Long.MAX_VALUE;

	public IcbmFlightController(final IcbmFlightPlan flightPlan) {
		this.flightPlan = flightPlan;
		this.chunkTickets = new IcbmChunkTicketController(flightPlan);
	}

	public IcbmFlightPlan flightPlan() { return this.flightPlan; }
	public boolean completed() { return this.completed; }

	public void tick(final ServerLevel level) {
		if (this.completed) return;
		long elapsed = Math.max(0L, level.getGameTime() - this.flightPlan.launchGameTime());
		this.chunkTickets.update(level, elapsed);
		if (!this.separated && elapsed >= this.flightPlan.separationTick()) this.separate(level);
		if (this.separated && elapsed >= this.cleanupElapsed) this.complete(level);
	}

	public void cancel(final ServerLevel level) {
		if (this.completed) return;
		IcbmVisualNetworking.sendRemove(level, this.flightPlan.missileId(), this.flightPlan.ownerPlayerId(),
			this.flightPlan.launchPosition(), this.flightPlan.intendedTarget());
		this.complete(level);
	}

	private void separate(final ServerLevel level) {
		this.separated = true;
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(this.flightPlan.ownerPlayerId());
		if (owner != null && owner.level() != level) owner = null;
		Vec3 velocity = IcbmTrajectory.velocity(this.flightPlan, this.flightPlan.separationTick());
		Optional<WarheadLaunchService.LaunchResult> result = WarheadLaunchService.launchFromCarrier(
			level, owner, this.flightPlan.separationPosition(), this.flightPlan.intendedTarget(),
			this.flightPlan.visualSeed(), this.flightPlan.payloadType()
		);
		if (result.isEmpty()) {
			IcbmVisualNetworking.sendRemove(level, this.flightPlan.missileId(), this.flightPlan.ownerPlayerId(),
				this.flightPlan.launchPosition(), this.flightPlan.intendedTarget());
			if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
				"ICBM {} cancelled: terminal launch failure", this.flightPlan.missileId()
			);
			this.complete(level);
			return;
		}
		WarheadLaunchService.LaunchResult terminal = result.get();
		this.cleanupElapsed = (long)this.flightPlan.separationTick() + terminal.flightTicks()
			+ IcbmConstants.TERMINAL_TICKET_TAIL_TICKS;
		IcbmVisualNetworking.sendSeparation(level, new ClientboundIcbmSeparationPayload(
			this.flightPlan.missileId(), terminal.warheadId(), this.flightPlan.separationPosition(), velocity,
			level.getGameTime(), this.flightPlan.visualSeed(), this.flightPlan.payloadType()
		), this.flightPlan.ownerPlayerId(), this.flightPlan.launchPosition(), this.flightPlan.intendedTarget());
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
			"ICBM {} separated {} terminal warhead {} at {}", this.flightPlan.missileId(),
			this.flightPlan.payloadType().serializedName(), terminal.warheadId(), this.flightPlan.separationPosition()
		);
	}

	private void complete(final ServerLevel level) {
		this.chunkTickets.releaseAll(level);
		this.completed = true;
	}
}