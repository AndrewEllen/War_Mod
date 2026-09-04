package com.andye.warmod.antiair;

import com.andye.warmod.WarMod;
import com.andye.warmod.defence.DefenceOwnershipSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Transient target claims used to balance initial interceptor assignment.
 *
 * Exact synchronized routes are preferred. A bounded low-probability attempt
 * may be claimed when no exact route exists, but it still has a finite powered
 * duration and can never loiter.
 */
public final class AntiAirTargetClaimRegistry {
    private static final long STALE_TIMEOUT_TICKS =
        20L * 60L * 5L;

    private static final Map<ServerLevel, Claims> BY_LEVEL =
        new WeakHashMap<>();

    private AntiAirTargetClaimRegistry() {
    }

    public static synchronized Optional<AntiAirTargetSelectionResult>
        selectAndClaim(
            final ServerLevel level,
            final UUID interceptorId,
            final Vec3 launchOrigin,
            final Vec3 burnoutPosition,
            final int guidanceTier
        ) {
        return selectAndClaim(level, interceptorId, launchOrigin, burnoutPosition, guidanceTier,
            DefenceOwnershipSnapshot.unclaimed());
    }

    public static synchronized Optional<AntiAirTargetSelectionResult>
        selectAndClaim(
            final ServerLevel level,
            final UUID interceptorId,
            final Vec3 launchOrigin,
            final Vec3 burnoutPosition,
            final int guidanceTier,
            final DefenceOwnershipSnapshot ownership
        ) {
        releaseInterceptor(
            level,
            interceptorId,
            "reselect"
        );

        long burnoutTime =
            level.getGameTime()
                + AntiAirConstants.IGNITION_TICKS
                + AntiAirConstants.BOOST_TICKS;

        List<Option> options =
            new ArrayList<>();

        for (
            AntiAirTargetSelection selection
                : AntiAirTargetSelector.candidates(
                    level,
                    launchOrigin,
                    ownership
                )
        ) {
            AntiAirInterceptSolution solution =
                AntiAirInterceptSolver.solve(
                    burnoutPosition,
                    burnoutTime,
                    selection
                ).orElse(null);

            boolean exact =
                solution != null
                    && !solution.rangeLimited();

            if (solution == null) {
                solution =
                    AntiAirInterceptSolver.bestEffort(
                        burnoutPosition,
                        burnoutTime,
                        selection
                    ).orElse(null);
            }

            if (solution == null) {
                continue;
            }

            options.add(
                new Option(
                    selection,
                    solution,
                    exact,
                    claimCount(
                        level,
                        selection.targetLock()
                            .rootTrackId()
                    )
                )
            );
        }

        if (options.isEmpty()) {
            return Optional.empty();
        }

        int unclaimed =
            (int)options.stream()
                .filter(option ->
                    option.claims() == 0
                )
                .count();

        options.sort(
            Comparator
                .comparingInt(
                    (Option option) ->
                        option.claims()
                )
                .thenComparingInt(option ->
                    option.exact() ? 0 : 1
                )
                .thenComparingLong(option ->
                    option.selection()
                        .projection()
                        .firstEntryGameTime()
                )
                .thenComparingLong(option ->
                    option.solution()
                        .interceptGameTime()
                )
                .thenComparingDouble(option ->
                    option.selection()
                        .projection()
                        .closestHorizontalDistance()
                )
                .thenComparing(option ->
                    option.selection()
                        .targetLock()
                        .rootTrackId()
                        .toString()
                )
        );

        Option chosen =
            options.getFirst();

        UUID targetId =
            chosen.selection()
                .targetLock()
                .rootTrackId();

        claim(
            level,
            interceptorId,
            targetId
        );

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            WarMod.LOGGER.info(
                "Anti-air target assignment: "
                    + "interceptor={}, target={}, exact={}, "
                    + "claimsBefore={}, claimsAfter={}, "
                    + "candidates={}, unclaimed={}, "
                    + "interceptTicks={}, routeEnd={}",
                interceptorId,
                targetId,
                chosen.exact(),
                chosen.claims(),
                chosen.claims() + 1,
                options.size(),
                unclaimed,
                chosen.solution()
                    .interceptGameTime()
                    - burnoutTime,
                chosen.solution()
                    .nominalRoute()
                    .end()
            );
        }

