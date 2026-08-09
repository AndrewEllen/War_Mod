package com.andye.warmod.artillery;

import com.andye.warmod.entity.ArtilleryWarheadEntity;
import com.andye.warmod.icbm.IcbmChunkTicketRegistry;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.warhead.WarheadImpactChunkLeaseManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class ArtilleryLaunchService {
    private ArtilleryLaunchService() { }
    public static Optional<List<UUID>> launch(final ServerLevel level, final UUID owner, final Vec3 origin, final Vec3 target, final ArtilleryPayload payload) {
        if (origin.distanceTo(target) > ArtilleryConstants.MAX_RANGE_BLOCKS || !level.getWorldBorder().isWithinBounds(target)) return Optional.empty();
        int count = payload.cluster() ? 4 : 1;
        SplittableRandom random = new SplittableRandom(level.getGameTime() ^ origin.hashCode() ^ target.hashCode());
        ArrayList<ArtilleryWarheadEntity> spawned = new ArrayList<>(count);
        ArrayList<UUID> ids = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Vec3 childTarget = target;
            if (count > 1) { double angle = Math.PI * 2.0 * index / count + random.nextDouble(-0.12, 0.12); double radius = 5.0 + random.nextDouble() * 5.0; childTarget = target.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius); }
            if (origin.distanceTo(childTarget) > ArtilleryConstants.MAX_RANGE_BLOCKS) childTarget = target;
            Vec3 velocity = ArtilleryTrajectory.solve(origin, childTarget).orElse(null);
            if (velocity == null) return Optional.empty();
            UUID id = UUID.randomUUID();
            ArtilleryWarheadEntity entity = new ArtilleryWarheadEntity(level, id, owner, origin.add(velocity.normalize().scale(1.1)), childTarget, velocity, payload.yield(), random.nextLong());
            spawned.add(entity); ids.add(id);
        }
        for (ArtilleryWarheadEntity entity : spawned) if (!level.addFreshEntity(entity)) { spawned.forEach(ArtilleryWarheadEntity::discard); return Optional.empty(); }
        for (UUID id : ids) WarheadImpactChunkLeaseManager.holdApproach(level, id, origin, target, 1_200 + IcbmConstants.IMPACT_CHUNK_TAIL_TICKS);
        return Optional.of(List.copyOf(ids));
    }
}
