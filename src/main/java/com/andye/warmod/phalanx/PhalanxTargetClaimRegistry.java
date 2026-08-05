package com.andye.warmod.phalanx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * Cooperative, sticky target ownership for Phalanx turrets.
 *
 * Claims remain advisory: normal target selection balances coverage, while an
 * imminent local threat may deliberately receive every available turret.
 */
public final class PhalanxTargetClaimRegistry {
    /**
     * Long enough to survive temporary block-tick ordering or chunk activity
     * gaps, while still clearing abandoned claims without retaining turrets.
     */
    private static final long CLAIM_TIMEOUT_TICKS = 100L;

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
        state(level).byTurret.put(turretId, new Claim(targetId, gameTime));
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

    public static synchronized int claimCount(
        final ServerLevel level,
        final UUID targetId,
        final long gameTime
    ) {
        cleanup(level, gameTime);
        State state = STATES.get(level);

        if (state == null) {
            return 0;
        }

        int count = 0;

        for (Claim claim : state.byTurret.values()) {
            if (claim.targetId().equals(targetId)) {
                count++;
            }
        }

        return count;
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

    private record Claim(UUID targetId, long refreshedGameTime) {
    }

    private static final class State {
        private final Map<UUID, Claim> byTurret = new HashMap<>();
    }
}
