package com.andye.warmod.silo;

import com.andye.warmod.WarMod;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MissileSiloLaunchService {
    private static final Map<ServerLevel, LinkedHashMap<UUID, MissileSiloLaunchRequest>> PENDING = new WeakHashMap<>();

    private MissileSiloLaunchService() {
    }

    public static synchronized MissileSiloLaunchResult requestLaunch(final ServerLevel level,
        final MissileSiloBlockEntity silo, final MissileSiloLaunchTrigger trigger,
        final @Nullable UUID triggeringPlayerId, final @Nullable String triggeringPlayerName,
        final @Nullable TargetCoordinates targetOverride) {
        LinkedHashMap<UUID, MissileSiloLaunchRequest> requests = PENDING.computeIfAbsent(level,
            ignored -> new LinkedHashMap<>());
        if (requests.size() >= MissileSiloConstants.MAX_PENDING_LAUNCHES_PER_LEVEL)
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.TOO_MANY_PENDING_LAUNCHES, "Too many pending silo launches");
        if (silo.pendingLaunchRequestId() != null || silo.siloState() == com.andye.warmod.block.MissileSiloState.LAUNCHING
            || silo.siloState() == com.andye.warmod.block.MissileSiloState.COOLDOWN
            || silo.siloState() == com.andye.warmod.block.MissileSiloState.RELOADING)
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.BUSY, "Silo is busy");
        if (!MissileSiloStructure.isComplete(level, silo.getBlockPos(), silo.facing()))
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.INVALID_STRUCTURE, "Silo structure is incomplete");
        TargetCoordinates target = trigger == MissileSiloLaunchTrigger.REDSTONE ? silo.storedTarget() : targetOverride;
        if (target == null || !target.isValid() || !target.dimension().equals(level.dimension())
            || !level.getWorldBorder().isWithinBounds(target.position()) || level.isOutsideBuildHeight(net.minecraft.core.BlockPos.containing(target.position())))
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.INVALID_TARGET, "Silo target is invalid");
        if (silo.missileStack().isEmpty())
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.NO_AMMUNITION, "Silo has no ammunition");
        UUID requestId = UUID.randomUUID();
        var reserved = silo.reserveOne(requestId);
        if (reserved == null) return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.BUSY, "Silo is busy");
        WarheadPayloadType payload = MissilePayloadItems.payloadType(reserved).orElse(null);
        if (payload == null) {
            silo.restoreReserved();
            return MissileSiloLaunchResult.failed(MissileSiloLaunchFailure.NO_AMMUNITION, "Unsupported silo ammunition");
        }
        Set<ChunkPos> tickets = IcbmChunkTicketRegistry.window(IcbmChunkTicketRegistry.chunk(target.position()), 2);
        IcbmChunkTicketRegistry.acquireAll(level, tickets);
        MissileSiloLaunchRequest request = new MissileSiloLaunchRequest(requestId, silo.siloId(), silo.getBlockPos(),
            trigger, triggeringPlayerId, triggeringPlayerName, target, payload, level.getGameTime(), tickets);
        requests.put(requestId, request);
        if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("Silo {} reserved {}, request={}",
            silo.siloId(), payload.serializedName(), requestId);
        return MissileSiloLaunchResult.accepted(requestId);
    }

    public static synchronized void tick(final ServerLevel level) {
        LinkedHashMap<UUID, MissileSiloLaunchRequest> requests = PENDING.get(level);
        if (requests == null) return;
        Iterator<MissileSiloLaunchRequest> iterator = requests.values().iterator();
        while (iterator.hasNext()) {
            MissileSiloLaunchRequest request = iterator.next();
            MissileSiloBlockEntity silo = level.getBlockEntity(request.siloCentre()) instanceof MissileSiloBlockEntity found ? found : null;
            if (silo == null || !silo.siloId().equals(request.siloId())) {
                IcbmChunkTicketRegistry.releaseAll(level, request.temporaryTickets());
                iterator.remove();
                continue;
            }
            long waited = level.getGameTime() - request.creationGameTime();
            if (waited >= MissileSiloConstants.TARGET_PREPARATION_TIMEOUT_TICKS) {
                fail(level, silo, request, "target area did not load in time");
                iterator.remove();
                continue;
            }
            if (!allLoaded(level, request.temporaryTickets())) continue;
            Vec3 origin = new Vec3(silo.getBlockPos().getX() + 0.5, silo.getBlockPos().getY() + 0.9,
                silo.getBlockPos().getZ() + 0.5);
            UUID owner = request.trigger() == MissileSiloLaunchTrigger.REDSTONE ? silo.ownerPlayerId() : request.triggeringPlayerId();
            java.util.Set<net.minecraft.core.BlockPos> ignored = new java.util.LinkedHashSet<>(MissileSiloStructure.positions(silo.getBlockPos(), silo.facing()));
            int guidanceTier = com.andye.warmod.block.MissileSiloGuidanceFrameStructure.installedTier(level, silo.getBlockPos(), silo.facing());
            for (int tier = 1; tier <= guidanceTier; tier++) for (var layerPart : com.andye.warmod.block.MissileSiloGuidanceFrameLayer.values())
                for (var framePart : com.andye.warmod.block.MissileSiloGuidanceFramePart.values()) ignored.add(
                    com.andye.warmod.block.MissileSiloGuidanceFrameStructure.position(silo.getBlockPos(), silo.facing(), tier, layerPart, framePart));
            MissileSiloCollisionContext collision = new MissileSiloCollisionContext(silo.siloId(), silo.getBlockPos(),
                ignored, MissileSiloConstants.MISSILE_COLLISION_WIDTH, MissileSiloConstants.MISSILE_COLLISION_HEIGHT);
            var result = IcbmLaunchService.launchFromSilo(level, owner, request.triggeringPlayerName(), origin,
                request.target().position(), request.payloadType(), collision, silo.siloId(), silo.getBlockPos(), guidanceTier);
            IcbmChunkTicketRegistry.releaseAll(level, request.temporaryTickets());
            iterator.remove();
            if (result.isPresent()) {
                silo.launchAccepted(result.get().flightPlan().missileId());
                if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("Silo {} launched missile {}",
                    silo.siloId(), result.get().flightPlan().missileId());
            } else silo.fail("flight plan rejected");
        }
        if (requests.isEmpty()) PENDING.remove(level);
    }

    public static synchronized void cancel(final ServerLevel level, final UUID siloId, final String reason) {
        LinkedHashMap<UUID, MissileSiloLaunchRequest> requests = PENDING.get(level);
        if (requests == null) return;
        requests.values().removeIf(request -> {
            if (!request.siloId().equals(siloId)) return false;
            IcbmChunkTicketRegistry.releaseAll(level, request.temporaryTickets());
            return true;
        });
        if (requests.isEmpty()) PENDING.remove(level);
    }

    public static synchronized void stop() {
        for (Map.Entry<ServerLevel, LinkedHashMap<UUID, MissileSiloLaunchRequest>> entry : PENDING.entrySet())
            for (MissileSiloLaunchRequest request : entry.getValue().values()) {
                IcbmChunkTicketRegistry.releaseAll(entry.getKey(), request.temporaryTickets());
                if (entry.getKey().getBlockEntity(request.siloCentre()) instanceof MissileSiloBlockEntity silo) silo.restoreReserved();
            }
        PENDING.clear();
    }

    private static boolean allLoaded(final ServerLevel level, final Set<ChunkPos> positions) {
        for (ChunkPos position : positions) if (!level.getChunkSource().hasChunk(position.x(), position.z())) return false;
        return true;
    }

    private static void fail(final ServerLevel level, final MissileSiloBlockEntity silo,
        final MissileSiloLaunchRequest request, final String reason) {
        IcbmChunkTicketRegistry.releaseAll(level, request.temporaryTickets());
        silo.fail(reason);
        if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("Silo {} launch failed: {}", silo.siloId(), reason);
    }
}
