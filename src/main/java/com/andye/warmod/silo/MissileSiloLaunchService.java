package com.andye.warmod.silo;

import com.andye.warmod.WarMod;
import com.andye.warmod.block.MissileSiloGuidanceFrameStructure;
import com.andye.warmod.block.MissileSiloStructure;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.icbm.IcbmChunkTicketRegistry;
import com.andye.warmod.icbm.IcbmLaunchService;
import com.andye.warmod.item.component.TargetCoordinates;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MissileSiloLaunchService {
    private static final Map<ServerLevel, LinkedHashMap<UUID, MissileSiloLaunchRequest>> PENDING =
        new WeakHashMap<>();

    private MissileSiloLaunchService() { }

    public static synchronized MissileSiloLaunchResult requestLaunch(final ServerLevel level,
        final MissileSiloBlockEntity silo, final MissileSiloLaunchTrigger trigger,
        final @Nullable UUID triggeringPlayerId, final @Nullable String triggeringPlayerName,
        final @Nullable TargetCoordinates targetOverride) {
        LinkedHashMap<UUID, MissileSiloLaunchRequest> requests =
            PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (requests.size() >= MissileSiloConstants.MAX_PENDING_LAUNCHES_PER_LEVEL) {
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.TOO_MANY_PENDING_LAUNCHES,
                "Too many pending silo launches");
        }
        if (silo.pendingLaunchRequestId() != null
            || silo.siloState() == com.andye.warmod.block.MissileSiloState.LAUNCHING
            || silo.siloState() == com.andye.warmod.block.MissileSiloState.COOLDOWN
            || silo.siloState() == com.andye.warmod.block.MissileSiloState.RELOADING) {
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.BUSY, "Silo is busy");
        }
        if (!MissileSiloStructure.isComplete(level, silo.getBlockPos(), silo.facing())) {
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.INVALID_STRUCTURE,
                "Silo structure is incomplete");
        }
        MissileSiloGuidanceFrameStructure.cleanupLegacy(level, silo.getBlockPos());
        TargetCoordinates target =
            trigger == MissileSiloLaunchTrigger.REDSTONE ? silo.storedTarget() : targetOverride;
        if (target == null || !target.isValid() || !target.dimension().equals(level.dimension())
            || !level.getWorldBorder().isWithinBounds(target.position())
            || level.isOutsideBuildHeight(net.minecraft.core.BlockPos.containing(target.position()))) {
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.INVALID_TARGET,
                "Silo target is invalid");
        }
        if (silo.missileStack().isEmpty()) {
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.NO_AMMUNITION,
                "Silo has no ammunition");
        }

        UUID requestId = UUID.randomUUID();
        var reserved = silo.reserveOne(requestId);
        if (reserved == null) {
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.BUSY, "Silo is busy");
        }
        WarheadPayloadType payload = MissilePayloadItems.payloadType(reserved).orElse(null);
        if (payload == null) {
            silo.restoreReserved();
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.NO_AMMUNITION,
                "Unsupported silo ammunition");
        }

        Set<ChunkPos> tickets =
            IcbmChunkTicketRegistry.window(IcbmChunkTicketRegistry.chunk(target.position()), 2);
        IcbmChunkTicketRegistry.acquireAll(level, tickets);
        MissileSiloLaunchRequest request = new MissileSiloLaunchRequest(
            requestId, silo.siloId(), silo.getBlockPos(), trigger, triggeringPlayerId,
            triggeringPlayerName == null ? "SERVER" : triggeringPlayerName,
            target, payload, level.getGameTime(), tickets);
        requests.put(requestId, request);
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            WarMod.LOGGER.info("Silo {} preparation queued: target={}",
                silo.siloId(), target.position());
        }
        return MissileSiloLaunchResult.accepted(requestId);
    }

    public static synchronized void tick(final ServerLevel level) {
        LinkedHashMap<UUID, MissileSiloLaunchRequest> requests = PENDING.get(level);
        if (requests == null) return;
        Iterator<MissileSiloLaunchRequest> iterator = requests.values().iterator();
        while (iterator.hasNext()) {
            MissileSiloLaunchRequest request = iterator.next();
            MissileSiloBlockEntity silo =
                level.getBlockEntity(request.siloCentre()) instanceof MissileSiloBlockEntity found
                    ? found : null;
            if (silo == null || !silo.siloId().equals(request.siloId())) {
                release(level, request);
                notifyRequester(level, request, "Missile launch failed: Silo was removed");
                iterator.remove();
                continue;
            }
            long waited = level.getGameTime() - request.creationGameTime();
            if (waited >= MissileSiloConstants.TARGET_PREPARATION_TIMEOUT_TICKS) {
                fail(level, silo, request, "Target area did not load");
                iterator.remove();
                continue;
            }
            if (!allLoaded(level, request.temporaryTickets())) continue;
            if (!MissileSiloStructure.isComplete(level, silo.getBlockPos(), silo.facing())) {
                fail(level, silo, request, "Silo structure changed");
                iterator.remove();
                continue;
            }

            int guidanceTier =
                MissileSiloGuidanceFrameStructure.installedTier(level, silo.getBlockPos(), silo.facing());
            java.util.Set<net.minecraft.core.BlockPos> ignored =
                new java.util.LinkedHashSet<>(MissileSiloStructure.positions(silo.getBlockPos(), silo.facing()));
            ignored.addAll(MissileSiloGuidanceFrameStructure.positions(silo.getBlockPos(), silo.facing()));
            Vec3 origin = new Vec3(silo.getBlockPos().getX() + 0.5,
                silo.getBlockPos().getY() + 1.4, silo.getBlockPos().getZ() + 0.5);
            UUID owner = request.trigger() == MissileSiloLaunchTrigger.REDSTONE
                ? silo.ownerPlayerId() : request.triggeringPlayerId();
            MissileSiloCollisionContext collision = new MissileSiloCollisionContext(
                silo.siloId(), silo.getBlockPos(), ignored,
                MissileSiloConstants.MISSILE_COLLISION_WIDTH,
                MissileSiloConstants.MISSILE_COLLISION_HEIGHT);
            var result = IcbmLaunchService.launchFromSilo(level, owner, request.triggeringPlayerName(),
                origin, request.target().position(), request.payloadType(), collision,
                silo.siloId(), silo.getBlockPos(), guidanceTier);
            release(level, request);
            iterator.remove();
            if (result.isPresent()) {
                silo.launchAccepted(result.get().flightPlan().missileId());
                notifyRequester(level, request, "Missile launch accepted");
                if (SharedConstants.IS_RUNNING_IN_IDE) {
                    WarMod.LOGGER.info("Silo {} launch accepted after {} ticks",
                        silo.siloId(), waited);
                }
            } else {
                failWithoutRelease(level, silo, request, "Flight plan could not be constructed");
            }
        }
        if (requests.isEmpty()) PENDING.remove(level);
    }

    public static synchronized void cancel(final ServerLevel level, final UUID siloId, final String reason) {
        LinkedHashMap<UUID, MissileSiloLaunchRequest> requests = PENDING.get(level);
        if (requests == null) return;
        Iterator<MissileSiloLaunchRequest> iterator = requests.values().iterator();
        while (iterator.hasNext()) {
            MissileSiloLaunchRequest request = iterator.next();
            if (!request.siloId().equals(siloId)) continue;
            release(level, request);
            if (level.getBlockEntity(request.siloCentre()) instanceof MissileSiloBlockEntity silo) {
                silo.fail(reason);
            }
            notifyRequester(level, request, "Missile launch failed: " + sentence(reason));
            iterator.remove();
        }
        if (requests.isEmpty()) PENDING.remove(level);
    }

    public static synchronized void stop() {
        for (Map.Entry<ServerLevel, LinkedHashMap<UUID, MissileSiloLaunchRequest>> entry : PENDING.entrySet()) {
            for (MissileSiloLaunchRequest request : entry.getValue().values()) {
                release(entry.getKey(), request);
                if (entry.getKey().getBlockEntity(request.siloCentre())
                    instanceof MissileSiloBlockEntity silo) silo.restoreReserved();
            }
        }
        PENDING.clear();
    }

    private static boolean allLoaded(final ServerLevel level, final Set<ChunkPos> positions) {
        for (ChunkPos position : positions) {
            if (level.getChunkSource().getChunkNow(position.x(), position.z()) == null) return false;
        }
        return true;
    }

    private static void fail(final ServerLevel level, final MissileSiloBlockEntity silo,
        final MissileSiloLaunchRequest request, final String reason) {
        release(level, request);
        failWithoutRelease(level, silo, request, reason);
    }

    private static void failWithoutRelease(final ServerLevel level, final MissileSiloBlockEntity silo,
        final MissileSiloLaunchRequest request, final String reason) {
        silo.fail(reason);
        notifyRequester(level, request, "Missile launch failed: " + reason);
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            WarMod.LOGGER.info("Silo {} launch failed: {}", silo.siloId(), reason);
        }
    }

    private static void release(final ServerLevel level, final MissileSiloLaunchRequest request) {
        IcbmChunkTicketRegistry.releaseAll(level, request.temporaryTickets());
    }

    private static void notifyRequester(final ServerLevel level,
        final MissileSiloLaunchRequest request, final String message) {
        if (request.triggeringPlayerId() == null) return;
        ServerPlayer player =
            level.getServer().getPlayerList().getPlayer(request.triggeringPlayerId());
        if (player != null) player.sendSystemMessage(Component.literal(message));
    }

    private static String sentence(final String text) {
        if (text == null || text.isBlank()) return "Unknown failure";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}