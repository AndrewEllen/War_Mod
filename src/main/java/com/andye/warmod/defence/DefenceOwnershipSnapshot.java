package com.andye.warmod.defence;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record DefenceOwnershipSnapshot(
    @Nullable UUID ownerPlayerId,
    String ownerDisplayName,
    List<DefenceAlly> allies
) {
    public DefenceOwnershipSnapshot {
        ownerDisplayName = ownerDisplayName == null || ownerDisplayName.isBlank()
            ? "SERVER"
            : ownerDisplayName;
        allies = List.copyOf(allies);
    }

    public static DefenceOwnershipSnapshot unclaimed() {
        return new DefenceOwnershipSnapshot(null, "SERVER", List.of());
    }

    public boolean isOwner(final UUID playerId) {
        return ownerPlayerId != null && ownerPlayerId.equals(playerId);
    }

    public boolean isHostile(
        final @Nullable UUID missileOwnerPlayerId,
        final boolean forcedHostile
    ) {
        return isHostile(MissileAffiliation.ofOwner(missileOwnerPlayerId), forcedHostile);
    }

    public boolean isHostile(
        final MissileAffiliation missileAffiliation,
        final boolean forcedHostile
    ) {
        if (forcedHostile || missileAffiliation == null || missileAffiliation.unclaimed()
                || ownerPlayerId == null) {
            return true;
        }
        HashSet<UUID> recognizedPlayers = new HashSet<>();
        recognizedPlayers.add(ownerPlayerId);
        allies.forEach(ally -> recognizedPlayers.add(ally.playerId()));
        return !recognizedPlayers.containsAll(missileAffiliation.playerIds());
    }

    public static boolean isUnowned(final @Nullable UUID playerId) {
        return playerId == null || (playerId.getMostSignificantBits() == 0L
            && playerId.getLeastSignificantBits() == 0L);
    }
}
