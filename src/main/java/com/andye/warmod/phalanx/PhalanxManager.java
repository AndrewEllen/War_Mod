package com.andye.warmod.phalanx;

import com.andye.warmod.block.PhalanxStructure;
import com.andye.warmod.block.entity.PhalanxBlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;

public final class PhalanxManager {
    private static final Map<ServerLevel, Map<UUID, PhalanxBlockEntity>> ACTIVE =
        new WeakHashMap<>();

    private static boolean registered;

    private PhalanxManager() {
    }

    public static void registerLifecycle() {
        if (registered) {
            return;
        }

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ACTIVE.clear();
            PhalanxChunkTicketManager.clear();
        });

        registered = true;
    }

    public static synchronized boolean canPlace(final ServerLevel level) {
        return ACTIVE.getOrDefault(level, Map.of()).size()
            < PhalanxConstants.MAX_TURRETS_PER_LEVEL;
    }

    public static synchronized PhalanxRegistrationResult register(
        final ServerLevel level,
        final PhalanxBlockEntity turret
    ) {
        if (!PhalanxStructure.complete(level, turret.getBlockPos())) {
            return PhalanxRegistrationResult.rejected(
                PhalanxRegistrationFailure.INCOMPLETE_STRUCTURE
            );
        }

        Map<UUID, PhalanxBlockEntity> map =
            ACTIVE.computeIfAbsent(level, ignored -> new LinkedHashMap<>());

        PhalanxBlockEntity existingById = map.get(turret.turretId());

        if (existingById != null) {
            // Permit a block-entity instance replacement at the same controller,
            // for example after a chunk reload.
            if (existingById.getBlockPos().equals(turret.getBlockPos())) {
                if (!PhalanxChunkTicketManager.register(
                    level,
                    turret.turretId(),
                    turret.getBlockPos()
                )) {
                    return PhalanxRegistrationResult.rejected(
                        PhalanxRegistrationFailure.MANAGER_REJECTED
                    );
                }

                map.put(turret.turretId(), turret);
                return PhalanxRegistrationResult.success();
            }

            return PhalanxRegistrationResult.rejected(
                PhalanxRegistrationFailure.DUPLICATE_UUID
            );
        }

        if (map.size() >= PhalanxConstants.MAX_TURRETS_PER_LEVEL) {
            return PhalanxRegistrationResult.rejected(
                PhalanxRegistrationFailure.LIMIT_REACHED
            );
        }

        for (PhalanxBlockEntity registeredTurret : map.values()) {
            if (registeredTurret.getBlockPos().equals(turret.getBlockPos())) {
                return PhalanxRegistrationResult.rejected(
                    PhalanxRegistrationFailure.POSITION_OCCUPIED
                );
            }
        }

        if (!PhalanxChunkTicketManager.register(
            level,
            turret.turretId(),
            turret.getBlockPos()
        )) {
            return PhalanxRegistrationResult.rejected(
                PhalanxRegistrationFailure.MANAGER_REJECTED
            );
        }

        try {
            map.put(turret.turretId(), turret);
            return PhalanxRegistrationResult.success();
        } catch (RuntimeException exception) {
            PhalanxChunkTicketManager.unregister(level, turret.turretId());

            if (map.isEmpty()) {
                ACTIVE.remove(level);
            }

            throw exception;
        }
    }

    public static synchronized void unregister(
        final ServerLevel level,
        final PhalanxBlockEntity turret
    ) {
        PhalanxChunkTicketManager.unregister(level, turret.turretId());

        Map<UUID, PhalanxBlockEntity> map = ACTIVE.get(level);
        if (map == null) {
            return;
        }

        map.remove(turret.turretId());

        if (map.isEmpty()) {
            ACTIVE.remove(level);
        }
    }
}
