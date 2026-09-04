package com.andye.warmod.defence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record DefenceAlly(UUID playerId, String playerName) {
    public static final Codec<DefenceAlly> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("player_id").forGetter(DefenceAlly::playerId),
        Codec.STRING.fieldOf("player_name").forGetter(DefenceAlly::playerName)
    ).apply(instance, DefenceAlly::new));
}
