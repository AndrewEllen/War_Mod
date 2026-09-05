package com.andye.warmod.defence;

import com.mojang.serialization.Codec;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/** Immutable player affiliation captured when a missile leaves its launcher. */
public record MissileAffiliation(Set<UUID> playerIds) {
    public static final Codec<MissileAffiliation> CODEC = UUIDUtil.CODEC.listOf()
        .xmap(values -> new MissileAffiliation(new LinkedHashSet<>(values)),
            affiliation -> affiliation.playerIds().stream().toList());

    public MissileAffiliation {
        LinkedHashSet<UUID> sanitized = new LinkedHashSet<>();
        for (UUID id : playerIds) {
            if (!DefenceOwnershipSnapshot.isUnowned(id)) sanitized.add(id);
        }
        playerIds = Set.copyOf(sanitized);
    }

    public static MissileAffiliation unowned() {
        return new MissileAffiliation(Set.of());
    }

    public static MissileAffiliation ofOwner(final @Nullable UUID ownerPlayerId) {
        return DefenceOwnershipSnapshot.isUnowned(ownerPlayerId)
            ? unowned() : new MissileAffiliation(Set.of(ownerPlayerId));
    }

    public static MissileAffiliation ofDefence(final DefenceOwnershipSnapshot ownership) {
        if (ownership.ownerPlayerId() == null) return unowned();
        LinkedHashSet<UUID> players = new LinkedHashSet<>();
        players.add(ownership.ownerPlayerId());
        ownership.allies().forEach(ally -> players.add(ally.playerId()));
        return new MissileAffiliation(players);
    }

    public boolean unclaimed() {
        return playerIds.isEmpty();
    }
}
