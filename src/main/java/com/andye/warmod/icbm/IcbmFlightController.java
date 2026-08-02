package com.andye.warmod.icbm;

import com.andye.warmod.WarMod;
import com.andye.warmod.icbm.network.ClientboundIcbmSeparationPayload;
import com.andye.warmod.icbm.network.IcbmVisualNetworking;
import com.andye.warmod.radar.RadarRemovalReason;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.warhead.WarheadLaunchService;
import com.andye.warmod.silo.MissileSiloCollisionContext;
import com.andye.warmod.silo.MissileSiloCollisionDetector;
import com.andye.warmod.silo.MissileSiloDetonationService;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative timing for a cinematic carrier that never collides with route terrain. */
public final class IcbmFlightController {
	private final IcbmFlightPlan flightPlan;
	private final IcbmChunkTicketController chunkTickets;
	private final @Nullable MissileSiloCollisionContext collisionContext;
	private Vec3 previousPosition;
	private boolean separated;
	private boolean completed;
	private long cleanupElapsed = Long.MAX_VALUE;

	public IcbmFlightController(final IcbmFlightPlan flightPlan) {
		this(flightPlan, null);
	}

	public IcbmFlightController(final IcbmFlightPlan flightPlan,
		final @Nullable MissileSiloCollisionContext collisionContext) {
		this.flightPlan = flightPlan;
		this.chunkTickets = new IcbmChunkTicketController(flightPlan);
		this.collisionContext = collisionContext;
		this.previousPosition = flightPlan.launchPosition();
	}

	public IcbmFlightPlan flightPlan() { return this.flightPlan; }
	public boolean completed() { return this.completed; }

	public void tick(final ServerLevel level) {
		if (this.completed) return;
		long elapsed = Math.max(0L, level.getGameTime() - this.flightPlan.launchGameTime());
		Vec3 currentPosition = IcbmTrajectory.position(this.flightPlan, elapsed);
		if (this.collisionContext != null && elapsed < this.flightPlan.ignitionTicks() + this.flightPlan.boostTicks()) {
			MissileSiloCollisionDetector.Collision collision = MissileSiloCollisionDetector.findFirst(level,
				this.previousPosition, currentPosition, this.collisionContext);
			if (collision != null) {
				this.collide(level, collision);
				return;
			}
		}
		this.previousPosition = currentPosition;
		this.chunkTickets.update(level, elapsed);
		if (!this.separated && elapsed >= this.flightPlan.separationTick()) this.separate(level);
		if (this.separated && elapsed >= this.cleanupElapsed) this.complete(level);
	}

	private void collide(final ServerLevel level, final MissileSiloCollisionDetector.Collision collision) {
		IcbmVisualNetworking.sendRemove(level, this.flightPlan.missileId(), this.flightPlan.ownerPlayerId(),
			this.flightPlan.launchPosition(), collision.impactPosition());
		this.chunkTickets.releaseAll(level);
		MissileSiloDetonationService.detonateAt(level, this.flightPlan.ownerPlayerId(), this.flightPlan.missileId(),
			this.flightPlan.missileId(), collision.impactPosition(), this.flightPlan.visualSeed(), this.flightPlan.payloadType());
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("Silo missile {} struck block {} at {}",
			this.flightPlan.missileId(), collision.blockPosition(), collision.impactPosition());
		this.completed = true;
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
			this.flightPlan.visualSeed(), this.flightPlan.payloadType(), this.flightPlan.missileId()
		);
		if (result.isEmpty()) {
			IcbmVisualNetworking.sendRemove(level, this.flightPlan.missileId(), this.flightPlan.ownerPlayerId(),
				this.flightPlan.launchPosition(), this.flightPlan.intendedTarget());
			if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
				"ICBM {} cancelled: terminal launch failure", this.flightPlan.missileId()
			);
			RadarTrackingService.removeTrack(level, this.flightPlan.missileId(), RadarRemovalReason.TERMINAL_LAUNCH_FAILED);
			this.complete(level);
			return;
		}
		WarheadLaunchService.LaunchResult terminal = result.get();
		RadarTrackingService.registerTerminalSeparation(level, this.flightPlan.missileId(), terminal);
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