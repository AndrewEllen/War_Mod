package com.andye.warmod.silo;

import com.andye.warmod.antiair.AntiAirFlightControllerManager;
import com.andye.warmod.antiair.AntiAirLaunchDecision;
import com.andye.warmod.antiair.AntiAirLaunchMode;
import com.andye.warmod.antiair.AntiAirLaunchPlanner;
import com.andye.warmod.antiair.AntiAirLaunchService;
import com.andye.warmod.block.MissileSiloBlock;
import com.andye.warmod.block.MissileSiloGuidanceFrameStructure;
import com.andye.warmod.block.MissileSiloState;
import com.andye.warmod.block.MissileSiloStructure;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.icbm.IcbmChunkTicketRegistry;
import com.andye.warmod.icbm.IcbmLaunchService;
import com.andye.warmod.item.component.TargetCoordinates;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class MissileSiloLaunchService {
    private static final Map<ServerLevel, LinkedHashMap<UUID, MissileSiloLaunchRequest>> PENDING =
            new WeakHashMap<>();

    private MissileSiloLaunchService() {}

    public static synchronized MissileSiloLaunchResult requestLaunch(
            ServerLevel level,
            MissileSiloBlockEntity silo,
            MissileSiloLaunchTrigger trigger,
            @Nullable UUID player,
            @Nullable String name,
            @Nullable TargetCoordinates override) {
        if (silo.pendingLaunchRequestId() != null
                || EnumSet.of(
                                MissileSiloState.LAUNCHING,
                                MissileSiloState.COOLDOWN,
                                MissileSiloState.RELOADING)
                        .contains(silo.siloState()))
            return fail(MissileSiloLaunchFailure.BUSY, "Silo is busy");
        if (!MissileSiloStructure.isComplete(level, silo.getBlockPos(), silo.facing()))
            return fail(MissileSiloLaunchFailure.INVALID_STRUCTURE, "Silo structure is incomplete");
        SiloMissileType type = MissilePayloadItems.missileType(silo.missileStack()).orElse(null);
        if (type == null)
            return fail(MissileSiloLaunchFailure.NO_AMMUNITION, "Silo has no supported ammunition");
        return type.role() == SiloMissileRole.INTERCEPTOR
                ? interceptor(level, silo, trigger, player, name, type)
                : strategic(level, silo, trigger, player, name, override, type);
    }

    private static MissileSiloLaunchResult interceptor(
            ServerLevel level,
            MissileSiloBlockEntity silo,
            MissileSiloLaunchTrigger trigger,
            @Nullable UUID player,
            @Nullable String name,
            SiloMissileType type) {
        if (!AntiAirFlightControllerManager.canAccept(level))
            return failInterceptor(
                    silo,
                    MissileSiloLaunchFailure.INTERCEPTOR_CONTROLLER_REJECTED,
                    "Interceptor controller limit reached");
        var requests = PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (requests.size() >= MissileSiloConstants.MAX_PENDING_LAUNCHES_PER_LEVEL)
            return fail(
                    MissileSiloLaunchFailure.TOO_MANY_PENDING_LAUNCHES,
                    "Too many pending silo launches");
        UUID requestId = UUID.randomUUID();
        if (silo.reserveOne(requestId) == null)
            return fail(MissileSiloLaunchFailure.BUSY, "Silo is busy");
        requests.put(
                requestId,
                new MissileSiloLaunchRequest(
                        requestId,
                        silo.siloId(),
                        silo.getBlockPos(),
                        trigger,
                        player,
                        name == null ? "SERVER" : name,
                        type,
                        null,
                        null,
                        null,
                        AntiAirLaunchMode.NO_TARGET_ASCENT,
                        level.getGameTime(),
                        Set.of()));
        return MissileSiloLaunchResult.accepted(requestId);
    }

    private static MissileSiloLaunchResult strategic(
            ServerLevel level,
            MissileSiloBlockEntity silo,
            MissileSiloLaunchTrigger trigger,
            @Nullable UUID player,
            @Nullable String name,
            @Nullable TargetCoordinates override,
            SiloMissileType type) {
        var requests = PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (requests.size() >= MissileSiloConstants.MAX_PENDING_LAUNCHES_PER_LEVEL)
            return fail(
                    MissileSiloLaunchFailure.TOO_MANY_PENDING_LAUNCHES,
                    "Too many pending silo launches");
        TargetCoordinates target =
                trigger == MissileSiloLaunchTrigger.REDSTONE ? silo.storedTarget() : override;
        if (target == null) target = silo.storedTarget();
        if (target == null
                || !target.isValid()
                || !target.dimension().equals(level.dimension())
                || !level.getWorldBorder().isWithinBounds(target.position())
                || level.isOutsideBuildHeight(
                        net.minecraft.core.BlockPos.containing(target.position())))
            return fail(MissileSiloLaunchFailure.INVALID_TARGET, "Silo target is invalid");
        UUID id = UUID.randomUUID();
        if (silo.reserveOne(id) == null) return fail(MissileSiloLaunchFailure.BUSY, "Silo is busy");
        Set<ChunkPos> tickets =
                IcbmChunkTicketRegistry.window(IcbmChunkTicketRegistry.chunk(target.position()), 2);
        IcbmChunkTicketRegistry.acquireAll(level, tickets);
        requests.put(
                id,
                new MissileSiloLaunchRequest(
                        id,
                        silo.siloId(),
                        silo.getBlockPos(),
                        trigger,
                        player,
                        name == null ? "SERVER" : name,
                        type,
                        target,
                        null,
                        null,
                        null,
                        level.getGameTime(),
                        tickets));
        return MissileSiloLaunchResult.accepted(id);
    }

    public static synchronized boolean isPending(ServerLevel level, @Nullable UUID requestId) {
        return requestId != null
                && PENDING.containsKey(level)
                && PENDING.get(level).containsKey(requestId);
    }

    public static synchronized void tick(ServerLevel level) {
        var requests = PENDING.get(level);
        if (requests == null) return;
        var iterator = requests.values().iterator();
        while (iterator.hasNext()) {
            var request = iterator.next();
            var silo =
                    level.getBlockEntity(request.siloCentre())
                                            instanceof MissileSiloBlockEntity entity
                                    && entity.siloId().equals(request.siloId())
                            ? entity
                            : null;
            if (silo == null) {
                release(level, request);
                iterator.remove();
                continue;
            }
            if (!request.requestId().equals(silo.pendingLaunchRequestId())
                    || silo.reservedMissile().isEmpty()) {
                release(level, request);
                iterator.remove();
                continue;
            }
            if (level.getGameTime() - request.creationGameTime()
                    >= MissileSiloConstants.TARGET_PREPARATION_TIMEOUT_TICKS) {
                release(level, request);
                silo.fail("Target area did not load");
                iterator.remove();
                continue;
            }
            if (level.getGameTime() - request.creationGameTime()
                    < MissileSiloConstants.OPENING_ANIMATION_TICKS) continue;
            if (!allLoaded(level, request.temporaryTickets())) continue;
            if (!MissileSiloStructure.isComplete(level, silo.getBlockPos(), silo.facing())) {
                release(level, request);
                silo.fail("Silo structure changed");
                iterator.remove();
                continue;
            }
            int tier = MissilePayloadItems.guidanceTier(silo.reservedMissile());
            UUID owner =
                    request.trigger() == MissileSiloLaunchTrigger.REDSTONE
                            ? silo.ownerPlayerId()
                            : request.triggeringPlayerId();
            if (request.missileType().role() == SiloMissileRole.INTERCEPTOR) {
                // Solve against current target motion after the doors open, not a stale lock from
                // the button click.
                Vec3 origin = origin(silo);
                AntiAirLaunchDecision decision =
                        AntiAirLaunchPlanner.plan(
                                        level,
                                        origin,
                                        AntiAirLaunchService.estimatedBurnout(level, origin))
                                .orElse(null);
                var launch =
                        decision == null
                                ? java.util.Optional
                                        .<com.andye.warmod.antiair.AntiAirLaunchResult>empty()
                                : AntiAirLaunchService.launchFromSilo(
                                        level,
                                        owner,
                                        request.triggeringPlayerName(),
                                        silo.siloId(),
                                        silo.getBlockPos(),
                                        origin,
                                        request.missileType().antiAirVariant().orElseThrow(),
                                        decision,
                                        tier,
                                        collision(silo));
                release(level, request);
                iterator.remove();
                if (launch.isPresent()) {
                    silo.launchAccepted(launch.get().flightPlan().interceptorId());
                    notify(level, request.triggeringPlayerId(), "Interceptor launch accepted");
                } else {
                    silo.fail("Interceptor route could not be constructed");
                    notify(level, request.triggeringPlayerId(), "Interceptor launch failed");
                }
                continue;
            }
            var result =
                    IcbmLaunchService.launchFromSilo(
                            level,
                            owner,
                            request.triggeringPlayerName(),
                            origin(silo),
                            request.strategicTarget().position(),
                            request.missileType().payloadType().orElseThrow(),
                            request.missileType().yield().orElseThrow(),
                            request.missileType().deliveryMode(),
                            collision(silo),
                            silo.siloId(),
                            silo.getBlockPos(),
                            tier);
            release(level, request);
            iterator.remove();
            if (result.isPresent()) {
                com.andye.warmod.warhead.StrategicMissilePayloadRegistry.put(
                        result.get().flightPlan().missileId(),
                        request.missileType().strategicPayload());
                request.missileType()
                        .yield()
                        .ifPresent(
                                yield ->
                                        com.andye.warmod.warhead.WarheadYieldRegistry.put(
                                                level,
                                                result.get().flightPlan().missileId(),
                                                yield));
                silo.launchAccepted(result.get().flightPlan().missileId());
                notify(level, request.triggeringPlayerId(), "Missile launch accepted");
            } else {
                silo.fail("Flight plan could not be constructed");
                notify(level, request.triggeringPlayerId(), "Missile launch failed");
            }
        }
        if (requests.isEmpty()) PENDING.remove(level);
    }

    public static synchronized void cancel(ServerLevel level, UUID siloId, String reason) {
        var pending = PENDING.get(level);
        if (pending == null) return;
        var iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            var request = iterator.next();
            if (!request.siloId().equals(siloId)) continue;
            release(level, request);
            if (level.getBlockEntity(request.siloCentre()) instanceof MissileSiloBlockEntity silo
                    && silo.siloId().equals(request.siloId())
                    && request.requestId().equals(silo.pendingLaunchRequestId())) {
                silo.fail(reason);
            }
            iterator.remove();
        }
        if (pending.isEmpty()) PENDING.remove(level);
    }

    public static synchronized void stop() {
        for (var entry : PENDING.entrySet())
            for (var request : entry.getValue().values()) {
                release(entry.getKey(), request);
                if (entry.getKey().getBlockEntity(request.siloCentre())
                                instanceof MissileSiloBlockEntity silo
                        && silo.siloId().equals(request.siloId())
                        && request.requestId().equals(silo.pendingLaunchRequestId())) {
                    silo.restoreReserved();
                }
            }
        PENDING.clear();
    }

    private static Vec3 origin(MissileSiloBlockEntity silo) {
        return new Vec3(
                silo.getBlockPos().getX() + .5,
                silo.getBlockPos().getY() + 1.4,
                silo.getBlockPos().getZ() + .5);
    }

    private static MissileSiloCollisionContext collision(MissileSiloBlockEntity silo) {
        Set<net.minecraft.core.BlockPos> ignored =
                new LinkedHashSet<>(
                        MissileSiloStructure.positions(
                                silo.getBlockPos(),
                                silo.facing(),
                                silo.getBlockState().getValue(MissileSiloBlock.LARGE)));
        if (!silo.getBlockState().getValue(MissileSiloBlock.LARGE)) {
            ignored.addAll(
                    MissileSiloGuidanceFrameStructure.positions(silo.getBlockPos(), silo.facing()));
        }
        return new MissileSiloCollisionContext(
                silo.siloId(),
                silo.getBlockPos(),
                ignored,
                MissileSiloConstants.MISSILE_COLLISION_WIDTH,
                MissileSiloConstants.MISSILE_COLLISION_HEIGHT);
    }

    private static boolean allLoaded(ServerLevel level, Set<ChunkPos> positions) {
        for (var position : positions)
            if (level.getChunkSource().getChunkNow(position.x(), position.z()) == null)
                return false;
        return true;
    }

    private static void release(ServerLevel level, MissileSiloLaunchRequest request) {
        IcbmChunkTicketRegistry.releaseAll(level, request.temporaryTickets());
    }

    private static MissileSiloLaunchResult fail(MissileSiloLaunchFailure failure, String message) {
        return MissileSiloLaunchResult.failed(failure, message);
    }

    private static MissileSiloLaunchResult failInterceptor(
            MissileSiloBlockEntity silo, MissileSiloLaunchFailure failure, String message) {
        silo.fail(message);
        return fail(failure, message);
    }

    private static void notify(ServerLevel level, @Nullable UUID id, String message) {
        if (id == null) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        if (player != null) player.sendSystemMessage(Component.literal(message));
    }
}
