package com.andye.warmod.silo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

public record MissileSiloRecord(UUID siloId, BlockPos centre, UUID ownerId, String ownerName) {
    public static final Codec<MissileSiloRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("silo_id").forGetter(MissileSiloRecord::siloId),
        BlockPos.CODEC.fieldOf("centre").forGetter(MissileSiloRecord::centre),
        UUIDUtil.CODEC.fieldOf("owner_id").forGetter(MissileSiloRecord::ownerId),
        Codec.STRING.fieldOf("owner_name").forGetter(MissileSiloRecord::ownerName)
    ).apply(instance, MissileSiloRecord::new));
}
