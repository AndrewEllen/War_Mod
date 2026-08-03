package com.andye.warmod.icbm;

import com.andye.warmod.radar.RadarRemovalReason;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.silo.MissileSiloCollisionContext;
import com.andye.warmod.icbm.guidance.IcbmGuidanceProfile;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class IcbmFlightControllerManager {
	private static final int MAXIMUM_ACTIVE_FLIGHTS_PER_LEVEL = 64;
	private static final Map<ServerLevel, LinkedHashMap<UUID, IcbmFlightController>> ACTIVE = new WeakHashMap<>();
	private static boolean registered;

	private IcbmFlightControllerManager() { }

	public static void register() {
		if (registered) return;
		ServerTickEvents.END_LEVEL_TICK.register(IcbmFlightControllerManager::tickLevel);
		ServerLifecycleEvents.SERVER_STOPPING.register(IcbmFlightControllerManager::releaseAll);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
		registered = true;
	}

	public static synchronized boolean add(final ServerLevel level, final IcbmFlightPlan plan) {
		return addInternal(level, plan, null);
	}

	public static synchronized boolean add(final ServerLevel level, final IcbmFlightPlan plan,
		final MissileSiloCollisionContext collisionContext) { return addInternal(level, plan, collisionContext, null); }
	public static synchronized boolean add(final ServerLevel level, final IcbmFlightPlan plan,
		final MissileSiloCollisionContext collisionContext, final IcbmGuidanceProfile guidance) {
		return addInternal(level, plan, collisionContext, guidance);
	}

	private static boolean addInternal(final ServerLevel level, final IcbmFlightPlan plan,
		final MissileSiloCollisionContext collisionContext) { return addInternal(level, plan, collisionContext, null); }
	private static boolean addInternal(final ServerLevel level, final IcbmFlightPlan plan,
		final MissileSiloCollisionContext collisionContext, final IcbmGuidanceProfile guidance) {
		LinkedHashMap<UUID, IcbmFlightController> flights = ACTIVE.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
		if (flights.containsKey(plan.missileId())) return false;
		while (flights.size() >= MAXIMUM_ACTIVE_FLIGHTS_PER_LEVEL) {
			Iterator<IcbmFlightController> iterator = flights.values().iterator();
			if (!iterator.hasNext()) break;
			IcbmFlightController evicted = iterator.next();
			evicted.cancel(level);
			RadarTrackingService.removeTrack(level, evicted.flightPlan().missileId(), RadarRemovalReason.EVICTED);
			iterator.remove();
		}
		flights.put(plan.missileId(), new IcbmFlightController(plan, collisionContext, guidance));
		RadarTrackingService.registerIcbm(level, plan);
		return true;
	}

	public static synchronized List<IcbmFlightPlan> snapshot(final ServerLevel level) {
		LinkedHashMap<UUID, IcbmFlightController> flights = ACTIVE.get(level);
		return flights == null ? List.of() : flights.values().stream().map(IcbmFlightController::flightPlan).toList();
	}

	public static synchronized List<IcbmPointDefenceSnapshot> pointDefenceSnapshots(final ServerLevel level, final long gameTime) {
		LinkedHashMap<UUID, IcbmFlightController> flights = ACTIVE.get(level);
		if (flights == null || flights.isEmpty()) return List.of();
		java.util.ArrayList<IcbmPointDefenceSnapshot> snapshots = new java.util.ArrayList<>();
		for (IcbmFlightController controller : flights.values()) {
			if (controller.completed() || controller.separated()) continue;
			IcbmFlightPlan plan = controller.flightPlan();
			double elapsed = Math.max(0.0, gameTime - plan.launchGameTime());
			var position = IcbmTrajectory.position(plan, elapsed);
			var velocity = IcbmTrajectory.velocity(plan, elapsed);
			double carrierRemaining = Math.max(0.0, plan.separationTick() - elapsed);
			double terminalDistance = plan.separationPosition().distanceTo(plan.intendedTarget());
			double terminalTicks = Math.ceil(terminalDistance / com.andye.warmod.warhead.WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK);
			terminalTicks = net.minecraft.util.Mth.clamp(terminalTicks, IcbmConstants.MINIMUM_TERMINAL_TICKS, IcbmConstants.MAXIMUM_TERMINAL_TICKS);
			snapshots.add(new IcbmPointDefenceSnapshot(plan.missileId(), plan.payloadType(), position, velocity, plan.intendedTarget(), carrierRemaining + terminalTicks));
		}
		return List.copyOf(snapshots);
	}
	public static synchronized java.util.Optional<com.andye.warmod.antiair.StrategicMissileTargetState> targetState(final ServerLevel level,final UUID rootTrackId,final long gameTime){var flights=ACTIVE.get(level);var controller=flights==null?null:flights.get(rootTrackId);return controller==null||controller.separated()?java.util.Optional.empty():java.util.Optional.of(controller.targetState(gameTime));}
	public static synchronized boolean cancelForInterception(final ServerLevel level,final UUID rootTrackId,final UUID interceptorId,final net.minecraft.world.phys.Vec3 position){var flights=ACTIVE.get(level);var controller=flights==null?null:flights.get(rootTrackId);if(controller==null||!controller.cancelForInterception(level,interceptorId,position))return false;flights.remove(rootTrackId);if(flights.isEmpty())ACTIVE.remove(level);return true;}
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

	private static synchronized void releaseAll(final MinecraftServer server) {
		for (Map.Entry<ServerLevel, LinkedHashMap<UUID, IcbmFlightController>> entry : ACTIVE.entrySet())
			for (IcbmFlightController controller : entry.getValue().values()) controller.cancel(entry.getKey());
		ACTIVE.clear();
	}
}