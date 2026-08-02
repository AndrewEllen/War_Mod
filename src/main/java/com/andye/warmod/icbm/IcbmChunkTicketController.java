package com.andye.warmod.icbm;

import com.andye.warmod.WarMod;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** A UUID-owned ticket window with per-chunk reference counts for Minecraft 26.2's keyless ticket API. */
public final class IcbmChunkTicketController {
	private final UUID missileId;
	private final IcbmFlightPlan plan;
	private final Set<ChunkPos> held = new HashSet<>();
	private ChunkPos lastCarrierChunk;
	private boolean released;

	public IcbmChunkTicketController(final IcbmFlightPlan plan) {
		this.missileId = plan.missileId();
		this.plan = plan;
	}

	public void update(final ServerLevel level, final long elapsed) {
		if (this.released || (elapsed & 1L) != 0L) return;
		Set<ChunkPos> wanted = new HashSet<>();
		if (elapsed < this.plan.separationTick()) {
			Vec3 carrier = IcbmTrajectory.position(this.plan, elapsed);
			ChunkPos current = new ChunkPos((int)Math.floor(carrier.x) >> 4, (int)Math.floor(carrier.z) >> 4);
			addWindow(wanted, current, IcbmConstants.CARRIER_CHUNK_RADIUS);
			if (!current.equals(this.lastCarrierChunk)) {
				this.lastCarrierChunk = current;
				if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
					"ICBM {} chunk ticket window moved to {}", this.missileId, current
				);
			}
		}
		if (elapsed >= this.plan.separationTick() - IcbmConstants.TERMINAL_TICKET_LEAD_TICKS) {
			addWindow(wanted, chunk(this.plan.separationPosition()), 0);
			addWindow(wanted, chunk(this.plan.intendedTarget()), IcbmConstants.TARGET_CHUNK_RADIUS);
		}
		this.replace(level, wanted);
	}

	public void releaseAll(final ServerLevel level) {
		if (this.released) return;
		this.replace(level, Set.of());
		this.released = true;
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("ICBM {} released all chunk tickets", this.missileId);
	}

	private void replace(final ServerLevel level, final Set<ChunkPos> wanted) {
		for (ChunkPos position : Set.copyOf(this.held)) if (!wanted.contains(position)) {
			IcbmChunkTicketRegistry.release(level, position);
			this.held.remove(position);
		}
		for (ChunkPos position : wanted) if (this.held.add(position)) IcbmChunkTicketRegistry.acquire(level, position);
	}

	private static ChunkPos chunk(final Vec3 position) {
		return IcbmChunkTicketRegistry.chunk(position);
	}

	private static void addWindow(final Set<ChunkPos> positions, final ChunkPos center, final int radius) {
		IcbmChunkTicketRegistry.addWindow(positions, center, radius);
	}
}