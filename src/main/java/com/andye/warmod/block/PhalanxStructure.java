package com.andye.warmod.block;

import com.andye.warmod.block.entity.PhalanxBlockEntity;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.phalanx.PhalanxBulletManager;
import com.andye.warmod.phalanx.PhalanxManager;
import com.andye.warmod.phalanx.PhalanxStructureAssembly;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PhalanxStructure {
    private PhalanxStructure() { }
    public static boolean complete(final ServerLevel level, final BlockPos controller) {
        BlockState base = level.getBlockState(controller);
        if (!base.is(ModBlocks.PHALANX_TURRET) || base.getValue(PhalanxTurretBlock.PART) != PhalanxPart.BASE_00) return false;
        Direction facing = base.getValue(PhalanxTurretBlock.FACING);
        BlockState head = level.getBlockState(controller.above());
        return head.is(ModBlocks.PHALANX_TURRET)
            && head.getValue(PhalanxTurretBlock.PART) == PhalanxPart.TOP_00
            && head.getValue(PhalanxTurretBlock.FACING) == facing
            && level.getBlockEntity(controller) instanceof PhalanxBlockEntity;
    }
    public static BlockPos controller(final BlockPos position, final BlockState state) {
        if (!state.is(ModBlocks.PHALANX_TURRET)) return position;
        return state.getValue(PhalanxTurretBlock.PART).controller(position);
    }
    private static boolean hasLegacyParts(final ServerLevel level, final BlockPos controller) {
        for (PhalanxPart part : PhalanxPart.values()) {
            if (part == PhalanxPart.BASE_00 || part == PhalanxPart.TOP_00) continue;
            if (level.getBlockState(controller.offset(part.offset())).is(ModBlocks.PHALANX_TURRET)) return true;
        }
        return false;
    }
    public static void teardown(final ServerLevel level, final BlockPos anyPosition, final BlockState state, final boolean drops) {
        if (!state.is(ModBlocks.PHALANX_TURRET) || PhalanxStructureAssembly.contains(level, anyPosition)) return;
        BlockPos controller = controller(anyPosition, state);
        PhalanxBlockEntity blockEntity = level.getBlockEntity(controller) instanceof PhalanxBlockEntity found ? found : null;
        if (blockEntity != null && blockEntity.teardown()) return;
        if (blockEntity != null) blockEntity.setTeardown(true);
        try {
            if (blockEntity != null) { PhalanxManager.unregister(level, blockEntity); PhalanxBulletManager.removeForTurret(level, blockEntity.turretId()); }
            if (drops) {
                Block.popResource(level, controller, new ItemStack(ModItems.PHALANX_TURRET));
                if (blockEntity != null) for (int slot = 0; slot < 2; slot++) { ItemStack ammunition = blockEntity.removeItemNoUpdate(slot); if (!ammunition.isEmpty()) Block.popResource(level, controller, ammunition); }
            }
            Iterable<PhalanxPart> removalParts = hasLegacyParts(level, controller) ? java.util.List.of(PhalanxPart.values()) : PhalanxPart.compactStructure();
            for (PhalanxPart part : removalParts) { BlockPos position = controller.offset(part.offset()); if (level.getBlockState(position).is(ModBlocks.PHALANX_TURRET)) level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); }
        } finally { if (blockEntity != null) blockEntity.setTeardown(false); }
    }
}