package com.andye.warmod.antiair;

import com.andye.warmod.silo.MissileSiloCollisionContext;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class AntiAirFlightControllerManager {
    private static final Map<ServerLevel, LinkedHashMap<UUID, AntiAirFlightController>> ACTIVE = new WeakHashMap<>();
    private static boolean registered;
    private AntiAirFlightControllerManager() { }
    public static void register() { if (registered) return; ServerTickEvents.END_LEVEL_TICK.register(AntiAirFlightControllerManager::tick); ServerLifecycleEvents.SERVER_STOPPING.register(AntiAirFlightControllerManager::stop); ServerLifecycleEvents.SERVER_STOPPED.register(s -> { ACTIVE.clear(); AntiAirTargetClaimRegistry.clearAll(); }); registered = true; }
    public static synchronized boolean canAccept(ServerLevel level) { return ACTIVE.getOrDefault(level, new LinkedHashMap<>()).size() < AntiAirConstants.MAXIMUM_ACTIVE_INTERCEPTORS_PER_LEVEL; }
    public static synchronized boolean add(ServerLevel level, AntiAirFlightPlan plan, MissileSiloCollisionContext collision) { if (!canAccept(level)) return false; var map = ACTIVE.computeIfAbsent(level, ignored -> new LinkedHashMap<>()); if (map.containsKey(plan.interceptorId())) return false; map.put(plan.interceptorId(), new AntiAirFlightController(plan, collision)); return true; }
    public static synchronized boolean isTargetTracking(ServerLevel level, UUID interceptorId, UUID targetId) { var map = ACTIVE.get(level); var controller = map == null ? null : map.get(interceptorId); return controller != null && controller.isTargetTracking(targetId); }
    public static synchronized List<AntiAirFallbackSnapshot> fallbackSnapshots(ServerLevel level) { var map = ACTIVE.get(level); if (map == null) return List.of(); return map.values().stream().filter(c -> c.phase() == AntiAirFlightPhase.FALLBACK).map(c -> new AntiAirFallbackSnapshot(c.plan().interceptorId(), c.currentPosition(), c.currentVelocity(), c.fallbackStart(), c.plan().variant(), !c.completed())).toList(); }
    public static synchronized boolean cancelForPointDefence(ServerLevel level, UUID id, UUID bullet, Vec3 hit) { var map = ACTIVE.get(level); var controller = map == null ? null : map.get(id); return controller != null && controller.cancelForPointDefence(level, bullet, hit); }
    private static synchronized void tick(ServerLevel level) { var map = ACTIVE.get(level); if (map != null) { var iterator = map.values().iterator(); while (iterator.hasNext()) { var controller = iterator.next(); controller.tick(level); if (controller.completed()) iterator.remove(); } if (map.isEmpty()) ACTIVE.remove(level); } AntiAirTargetClaimRegistry.prune(level); }
    private static synchronized void stop(MinecraftServer server) { ACTIVE.clear(); AntiAirTargetClaimRegistry.clearAll(); }
}