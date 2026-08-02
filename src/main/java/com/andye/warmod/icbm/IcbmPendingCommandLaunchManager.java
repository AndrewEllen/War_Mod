package com.andye.warmod.icbm;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class IcbmPendingCommandLaunchManager {
	private static final long TIMEOUT_TICKS = 200L;
	private static final Map<ServerLevel, LinkedHashMap<UUID, IcbmPendingCommandLaunch>> PENDING = new WeakHashMap<>();
	private static boolean registered;
	private IcbmPendingCommandLaunchManager() { }

	public static void register() {
		if (registered) return;
		ServerTickEvents.END_LEVEL_TICK.register(IcbmPendingCommandLaunchManager::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(IcbmPendingCommandLaunchManager::stop);
		registered = true;
	}

	public static synchronized boolean queue(final ServerLevel level, final ServerPlayer player, final Vec3 target,
		final @Nullable Vec3 requestedLaunch, final WarheadPayloadType payloadType) {
		IcbmLaunchService.PreparedCommandLaunch prepared = IcbmLaunchService.prepareCommandLaunch(
			level, player, target, requestedLaunch, payloadType).orElse(null);
		if (prepared == null) return false;
		Set<ChunkPos> tickets = IcbmChunkTicketRegistry.window(IcbmChunkTicketRegistry.chunk(prepared.launchPosition()), 1);
		IcbmChunkTicketRegistry.addWindow(tickets, IcbmChunkTicketRegistry.chunk(target), 2);
		IcbmChunkTicketRegistry.acquireAll(level, tickets);
		IcbmPendingCommandLaunch request = new IcbmPendingCommandLaunch(prepared.requestId(), player.getUUID(),
			level.dimension(), target, requestedLaunch, prepared.launchPosition(), payloadType, level.getGameTime(),
			prepared.visualSeed(), tickets);
		PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(request.requestId(), request);
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
			"ICBM command launch {} queued: launchChunk={}, targetChunk={}", request.requestId(),
			IcbmChunkTicketRegistry.chunk(request.launchPosition()), IcbmChunkTicketRegistry.chunk(request.target()));
		return true;
	}

	private static synchronized void tick(final ServerLevel level) {
		LinkedHashMap<UUID, IcbmPendingCommandLaunch> requests = PENDING.get(level);
		if (requests == null) return;
		long now = level.getGameTime();
		Iterator<IcbmPendingCommandLaunch> iterator = requests.values().iterator();
		while (iterator.hasNext()) {
			IcbmPendingCommandLaunch request = iterator.next();
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.playerId());
			if (player == null) { fail(level, request, null, "player disconnected"); iterator.remove(); continue; }
			if (player.level() != level || !player.level().dimension().equals(request.dimension())) {
				fail(level, request, player, "player changed dimension"); iterator.remove(); continue;
			}
			if (now < request.creationGameTime()) { fail(level, request, player, "server game time rolled back"); iterator.remove(); continue; }
			if (now - request.creationGameTime() >= TIMEOUT_TICKS) {
				fail(level, request, player, "launch or target area did not load in time"); iterator.remove(); continue;
			}
			if (!IcbmLaunchService.pendingRequestStillValid(level, request)) {
				fail(level, request, player, "request became invalid"); iterator.remove(); continue;
			}
			if (!allLoaded(level, request.temporaryTickets())) continue;
			long waited = now - request.creationGameTime();
			if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
				"ICBM command launch {} chunks ready after {} ticks", request.requestId(), waited);
			var result = IcbmLaunchService.completePendingCommandLaunch(level, player, request);
			IcbmChunkTicketRegistry.releaseAll(level, request.temporaryTickets());
			iterator.remove();
			if (result.isPresent()) player.sendSystemMessage(Component.literal("ICBM launched toward " + format(request.target())));
			else {
				player.sendSystemMessage(Component.literal("ICBM launch failed: flight plan could not be constructed"));
				logFailure(request, "flight plan construction or central manager rejection");
			}
		}
		if (requests.isEmpty()) PENDING.remove(level);
	}

	private static boolean allLoaded(final ServerLevel level, final Set<ChunkPos> positions) {
		for (ChunkPos position : positions) if (!level.getChunkSource().hasChunk(position.x(), position.z())) return false;
		return true;
	}

	private static void fail(final ServerLevel level, final IcbmPendingCommandLaunch request,
		final @Nullable ServerPlayer player, final String reason) {
		IcbmChunkTicketRegistry.releaseAll(level, request.temporaryTickets());
		if (player != null) {
			String message = reason.equals("launch or target area did not load in time")
				? "ICBM launch failed: launch or target area did not load in time"
				: "ICBM launch failed: " + reason;
			player.sendSystemMessage(Component.literal(message));
		}
		logFailure(request, reason);
	}

	private static void logFailure(final IcbmPendingCommandLaunch request, final String reason) {
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info(
			"ICBM command launch {} failed: {}", request.requestId(), reason);
	}

	private static synchronized void stop(final MinecraftServer server) {
		for (Map.Entry<ServerLevel, LinkedHashMap<UUID, IcbmPendingCommandLaunch>> entry : new ArrayList<>(PENDING.entrySet()))
			for (IcbmPendingCommandLaunch request : entry.getValue().values())
				IcbmChunkTicketRegistry.releaseAll(entry.getKey(), request.temporaryTickets());
		PENDING.clear();
	}

	private static String format(final Vec3 position) {
		return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", position.x, position.y, position.z);
	}
}