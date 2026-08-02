package com.andye.warmod.icbm;

import com.andye.warmod.WarMod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** A UUID-owned ticket window with per-chunk reference counts for Minecraft 26.2's keyless ticket API. */
public final class IcbmChunkTicketController {
	private static final Map<ServerLevel, Map<ChunkPos, Integer>> REFERENCES = new WeakHashMap<>();
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
			release(level, position);
			this.held.remove(position);
		}
		for (ChunkPos position : wanted) if (this.held.add(position)) acquire(level, position);
	}

	private static synchronized void acquire(final ServerLevel level, final ChunkPos position) {
		Map<ChunkPos, Integer> references = REFERENCES.computeIfAbsent(level, ignored -> new HashMap<>());
		int count = references.getOrDefault(position, 0);
		if (count == 0) level.getChunkSource().addTicketWithRadius(IcbmChunkTicketType.ICBM, position, 0);
		references.put(position, count + 1);
	}

	private static synchronized void release(final ServerLevel level, final ChunkPos position) {
		Map<ChunkPos, Integer> references = REFERENCES.get(level);
		if (references == null) return;
		int count = references.getOrDefault(position, 0);
		if (count <= 1) {
			references.remove(position);
			level.getChunkSource().removeTicketWithRadius(IcbmChunkTicketType.ICBM, position, 0);
		} else references.put(position, count - 1);
		if (references.isEmpty()) REFERENCES.remove(level);
	}

	private static ChunkPos chunk(final Vec3 position) {
		return new ChunkPos((int)Math.floor(position.x) >> 4, (int)Math.floor(position.z) >> 4);
	}

	private static void addWindow(final Set<ChunkPos> positions, final ChunkPos center, final int radius) {
		for (int x = center.x() - radius; x <= center.x() + radius; x++)
			for (int z = center.z() - radius; z <= center.z() + radius; z++) positions.add(new ChunkPos(x, z));
	}
}