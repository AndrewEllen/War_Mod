package com.andye.warmod.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record LinkedSilo(ResourceKey<Level> dimension, BlockPos centre, UUID siloId) {
    public static final Codec<LinkedSilo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(LinkedSilo::dimension),
        BlockPos.CODEC.fieldOf("centre").forGetter(LinkedSilo::centre),
        UUIDUtil.CODEC.fieldOf("silo_id").forGetter(LinkedSilo::siloId)
    ).apply(instance, LinkedSilo::new));

    public boolean isValid() {
        return this.dimension != null && this.centre != null && this.siloId != null
            && Level.isInSpawnableBounds(this.centre);
    }
}
