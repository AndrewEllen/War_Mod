package com.andye.warmod.mixin;

import com.andye.warmod.warhead.WarheadLightEngineAccess;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ThreadedLevelLightEngine.class)
public interface ThreadedLevelLightEngineWarheadMixin extends WarheadLightEngineAccess {
    @Override
    @Invoker("updateChunkStatus")
    void war_mod$resetChunkLighting(ChunkPos position);
}
