package com.andye.warmod.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record LinkedLaunchController(
    ResourceKey<Level> dimension,
    BlockPos centre,
    UUID controllerId
) {
    public static final Codec<LinkedLaunchController> CODEC =
        RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension")
                .forGetter(LinkedLaunchController::dimension),
            BlockPos.CODEC.fieldOf("centre")
                .forGetter(LinkedLaunchController::centre),
            UUIDUtil.CODEC.fieldOf("controller_id")
                .forGetter(LinkedLaunchController::controllerId)
        ).apply(instance, LinkedLaunchController::new));

    public boolean isValid() {
        return dimension != null
            && centre != null
            && controllerId != null
            && Level.isInSpawnableBounds(centre);
    }
}
