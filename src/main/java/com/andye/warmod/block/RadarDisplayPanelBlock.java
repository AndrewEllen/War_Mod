package com.andye.warmod.block;

import com.andye.warmod.block.entity.ModBlockEntities;
import com.andye.warmod.block.entity.RadarDisplayPanelBlockEntity;
import com.andye.warmod.item.component.LinkedRadarStation;
import com.andye.warmod.radar.display.RadarDisplayConstants;
import com.andye.warmod.radar.display.RadarDisplayManager;
import com.andye.warmod.radar.display.RadarDisplayStructure;
import com.andye.warmod.radar.station.network.RadarStationNetworking;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class RadarDisplayPanelBlock extends BaseEntityBlock {
    public static final MapCodec<RadarDisplayPanelBlock> CODEC =
        simpleCodec(RadarDisplayPanelBlock::new);
    public static final Property<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public RadarDisplayPanelBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends RadarDisplayPanelBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
        final StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
        final BlockPos position,
        final BlockState state
    ) {
        return new RadarDisplayPanelBlockEntity(position, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        final Level level,
        final BlockState state,
        final BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return createTickerHelper(
            type,
            ModBlockEntities.RADAR_DISPLAY_PANEL,
            RadarDisplayPanelBlockEntity::serverTick
        );
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        Player player = context.getPlayer();
        return defaultBlockState().setValue(
            FACING,
            player == null ? Direction.NORTH : player.getDirection().getOpposite()
        );
    }

    @Override
    public void setPlacedBy(
        final Level level,
        final BlockPos position,
        final BlockState state,
        final @Nullable LivingEntity placer,
        final ItemStack stack
    ) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (level instanceof ServerLevel server) {
            RadarDisplayManager.rebuild(server, position);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(
        final BlockState state,
        final ServerLevel level,
        final BlockPos position,
        final boolean movedByPiston
    ) {
        super.affectNeighborsAfterRemoval(state, level, position, movedByPiston);
        RadarDisplayManager.rebuildNeighbours(
            level,
            position,
            state.getValue(FACING)
        );
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos position,
        final Player player,
        final BlockHitResult hit
    ) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }

        RadarDisplayStructure display = RadarDisplayStructure.resolve(level, position);
        if (!display.valid()) {
            player.sendSystemMessage(Component.literal(
                "Display must form a filled rectangle from 1x1 to 10x10"
            ));
            return InteractionResult.SUCCESS_SERVER;
        }

        RadarDisplayPanelBlockEntity controller =
            level.getBlockEntity(display.controller())
                instanceof RadarDisplayPanelBlockEntity found
                    ? found
                    : null;

        if (player.isShiftKeyDown()) {
            String station = controller == null || controller.link() == null
                ? "OFFLINE"
                : controller.link().radarId().toString().substring(0, 8);
            player.sendSystemMessage(Component.literal(
                "Radar display " + display.width() + "x" + display.height()
                    + " | Range ±"
                    + (int)RadarDisplayConstants.horizontalRadius(display.width())
                    + " X / ±"
                    + (int)RadarDisplayConstants.verticalRadius(display.height())
                    + " Z | Station " + station
            ));
            return InteractionResult.SUCCESS_SERVER;
        }

        if (controller == null || controller.link() == null) {
            player.sendSystemMessage(Component.literal(
                "Radar display is not linked to a station"
            ));
            return InteractionResult.FAIL;
        }

        var link = controller.link();
        RadarStationNetworking.openMap(serverPlayer, new LinkedRadarStation(
            link.dimension(),
            link.centre(),
            link.radarId()
        ));
        return InteractionResult.SUCCESS_SERVER;
    }
}
