package com.andye.warmod.icbm;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

public final class IcbmFlightControllerManager {
	private static final int MAXIMUM_ACTIVE_FLIGHTS_PER_LEVEL = 64;
	private static final Map<ServerLevel, LinkedHashMap<UUID, IcbmFlightController>> ACTIVE = new WeakHashMap<>();
	private static boolean registered;

	private IcbmFlightControllerManager() { }

	public static void register() {
		if (registered) return;
		ServerTickEvents.END_LEVEL_TICK.register(IcbmFlightControllerManager::tickLevel);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
		registered = true;
	}

	public static synchronized boolean add(final ServerLevel level, final IcbmFlightPlan plan) {
		LinkedHashMap<UUID, IcbmFlightController> flights = ACTIVE.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
		if (flights.containsKey(plan.missileId())) return false;
		while (flights.size() >= MAXIMUM_ACTIVE_FLIGHTS_PER_LEVEL) {
			Iterator<UUID> iterator = flights.keySet().iterator();
			if (!iterator.hasNext()) break;
			iterator.next();
			iterator.remove();
		}
		flights.put(plan.missileId(), new IcbmFlightController(plan));
		return true;
	}

	private static synchronized void tickLevel(final ServerLevel level) {
		LinkedHashMap<UUID, IcbmFlightController> flights = ACTIVE.get(level);
		if (flights == null) return;
		Iterator<IcbmFlightController> iterator = flights.values().iterator();
		while (iterator.hasNext()) {
			IcbmFlightController controller = iterator.next();
			controller.tick(level);
			if (controller.completed()) iterator.remove();
		}
		if (flights.isEmpty()) ACTIVE.remove(level);
	}
}
