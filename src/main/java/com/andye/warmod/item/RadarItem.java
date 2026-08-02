package com.andye.warmod.item;

import com.andye.warmod.radar.RadarSubscriptionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class RadarItem extends Item {
	public RadarItem(final Properties properties){super(properties);}
	@Override public InteractionResult use(final Level level,final Player player,final InteractionHand hand){
		if(!level.isClientSide()&&player instanceof ServerPlayer serverPlayer){RadarSubscriptionManager.open(serverPlayer);return InteractionResult.SUCCESS_SERVER;}
		return InteractionResult.SUCCESS;
	}
}
