package com.andye.warmod.block.entity;

import com.andye.warmod.radar.display.RadarDisplayConstants;
import com.andye.warmod.radar.display.RadarDisplayLink;
import com.andye.warmod.radar.display.network.RadarDisplayNetworking;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class RadarDisplayPanelBlockEntity extends BlockEntity {
    private @Nullable UUID displayId;
    private BlockPos controller;
    private int size;
    private boolean valid;
    private @Nullable RadarDisplayLink link;

    private long lastBroadcastGameTime = Long.MIN_VALUE;

    public RadarDisplayPanelBlockEntity(
        final BlockPos position,
        final BlockState state
    ) {
        super(ModBlockEntities.RADAR_DISPLAY_PANEL, position, state);
        controller = position;
    }

    public static void serverTick(
        final Level level,
        final BlockPos position,
        final BlockState state,
        final RadarDisplayPanelBlockEntity display
    ) {
        if (!(level instanceof ServerLevel server)
            || !display.controllerPanel()
            || display.displayId == null) {
            return;
        }

        long now = server.getGameTime();

        // Two updates per second, plus immediate updates from linking/rebuild.
        if (display.lastBroadcastGameTime != Long.MIN_VALUE && now - display.lastBroadcastGameTime < 10L) {
            return;
        }

        display.lastBroadcastGameTime = now;
        RadarDisplayNetworking.sendState(display);
    }

    public void configure(
        final @Nullable UUID id,
        final BlockPos controller,
        final int size,
        final boolean valid,
        final @Nullable RadarDisplayLink commonLink
    ) {
        boolean changed =
            !Objects.equals(displayId, id)
            || !this.controller.equals(controller)
            || this.size != size
            || this.valid != valid
            || !Objects.equals(link, commonLink);

        displayId = id;
        this.controller = controller.immutable();
        this.size = size;
        this.valid = valid;
        link = commonLink;

        if (changed) {
            sync();
        }
    }

    public void link(final RadarDisplayLink value) {
        if (Objects.equals(link, value)) {
            return;
        }

        link = value;
        sync();

        if (controllerPanel()) {
            RadarDisplayNetworking.sendState(this);
        }
    }

    public void clearLink() {
        if (link == null) {
            return;
        }

        link = null;
        sync();

        if (controllerPanel()) {
            RadarDisplayNetworking.sendState(this);
        }
    }

    public @Nullable RadarDisplayLink link() {
        return link;
    }

    public @Nullable UUID displayId() {
        return displayId;
    }

    public BlockPos controller() {
        return controller;
    }

    public int size() {
        return size;
    }

    public int radius() {
        return valid
            ? RadarDisplayConstants.radius(size)
            : 0;
    }

    public boolean valid() {
        return valid;
    }

    public boolean controllerPanel() {
        return valid && worldPosition.equals(controller);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel server
            && controllerPanel()
            && displayId != null) {
            RadarDisplayNetworking.clear(
                server,
                controller,
                displayId
            );
        }

        super.setRemoved();
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);

        output.storeNullable(
            "display_id",
            UUIDUtil.CODEC,
            displayId
        );

        output.store(
            "controller",
            BlockPos.CODEC,
            controller
        );

        output.putInt("size", size);
        output.putBoolean("valid", valid);

        if (link != null) {
            output.store(
                "link",
                RadarDisplayLink.CODEC,
                link
            );
        }
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);

        displayId = input.read(
            "display_id",
            UUIDUtil.CODEC
        ).orElse(null);

        controller = input.read(
            "controller",
            BlockPos.CODEC
        ).orElse(worldPosition);

        size = input.getIntOr("size", 0);
        valid = input.getBooleanOr("valid", false);

        link = input.read(
            "link",
            RadarDisplayLink.CODEC
        ).orElse(null);
    }

    private void sync() {
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(
                worldPosition,
                getBlockState(),
                getBlockState(),
                3
            );
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(
        final net.minecraft.core.HolderLookup.Provider registries
    ) {
        return saveCustomOnly(registries);
    }
}
