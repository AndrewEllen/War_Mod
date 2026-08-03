package com.andye.warmod.block;

import com.andye.warmod.WarMod;
import com.andye.warmod.block.entity.PhalanxBlockEntity;
import com.andye.warmod.phalanx.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class PhalanxTurretBlockItem extends BlockItem {
    private enum Stage { PRECHECK, CONTROLLER_BLOCK, CONTROLLER_BLOCK_ENTITY, INITIALIZATION, REMAINING_PARTS, STRUCTURE_VALIDATION, MANAGER_REGISTRATION, ITEM_CONSUMPTION, COMPLETE }
    public PhalanxTurretBlockItem(Properties properties) { super(ModBlocks.PHALANX_TURRET, properties); }
    @Override public InteractionResult place(BlockPlaceContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.FAIL;
        Player player = context.getPlayer(); BlockPos controller = context.getClickedPos();
        Direction facing = player == null ? Direction.NORTH : player.getDirection().getOpposite();
        List<BlockPos> positions = Arrays.stream(PhalanxPart.values()).map(part -> controller.offset(part.offset())).toList();
        Stage stage = Stage.PRECHECK; List<BlockPos> placed = new ArrayList<>(); PhalanxBlockEntity entity = null;
        try {
            if (!PhalanxManager.canPlace(level)) return fail(player, "Phalanx limit reached");
            if (new HashSet<>(positions).size() != 8) return fail(player, "Phalanx placement has duplicate positions");
            for (BlockPos position : positions) {
                if (level.isOutsideBuildHeight(position) || !level.getWorldBorder().isWithinBounds(position)
                    || !level.getBlockState(position).canBeReplaced() || level.getBlockEntity(position) != null
                    || (player != null && !level.mayInteract(player, position)) || !level.getEntities(null, new AABB(position)).isEmpty())
                    return fail(player, "Cannot place complete 2x2x2 Phalanx here");
            }
            PhalanxStructureAssembly.begin(level, positions);
            stage = Stage.CONTROLLER_BLOCK;
            if (!level.setBlock(controller, state(PhalanxPart.BASE_00, facing), Block.UPDATE_ALL)) throw new IllegalStateException("controller setBlock returned false");
            placed.add(controller); stage = Stage.CONTROLLER_BLOCK_ENTITY;
            if (!(level.getBlockEntity(controller) instanceof PhalanxBlockEntity found)) throw new IllegalStateException("controller block entity missing");
            entity = found; stage = Stage.INITIALIZATION; entity.initialize(player, facing);
            stage = Stage.REMAINING_PARTS;
            for (PhalanxPart part : PhalanxPart.values()) if (part != PhalanxPart.BASE_00) { BlockPos position = controller.offset(part.offset()); if (!level.setBlock(position, state(part, facing), Block.UPDATE_ALL)) throw new IllegalStateException("part setBlock returned false: " + part); placed.add(position); }
            stage = Stage.STRUCTURE_VALIDATION; if (!PhalanxStructure.complete(level, controller)) throw new IllegalStateException("placed structure is incomplete");
            stage = Stage.MANAGER_REGISTRATION; PhalanxRegistrationResult registration = PhalanxManager.register(level, entity); if (!registration.accepted()) throw new IllegalStateException("manager registration rejected: " + registration.failure());
            stage = Stage.ITEM_CONSUMPTION; context.getItemInHand().consume(1, player); stage = Stage.COMPLETE;
            return InteractionResult.SUCCESS_SERVER;
        } catch (RuntimeException exception) {
            if (entity != null) { PhalanxManager.unregister(level, entity); PhalanxBulletManager.removeForTurret(level, entity.turretId()); }
            for (BlockPos position : placed) level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            WarMod.LOGGER.error("Phalanx placement failed at stage {}: controller={}, facing={}, placedParts={}", stage, controller, facing, placed.size(), exception);
            if (SharedConstants.IS_RUNNING_IN_IDE && player != null) player.sendSystemMessage(Component.literal("Phalanx placement failed at " + stage));
            return fail(player, "Phalanx placement failed atomically");
        } finally { PhalanxStructureAssembly.end(level, positions); }
    }
    private static net.minecraft.world.level.block.state.BlockState state(PhalanxPart part, Direction facing) { return ModBlocks.PHALANX_TURRET.defaultBlockState().setValue(PhalanxTurretBlock.PART, part).setValue(PhalanxTurretBlock.FACING, facing); }
    private static InteractionResult fail(Player player, String message) { if (player != null) player.sendSystemMessage(Component.literal(message)); return InteractionResult.FAIL; }
}