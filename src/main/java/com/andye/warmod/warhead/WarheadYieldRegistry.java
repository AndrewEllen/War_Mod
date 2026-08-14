package com.andye.warmod.warhead;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

/**
 * Ephemeral test-yield overrides keyed by the strategic radar root track.
 *
 * <p>This avoids changing production missile save data solely for developer
 * tooling. Direct test warheads register their own root ID; full ICBMs register
 * the carrier missile ID, which is also passed to every terminal child as the
 * radar root track. Entries expire automatically.</p>
 */
public final class WarheadYieldRegistry {
	private static final long RETENTION_TICKS = 6_000L;
	private static final Map<ServerLevel, Map<UUID, Entry>> ENTRIES = new WeakHashMap<>();
	private static boolean registered;

	private WarheadYieldRegistry() {
	}

	public static synchronized void registerLifecycle() {
		if (registered) return;
		ServerTickEvents.END_LEVEL_TICK.register(WarheadYieldRegistry::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		registered = true;
	}

	public static synchronized void put(
		final ServerLevel level,
		final UUID radarRootTrackId,
		final WarheadYield yield
	) {
		put(level, radarRootTrackId, yield, false);
	}

	public static synchronized void put(
		final ServerLevel level,
		final UUID radarRootTrackId,
		final WarheadYield yield,
		final boolean customFire
	) {
		if (level == null || radarRootTrackId == null || yield == null) return;
		ENTRIES.computeIfAbsent(level, ignored -> new HashMap<>()).put(
			radarRootTrackId,
			new Entry(yield, customFire, level.getGameTime() + RETENTION_TICKS)
		);
	}

	public static synchronized WarheadYield resolve(
		final ServerLevel level,
		final UUID warheadId,
		final UUID radarRootTrackId,
		final WarheadPayloadType fallback
	) {
		Map<UUID, Entry> levelEntries = ENTRIES.get(level);
		if (levelEntries != null) {
			Entry root = radarRootTrackId == null ? null : levelEntries.get(radarRootTrackId);
			if (root != null) return root.yield;
			Entry direct = warheadId == null ? null : levelEntries.get(warheadId);
			if (direct != null) return direct.yield;
		}
		return WarheadYield.defaultFor(fallback);
	}

	public static synchronized boolean usesCustomFire(
		final ServerLevel level,
		final UUID warheadId,
		final UUID radarRootTrackId
	) {
		Map<UUID, Entry> levelEntries = ENTRIES.get(level);
		if (levelEntries == null) return false;
		Entry root = radarRootTrackId == null ? null : levelEntries.get(radarRootTrackId);
		if (root != null) return root.customFire;
		Entry direct = warheadId == null ? null : levelEntries.get(warheadId);
		return direct != null && direct.customFire;
	}

	private static synchronized void tick(final ServerLevel level) {
		Map<UUID, Entry> levelEntries = ENTRIES.get(level);
		if (levelEntries == null) return;
		long now = level.getGameTime();
		Iterator<Entry> iterator = levelEntries.values().iterator();
		while (iterator.hasNext()) {
			if (iterator.next().expiresAt < now) iterator.remove();
		}
		if (levelEntries.isEmpty()) ENTRIES.remove(level);
	}

	private static synchronized void clear() {
		ENTRIES.clear();
	}

	private record Entry(WarheadYield yield, boolean customFire, long expiresAt) {
	}
}
