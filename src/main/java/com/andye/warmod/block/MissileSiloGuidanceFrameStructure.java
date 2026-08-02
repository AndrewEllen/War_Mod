package com.andye.warmod.block;

import com.andye.warmod.WarMod;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.item.ModItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MissileSiloGuidanceFrameStructure {
    private static final Set<String> TEARING_DOWN = new HashSet<>();

    private MissileSiloGuidanceFrameStructure() { }

    public static double maximumGuidanceError(final int tier) {
        return switch (Math.max(0, Math.min(3, tier))) {
            case 1 -> 50.0;
            case 2 -> 25.0;
            case 3 -> 0.0;
            default -> 100.0;
        };
    }

    public static int installedTier(final ServerLevel level, final BlockPos centre, final Direction facing) {
        int left = installedSideTier(level, centre, facing, GuidanceSupportSide.LEFT);
        int right = installedSideTier(level, centre, facing, GuidanceSupportSide.RIGHT);
        return left == 0 || right == 0 ? 0 : Math.min(left, right);
    }

    public static int installedSideTier(final ServerLevel level, final BlockPos centre,
        final Direction facing, final GuidanceSupportSide side) {
        int tier = 0;
        for (GuidanceSupportPart part : GuidanceSupportPart.values()) {
            BlockState state = level.getBlockState(part.position(centre, facing, side));
            if (!state.is(ModBlocks.MISSILE_SILO_GUIDANCE_SUPPORT)
                || state.getValue(MissileSiloGuidanceSupportBlock.SIDE) != side
                || state.getValue(MissileSiloGuidanceSupportBlock.PART) != part
                || state.getValue(MissileSiloGuidanceSupportBlock.FACING) != facing) return 0;
            int partTier = state.getValue(MissileSiloGuidanceSupportBlock.TIER);
            if (tier == 0) tier = partTier;
            else if (partTier != tier) return 0;
        }
        return tier;
    }

    public static Set<BlockPos> positions(final BlockPos centre, final Direction facing) {
        Set<BlockPos> result = new java.util.LinkedHashSet<>();
        for (GuidanceSupportSide side : GuidanceSupportSide.values()) {
            for (GuidanceSupportPart part : GuidanceSupportPart.values()) {
                result.add(part.position(centre, facing, side));
            }
        }
        return Set.copyOf(result);
    }

    public static @Nullable SupportTarget resolveTarget(final ServerLevel level, final BlockPos clickedPos,
        final BlockState clickedState, final Vec3 hitLocation) {
        if (clickedState.is(ModBlocks.MISSILE_SILO)) {
            MissileSiloBlockEntity silo = MissileSiloBlock.resolve(level, clickedPos, clickedState);
            if (silo == null) return null;
            Direction facing = silo.facing();
            Direction right = facing.getClockWise();
            Vec3 centre = Vec3.atCenterOf(silo.getBlockPos());
            Vec3 offset = hitLocation.subtract(centre);
            double lateral = offset.x * right.getStepX() + offset.z * right.getStepZ();
            if (Math.abs(lateral) < 0.05) {
                BlockPos delta = clickedPos.subtract(silo.getBlockPos());
                lateral = delta.getX() * right.getStepX() + delta.getZ() * right.getStepZ();
            }
            GuidanceSupportSide side = lateral >= 0.0 ? GuidanceSupportSide.RIGHT : GuidanceSupportSide.LEFT;
            return new SupportTarget(silo.getBlockPos(), facing, side);
        }
        if (clickedState.is(ModBlocks.MISSILE_SILO_GUIDANCE_SUPPORT)) {
            Direction facing = clickedState.getValue(MissileSiloGuidanceSupportBlock.FACING);
            GuidanceSupportSide side = clickedState.getValue(MissileSiloGuidanceSupportBlock.SIDE);
            GuidanceSupportPart part = clickedState.getValue(MissileSiloGuidanceSupportBlock.PART);
            return new SupportTarget(part.resolveCentre(clickedPos, facing, side), facing, side);
        }
        return null;
    }

    public static boolean installOrUpgrade(final ServerLevel level, final BlockPos centre,
        final Direction facing, final GuidanceSupportSide side, final int newTier, final @Nullable Player player) {
        if (!(level.getBlockEntity(centre) instanceof MissileSiloBlockEntity silo)) return false;
        cleanupLegacy(level, centre);
        int oldTier = installedSideTier(level, centre, facing, side);
        if (oldTier >= newTier) return fail(player, "That guidance side is already Tier " + oldTier);

        List<BlockPos> targetPositions = new ArrayList<>();
        for (GuidanceSupportPart part : GuidanceSupportPart.values()) {
            BlockPos pos = part.position(centre, facing, side);
            BlockState state = level.getBlockState(pos);
            boolean ownSupport = state.is(ModBlocks.MISSILE_SILO_GUIDANCE_SUPPORT)
                && state.getValue(MissileSiloGuidanceSupportBlock.SIDE) == side;
            if (!ownSupport && (!state.canBeReplaced() || level.getBlockEntity(pos) != null
                || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || player != null && !level.mayInteract(player, pos))) {
                return fail(player, "Guidance support obstructed at " + pos.toShortString());
            }
            targetPositions.add(pos);
        }

        List<BlockState> previous = targetPositions.stream().map(level::getBlockState).toList();
        try {
            for (int index = 0; index < GuidanceSupportPart.values().length; index++) {
                GuidanceSupportPart part = GuidanceSupportPart.values()[index];
                BlockState state = ModBlocks.MISSILE_SILO_GUIDANCE_SUPPORT.defaultBlockState()
                    .setValue(MissileSiloGuidanceSupportBlock.SIDE, side)
                    .setValue(MissileSiloGuidanceSupportBlock.PART, part)
                    .setValue(MissileSiloGuidanceSupportBlock.TIER, newTier)
                    .setValue(MissileSiloGuidanceSupportBlock.FACING, facing);
                if (!level.setBlock(targetPositions.get(index), state, Block.UPDATE_ALL)) {
                    throw new IllegalStateException("Support placement failed");
                }
            }
        } catch (RuntimeException exception) {
            for (int index = 0; index < targetPositions.size(); index++) {
                level.setBlock(targetPositions.get(index), previous.get(index), Block.UPDATE_ALL);
            }
            return fail(player, "Guidance support placement failed atomically");
        }

        if (oldTier > 0) {
            ItemStack returned = new ItemStack(ModItems.guidanceSupport(oldTier));
            if (player == null || !player.getInventory().add(returned)) Block.popResource(level, centre, returned);
        }
        logChange(level, silo, facing);
        silo.sync();
        return true;
    }

    public static void removeFromPart(final ServerLevel level, final BlockPos pos,
        final BlockState state, final boolean drops) {
        if (!state.is(ModBlocks.MISSILE_SILO_GUIDANCE_SUPPORT)) return;
        Direction facing = state.getValue(MissileSiloGuidanceSupportBlock.FACING);
        GuidanceSupportSide side = state.getValue(MissileSiloGuidanceSupportBlock.SIDE);
        GuidanceSupportPart part = state.getValue(MissileSiloGuidanceSupportBlock.PART);
        BlockPos centre = part.resolveCentre(pos, facing, side);
        removeSide(level, centre, facing, side, drops);
    }

    public static void removeSide(final ServerLevel level, final BlockPos centre, final Direction facing,
        final GuidanceSupportSide side, final boolean drops) {
        String key = level.dimension().identifier() + ":" + centre.asLong() + ":" + side;
        synchronized (TEARING_DOWN) {
            if (!TEARING_DOWN.add(key)) return;
        }
        try {
            int tier = installedSideTier(level, centre, facing, side);
            boolean removed = false;
            for (GuidanceSupportPart part : GuidanceSupportPart.values()) {
                BlockPos pos = part.position(centre, facing, side);
                if (level.getBlockState(pos).is(ModBlocks.MISSILE_SILO_GUIDANCE_SUPPORT)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    removed = true;
                }
            }
            if (drops && removed && tier > 0) {
                Block.popResource(level, centre, new ItemStack(ModItems.guidanceSupport(tier)));
            }
            if (level.getBlockEntity(centre) instanceof MissileSiloBlockEntity silo) {
                logChange(level, silo, facing);
                silo.sync();
            }
        } finally {
            synchronized (TEARING_DOWN) {
                TEARING_DOWN.remove(key);
            }
        }
    }

    public static void teardown(final ServerLevel level, final BlockPos centre,
        final Direction facing, final boolean drops) {
        removeSide(level, centre, facing, GuidanceSupportSide.LEFT, drops);
        removeSide(level, centre, facing, GuidanceSupportSide.RIGHT, drops);
        cleanupLegacy(level, centre);
    }

    public static void cleanupLegacy(final ServerLevel level, final BlockPos centre) {
        for (int y = 3; y <= 6; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = centre.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (BuiltInLegacyGuidance.isLegacy(state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    private static void logChange(final ServerLevel level, final MissileSiloBlockEntity silo,
        final Direction facing) {
        if (!SharedConstants.IS_RUNNING_IN_IDE) return;
        int left = installedSideTier(level, silo.getBlockPos(), facing, GuidanceSupportSide.LEFT);
        int right = installedSideTier(level, silo.getBlockPos(), facing, GuidanceSupportSide.RIGHT);
        WarMod.LOGGER.info("Silo {} guidance supports changed: left={}, right={}, effective={}",
            silo.siloId(), left, right, left == 0 || right == 0 ? 0 : Math.min(left, right));
    }

    private static boolean fail(final @Nullable Player player, final String message) {
        if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        return false;
    }

    public record SupportTarget(BlockPos centre, Direction facing, GuidanceSupportSide side) { }

    private static final class BuiltInLegacyGuidance {
        private static boolean isLegacy(final BlockState state) {
            net.minecraft.resources.Identifier id =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return id.getNamespace().equals("war_mod") && id.getPath().equals("missile_silo_guidance_frame");
        }
    }
}