        return Optional.of(
            new AntiAirTargetSelectionResult(
                chosen.selection(),
                chosen.solution(),
                AntiAirLaunchMode.TRACKED_INTERCEPT,
                chosen.claims(),
                options.size(),
                unclaimed
            )
        );
    }

    public static synchronized int claimCount(
        final ServerLevel level,
        final UUID targetId
    ) {
        return claims(level)
            .byTarget
            .getOrDefault(
                targetId,
                Set.of()
            )
            .size();
    }

    public static synchronized Optional<AntiAirTargetClaim>
        claimForInterceptor(
            final ServerLevel level,
            final UUID interceptorId
        ) {
        return Optional.ofNullable(
            claims(level)
                .byInterceptor
                .get(interceptorId)
        );
    }

    public static synchronized Collection<AntiAirTargetClaim>
        claimsForTarget(
            final ServerLevel level,
            final UUID targetId
        ) {
        Claims claims =
            claims(level);

        return claims.byTarget
            .getOrDefault(
                targetId,
                Set.of()
            )
            .stream()
            .map(claims.byInterceptor::get)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    public static synchronized boolean isClaimed(
        final ServerLevel level,
        final UUID targetId
    ) {
        return claimCount(
            level,
            targetId
        ) > 0;
    }

    public static synchronized void claim(
        final ServerLevel level,
        final UUID interceptorId,
        final UUID targetId
    ) {
        releaseInterceptor(
            level,
            interceptorId,
            "replaced"
        );

        Claims claims =
            claims(level);

        claims.byInterceptor.put(
            interceptorId,
            new AntiAirTargetClaim(
                interceptorId,
                targetId,
                level.getGameTime()
            )
        );

        claims.byTarget
            .computeIfAbsent(
                targetId,
                ignored ->
                    new HashSet<>()
            )
            .add(interceptorId);
    }

    public static synchronized void releaseInterceptor(
        final ServerLevel level,
        final UUID interceptorId,
        final String reason
    ) {
        Claims claims =
            BY_LEVEL.get(level);

        if (claims == null) {
            return;
        }

        AntiAirTargetClaim claim =
            claims.byInterceptor
                .remove(interceptorId);

        if (claim == null) {
            return;
        }

        Set<UUID> interceptors =
            claims.byTarget
                .get(claim.targetId());

        if (interceptors != null) {
            interceptors.remove(interceptorId);

            if (interceptors.isEmpty()) {
                claims.byTarget
                    .remove(claim.targetId());
            }
        }

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            WarMod.LOGGER.info(
                "Anti-air target claim released: "
                    + "interceptor={}, target={}, reason={}",
                interceptorId,
                claim.targetId(),
                reason
            );
        }
    }

    public static synchronized void releaseTarget(
        final ServerLevel level,
        final UUID targetId,
        final String reason
    ) {
        for (
            UUID interceptorId
                : List.copyOf(
                    claims(level)
                        .byTarget
                        .getOrDefault(
                            targetId,
                            Set.of()
                        )
                )
        ) {
            releaseInterceptor(
                level,
                interceptorId,
                reason
            );
        }
    }

    public static synchronized void prune(
        final ServerLevel level
    ) {
        Claims claims =
            BY_LEVEL.get(level);

        if (claims == null) {
            return;
        }

        long now =
            level.getGameTime();

        for (
            AntiAirTargetClaim claim
                : List.copyOf(
                    claims.byInterceptor
                        .values()
                )
        ) {
            if (
                now - claim.claimedGameTime()
                    > STALE_TIMEOUT_TICKS
                || !AntiAirFlightControllerManager
                    .isTargetTracking(
                        level,
                        claim.interceptorId(),
                        claim.targetId()
                    )
                || StrategicMissileTargetService
                    .state(
                        level,
                        claim.targetId(),
                        now
                    )
                    .isEmpty()
            ) {
                releaseInterceptor(
                    level,
                    claim.interceptorId(),
                    "stale"
                );
            }
        }
    }

    public static synchronized void clearLevel(
        final ServerLevel level
    ) {
        BY_LEVEL.remove(level);
    }

    public static synchronized void clearAll() {
        BY_LEVEL.clear();
    }

    private static Claims claims(
        final ServerLevel level
    ) {
        return BY_LEVEL.computeIfAbsent(
            level,
            ignored ->
                new Claims()
        );
    }

    private record Option(
        AntiAirTargetSelection selection,
        AntiAirInterceptSolution solution,
        boolean exact,
        int claims
    ) {
    }

    private static final class Claims {
        private final Map<
            UUID,
            AntiAirTargetClaim
        > byInterceptor =
            new LinkedHashMap<>();

        private final Map<
            UUID,
            Set<UUID>
        > byTarget =
            new HashMap<>();
    }
}
