package com.andye.warmod.item;

import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.block.RadarStationBlock;
import com.andye.warmod.block.RadarStationStructure;
import com.andye.warmod.block.entity.RadarDisplayPanelBlockEntity;
import com.andye.warmod.block.entity.RadarStationBlockEntity;
import com.andye.warmod.item.component.LinkedRadarStation;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.radar.display.RadarDisplayStructure;
import com.andye.warmod.radar.station.network.RadarStationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class RadarItem extends Item {
    public RadarItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)
            || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.FAIL;
        }

        BlockState state = level.getBlockState(context.getClickedPos());
        LinkedRadarStation link = null;

        if (state.is(ModBlocks.RADAR_STATION)) {
            RadarStationBlockEntity station = RadarStationBlock.resolve(
                level,
                context.getClickedPos(),
                state
            );
            if (station == null || !RadarStationStructure.complete(
                level,
                station.getBlockPos(),
                station.facing()
            )) {
                serverPlayer.sendSystemMessage(Component.literal(
                    "Radar Station is incomplete"
                ));
                return InteractionResult.FAIL;
            }
            link = new LinkedRadarStation(
                level.dimension(),
                station.getBlockPos(),
                station.radarId()
            );
        } else if (state.is(ModBlocks.RADAR_DISPLAY_PANEL)) {
            RadarDisplayStructure display = RadarDisplayStructure.resolve(
                level,
                context.getClickedPos()
            );
            if (!display.valid()) {
                serverPlayer.sendSystemMessage(Component.literal(
                    "Radar display structure is incomplete"
                ));
                return InteractionResult.FAIL;
            }
            if (!(level.getBlockEntity(display.controller())
                    instanceof RadarDisplayPanelBlockEntity controller)
                || controller.link() == null) {
                serverPlayer.sendSystemMessage(Component.literal(
                    "Radar display is not linked to a station"
                ));
                return InteractionResult.FAIL;
            }
            var displayLink = controller.link();
            link = new LinkedRadarStation(
                displayLink.dimension(),
                displayLink.centre(),
                displayLink.radarId()
            );
        } else {
            return InteractionResult.PASS;
        }

        context.getItemInHand().set(ModDataComponents.LINKED_RADAR_STATION, link);
        serverPlayer.sendSystemMessage(Component.literal(
            "Remote Display linked to station "
                + link.radarId().toString().substring(0, 8)
        ));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult use(
        final Level level,
        final Player player,
        final InteractionHand hand
    ) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        LinkedRadarStation link = stack.get(ModDataComponents.LINKED_RADAR_STATION);
        if (link == null) {
            player.sendSystemMessage(Component.literal(
                "Shift-right-click a Radar Station or linked display first"
            ));
            return InteractionResult.FAIL;
        }
        return RadarStationNetworking.openMap(serverPlayer, link)
            ? InteractionResult.SUCCESS_SERVER
            : InteractionResult.FAIL;
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return stack.has(ModDataComponents.LINKED_RADAR_STATION);
    }
}
