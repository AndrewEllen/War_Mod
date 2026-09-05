package com.andye.warmod.artillery;

import com.andye.warmod.entity.ArtilleryWarheadEntity;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class ArtilleryLaunchService {
    private ArtilleryLaunchService() { }
    public static Optional<List<UUID>> launch(final ServerLevel level, final UUID owner, final Vec3 origin, final Vec3 target, final ArtilleryPayload payload) {
        if (origin.distanceTo(target) > ArtilleryConstants.MAX_RANGE_BLOCKS || !level.getWorldBorder().isWithinBounds(target)) return Optional.empty();
        SplittableRandom random = new SplittableRandom(level.getGameTime() ^ origin.hashCode() ^ target.hashCode());
        Vec3 velocity = ArtilleryTrajectory.solve(origin, target).orElse(null);
        if (velocity == null) return Optional.empty();
        UUID id = UUID.randomUUID();
        ArtilleryWarheadEntity entity = new ArtilleryWarheadEntity(level, id, owner, origin,
            target, velocity, payload.yield(), random.nextLong(), payload.cluster());
        if (!level.addFreshEntity(entity)) return Optional.empty();
        entity.beginTerrainPreparation(level);
        // Artillery streams a short rolling corridor from its entity tick. Acquiring the ICBM's
        // complete 500-block approach corridor here generated more than a hundred chunks on the
        // firing tick and was the source of the intermittent launch freeze.
        return Optional.of(List.of(id));
    }
}
