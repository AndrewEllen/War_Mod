package com.andye.warmod.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record TargetCoordinates(ResourceKey<Level> dimension, Vec3 position) {
    public static final Codec<TargetCoordinates> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(TargetCoordinates::dimension),
        Vec3.CODEC.fieldOf("position").forGetter(TargetCoordinates::position)
    ).apply(instance, TargetCoordinates::new));

    public boolean isValid() {
        return this.dimension != null && this.position != null && this.position.isFinite()
            && Level.isInSpawnableBounds(BlockPos.containing(this.position));
    }
}
