package com.andye.warmod.silo;

import com.andye.warmod.WarMod;
import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.block.MissileSiloBlock;
import com.andye.warmod.block.entity.LaunchControllerBlockEntity;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.item.component.LinkedSilo;
import com.andye.warmod.item.component.TargetCoordinates;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

public final class LaunchControllerLaunchService {
    private LaunchControllerLaunchService() {
    }

    public static LaunchControllerBatchResult requestLaunches(
        final ServerLevel level,
        final LaunchControllerBlockEntity controller,
        final MissileSiloLaunchTrigger trigger,
        final @Nullable UUID playerId,
        final @Nullable String playerName,
        final @Nullable TargetCoordinates sharedTarget
    ) {
        List<LaunchControllerSiloResult> results = dispatchAll(
            controller.linkedSilos(),
            link -> requestOne(
                level,
                link,
                trigger,
                playerId,
                playerName,
                sharedTarget
            )
        );
        int accepted = (int)results.stream()
            .filter(LaunchControllerSiloResult::accepted)
            .count();
        LaunchControllerBatchResult batch = new LaunchControllerBatchResult(
            accepted,
            results
        );
        controller.recordBatch(batch);
        return batch;
    }

    static List<LaunchControllerSiloResult> dispatchAll(
        final List<LinkedSilo> links,
        final Function<LinkedSilo, LaunchControllerSiloResult> operation
    ) {
        ArrayList<LaunchControllerSiloResult> results =
            new ArrayList<>(links.size());
        for (LinkedSilo link : links) {
            try {
                LaunchControllerSiloResult result = operation.apply(link);
                results.add(result == null
                    ? LaunchControllerSiloResult.failed(
                        link,
                        "Launch service returned no result"
                    )
                    : result);
            } catch (RuntimeException failure) {
                WarMod.LOGGER.warn("Launch Controller failed to dispatch silo {} at {}",
                    link.siloId(), link.centre(), failure);
                results.add(LaunchControllerSiloResult.failed(
                    link,
                    "Launch request failed unexpectedly"
                ));
            }
        }
        return List.copyOf(results);
    }

    private static LaunchControllerSiloResult requestOne(
        final ServerLevel level,
        final LinkedSilo link,
        final MissileSiloLaunchTrigger trigger,
        final @Nullable UUID playerId,
        final @Nullable String playerName,
        final @Nullable TargetCoordinates sharedTarget
    ) {
        if (!link.isValid() || !link.dimension().equals(level.dimension())) {
            return LaunchControllerSiloResult.failed(
                link,
                "Linked silo is in another dimension or invalid"
            );
        }

        var state = level.getBlockState(link.centre());
        MissileSiloBlockEntity silo = state.is(ModBlocks.MISSILE_SILO)
            ? MissileSiloBlock.resolve(level, link.centre(), state)
            : null;
        if (silo == null || !silo.siloId().equals(link.siloId())) {
            return LaunchControllerSiloResult.failed(
                link,
                "Linked silo no longer exists"
            );
        }

        MissileSiloLaunchResult launch = MissileSiloLaunchService.requestLaunch(
            level,
            silo,
            trigger,
            playerId,
            playerName,
            sharedTarget
        );
        return new LaunchControllerSiloResult(
            link,
            launch.accepted(),
            launch.message()
        );
    }
}
