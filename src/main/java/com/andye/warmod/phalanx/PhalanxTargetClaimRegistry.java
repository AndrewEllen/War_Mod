package com.andye.warmod.phalanx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * Short-lived cooperative target claims for Phalanx turrets.
 *
 * Claims are advisory rather than exclusive. They spread comparable threats
 * across nearby turrets, while the selector may still put several turrets on
 * one urgent target once other suitable threats already have coverage.
 */
public final class PhalanxTargetClaimRegistry {
    private static final long CLAIM_TIMEOUT_TICKS = 12L;

    private static final Map<ServerLevel, State> STATES =
        new WeakHashMap<>();

    private PhalanxTargetClaimRegistry() {
    }

    public static synchronized void claim(
        final ServerLevel level,
        final UUID turretId,
        final UUID targetId,
        final long gameTime
    ) {
        cleanup(level, gameTime);

        state(level).byTurret.put(
            turretId,
            new Claim(targetId, gameTime)
        );
    }

    public static synchronized void release(
        final ServerLevel level,
        final UUID turretId
    ) {
        State state = STATES.get(level);

        if (state == null) {
            return;
        }

        state.byTurret.remove(turretId);

        if (state.byTurret.isEmpty()) {
            STATES.remove(level);
        }
    }

    public static synchronized int claimCountExcluding(
        final ServerLevel level,
        final UUID targetId,
        final UUID excludedTurretId,
        final long gameTime
    ) {
        cleanup(level, gameTime);

        State state = STATES.get(level);

        if (state == null) {
            return 0;
        }

        int count = 0;

        for (Map.Entry<UUID, Claim> entry : state.byTurret.entrySet()) {
            if (!entry.getKey().equals(excludedTurretId)
                && entry.getValue().targetId().equals(targetId)) {
                count++;
            }
        }

        return count;
    }

    public static synchronized void clearAll() {
        STATES.clear();
    }

    private static void cleanup(
        final ServerLevel level,
        final long gameTime
    ) {
        State state = STATES.get(level);

        if (state == null) {
            return;
        }

        Iterator<Map.Entry<UUID, Claim>> iterator =
            state.byTurret.entrySet().iterator();

        while (iterator.hasNext()) {
            Claim claim = iterator.next().getValue();

            if (gameTime - claim.refreshedGameTime() > CLAIM_TIMEOUT_TICKS) {
                iterator.remove();
            }
        }

        if (state.byTurret.isEmpty()) {
            STATES.remove(level);
        }
    }

    private static State state(final ServerLevel level) {
        return STATES.computeIfAbsent(level, ignored -> new State());
    }

    private record Claim(
        UUID targetId,
        long refreshedGameTime
    ) {
    }

    private static final class State {
        private final Map<UUID, Claim> byTurret = new HashMap<>();
    }
}
