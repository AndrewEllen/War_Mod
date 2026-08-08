package com.andye.warmod.item;

import com.andye.warmod.artillery.ArtilleryConstants;
import com.andye.warmod.entity.PrimedYieldExplosiveEntity;
import com.andye.warmod.warhead.WarheadYield;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Right-click primes and throws the selected yield instead of placing vanilla TNT. */
public final class TimedYieldExplosiveItem extends Item {
    private final WarheadYield yield;
    private final boolean cluster;

    public TimedYieldExplosiveItem(final Properties properties, final WarheadYield yield,
        final boolean cluster) {
        super(properties);
        this.yield = yield;
        this.cluster = cluster;
    }

    public WarheadYield yield() { return this.yield; }
    public boolean cluster() { return this.cluster; }
    public int fuseTicks() {
        return this.yield.nuclear()
            ? ArtilleryConstants.NUCLEAR_FUSE_TICKS
            : ArtilleryConstants.CONVENTIONAL_FUSE_TICKS;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer owner)) {
            return InteractionResult.PASS;
        }

        Vec3 start = owner.getEyePosition().add(owner.getLookAngle().scale(0.45));
        Vec3 velocity = owner.getLookAngle().scale(1.15)
            .add(owner.getDeltaMovement().scale(0.35));
        PrimedYieldExplosiveEntity entity = new PrimedYieldExplosiveEntity(server,
            owner.getUUID(), start, velocity, this.yield, this.cluster, fuseTicks());
        if (!server.addFreshEntity(entity)) return InteractionResult.PASS;
        if (!owner.hasInfiniteMaterials()) stack.shrink(1);
        owner.swing(hand);
        return InteractionResult.SUCCESS_SERVER;
    }
}
