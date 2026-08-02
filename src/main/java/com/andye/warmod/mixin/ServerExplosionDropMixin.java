package com.andye.warmod.mixin;

import com.andye.warmod.testtool.WarheadExplosionDropContext;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionDropMixin {
	@Redirect(
		method = "interactWithBlocks",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;onExplosionHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;Ljava/util/function/BiConsumer;)V"
		)
	)
	private void warMod$suppressTestWarheadDrops(final BlockState state, final ServerLevel level,
		final BlockPos position, final Explosion explosion, final BiConsumer<ItemStack, BlockPos> dropConsumer) {
		BiConsumer<ItemStack, BlockPos> scopedConsumer = WarheadExplosionDropContext.isActive()
			? (stack, dropPosition) -> { }
			: dropConsumer;
		state.onExplosionHit(level, position, explosion, scopedConsumer);
	}
}