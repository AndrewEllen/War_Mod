package com.andye.warmod.defence;

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
        if (forcedHostile || isUnowned(missileOwnerPlayerId) || ownerPlayerId == null) {
            return true;
        }
        if (ownerPlayerId.equals(missileOwnerPlayerId)) {
            return false;
        }
        return allies.stream().noneMatch(ally -> ally.playerId().equals(missileOwnerPlayerId));
    }

    public static boolean isUnowned(final @Nullable UUID playerId) {
        return playerId == null || (playerId.getMostSignificantBits() == 0L
            && playerId.getLeastSignificantBits() == 0L);
    }
}
