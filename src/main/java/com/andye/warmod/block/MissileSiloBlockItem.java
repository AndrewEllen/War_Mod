package com.andye.warmod.block;

import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.silo.MissileSiloManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class MissileSiloBlockItem extends BlockItem {
    public MissileSiloBlockItem(final Properties properties) {
        super(ModBlocks.MISSILE_SILO, properties);
    }

    @Override
    public InteractionResult place(final BlockPlaceContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.FAIL;
        Player player = context.getPlayer();
        BlockPos centre = context.getClickedPos();
        Direction facing = player == null ? Direction.NORTH : player.getDirection().getOpposite();
        if (!MissileSiloManager.canPlace(server)) return fail(player, "Missile silo limit reached for this dimension");
        List<BlockPos> positions = new ArrayList<>();
        for (MissileSiloPart part : MissileSiloPart.values()) {
            BlockPos pos = MissileSiloStructure.position(centre, part, facing);
            positions.add(pos);
            if (server.isOutsideBuildHeight(pos) || !server.getWorldBorder().isWithinBounds(pos)
                || server.getBlockEntity(pos) != null || !server.getBlockState(pos).canBeReplaced()
                || (player != null && !server.mayInteract(player, pos))
                || !server.getEntities(null, new AABB(pos)).isEmpty()) return fail(player, "Cannot place complete 3x3 missile silo here");
        }
        List<BlockPos> placed = new ArrayList<>();
        try {
            for (MissileSiloPart part : MissileSiloPart.values()) {
                BlockPos pos = MissileSiloStructure.position(centre, part, facing);
                BlockState state = ModBlocks.MISSILE_SILO.defaultBlockState().setValue(MissileSiloBlock.PART, part)
                    .setValue(MissileSiloBlock.FACING, facing);
                if (!server.setBlock(pos, state, Block.UPDATE_ALL)) throw new IllegalStateException("placement rejected");
                placed.add(pos);
            }
            if (!(server.getBlockEntity(centre) instanceof MissileSiloBlockEntity silo)) throw new IllegalStateException("missing centre block entity");
            silo.initialize(server, facing, player);
            if (!MissileSiloManager.register(server, silo)) throw new IllegalStateException("silo registration rejected");
            context.getItemInHand().consume(1, player);
            return InteractionResult.SUCCESS_SERVER;
        } catch (RuntimeException exception) {
            for (BlockPos pos : placed) server.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
            return fail(player, "Missile silo placement failed atomically");
        }
    }

    private static InteractionResult fail(final Player player, final String message) {
        if (player != null) player.sendSystemMessage(Component.literal(message));
        return InteractionResult.FAIL;
    }
}
