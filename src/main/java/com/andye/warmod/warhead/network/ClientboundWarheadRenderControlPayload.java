package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-command bridge for settings that are deliberately owned by one client. */
public record ClientboundWarheadRenderControlPayload(int action, float value)
    implements CustomPacketPayload {
    public static final int STATUS = 0;
    public static final int PACKED = 1;
    public static final int LEGACY = 2;
    public static final int BUDGET = 3;
    public static final int BUDGET_RESET = 4;
    public static final int DEBRIS_HORIZONTAL = 5;
    public static final int DEBRIS_VERTICAL = 6;
    public static final int DEBRIS_RESET = 7;
    public static final int DEBRIS_STATUS = 8;
    public static final Type<ClientboundWarheadRenderControlPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_render_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
        ClientboundWarheadRenderControlPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ClientboundWarheadRenderControlPayload::action,
            ByteBufCodecs.FLOAT,
            ClientboundWarheadRenderControlPayload::value,
            ClientboundWarheadRenderControlPayload::new);

    public boolean isWellFormed() {
        return action >= STATUS && action <= DEBRIS_STATUS && Float.isFinite(value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
