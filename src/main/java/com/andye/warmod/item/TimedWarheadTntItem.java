package com.andye.warmod.item;

import com.andye.warmod.artillery.ArtilleryPayload;
import com.andye.warmod.entity.TimedWarheadTntEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Hand-primeable and throwable timed warhead charge. */
public final class TimedWarheadTntItem extends Item {
    private final ArtilleryPayload payload;
    public TimedWarheadTntItem(final Properties properties, final ArtilleryPayload payload) { super(properties); this.payload = payload; }
    public ArtilleryPayload payload() { return payload; }
    @Override public Component getName(final ItemStack stack) { return Component.literal(payload.displayName("Timed TNT")); }
    @Override public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        Vec3 direction = player.getLookAngle().normalize();
        TimedWarheadTntEntity charge = new TimedWarheadTntEntity(server, player.getUUID(), player.getEyePosition().add(direction.scale(0.35)), direction.scale(0.72).add(0.0, 0.18, 0.0), payload);
        if (!server.addFreshEntity(charge)) return InteractionResult.FAIL;
        charge.beginTerrainPreparation(server);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }
}
