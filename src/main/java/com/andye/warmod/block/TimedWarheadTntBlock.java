package com.andye.warmod.block;

import com.andye.warmod.artillery.ArtilleryPayload;
import com.andye.warmod.entity.TimedWarheadTntEntity;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Placeable, textured yield-specific TNT that primes into War Mod's impact pipeline. */
public final class TimedWarheadTntBlock extends TntBlock {
    private final ArtilleryPayload payload;

    public TimedWarheadTntBlock(final BlockBehaviour.Properties properties,
        final ArtilleryPayload payload) {
        super(properties);
        this.payload = payload;
    }

    public ArtilleryPayload payload() {
        return payload;
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
        final BlockState oldState, final boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && level.hasNeighborSignal(pos) && prime(level, pos, null)) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
        final Block block, final @Nullable Orientation orientation, final boolean movedByPiston) {
        if (level.hasNeighborSignal(pos) && prime(level, pos, null)) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos,
        final BlockState state, final Player player) {
        if (!level.isClientSide() && !player.getAbilities().instabuild
            && state.getValue(UNSTABLE)) {
            prime(level, pos, player);
        }
        spawnDestroyParticles(level, player, pos, state);
        if (state.is(BlockTags.GUARDED_BY_PIGLINS) && level instanceof ServerLevel server) {
            PiglinAi.angerNearbyPiglins(server, player, false);
        }
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
        return state;
    }

    @Override
    public void wasExploded(final ServerLevel level, final BlockPos pos,
        final Explosion explosion) {
        if (level.getGameRules().get(GameRules.TNT_EXPLODES)) {
            Entity source = explosion.getIndirectSourceEntity();
            prime(level, pos, source instanceof LivingEntity living ? living : null);
        }
    }

    @Override
    protected InteractionResult useItemOn(final ItemStack stack, final BlockState state,
        final Level level, final BlockPos pos, final Player player,
        final InteractionHand hand, final BlockHitResult hit) {
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (prime(level, pos, player)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
            Item item = stack.getItem();
            if (stack.is(Items.FLINT_AND_STEEL)) {
                stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
            } else {
                stack.consume(1, player);
            }
            player.awardStat(Stats.ITEM_USED.get(item));
        } else if (level instanceof ServerLevel server
            && !server.getGameRules().get(GameRules.TNT_EXPLODES)) {
            player.sendOverlayMessage(Component.translatable("block.minecraft.tnt.disabled"));
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onProjectileHit(final Level level, final BlockState state,
        final BlockHitResult hit, final Projectile projectile) {
        if (level instanceof ServerLevel server) {
            BlockPos pos = hit.getBlockPos();
            Entity owner = projectile.getOwner();
            if (projectile.isOnFire() && projectile.mayInteract(server, pos)
                && prime(level, pos, owner instanceof LivingEntity living ? living : null)) {
                level.removeBlock(pos, false);
            }
        }
    }

    @Override
    public boolean dropFromExplosion(final Explosion explosion) {
        return false;
    }

    private boolean prime(final Level level, final BlockPos pos,
        final @Nullable LivingEntity source) {
        if (!(level instanceof ServerLevel server)
            || !server.getGameRules().get(GameRules.TNT_EXPLODES)) {
            return false;
        }
        TimedWarheadTntEntity charge = new TimedWarheadTntEntity(server,
            source == null ? null : source.getUUID(), Vec3.atCenterOf(pos), Vec3.ZERO, payload);
        if (!server.addFreshEntity(charge)) return false;
        charge.beginTerrainPreparation(server);
        level.playSound(null, charge.getX(), charge.getY(), charge.getZ(),
            SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(source, GameEvent.PRIME_FUSE, pos);
        return true;
    }
}
