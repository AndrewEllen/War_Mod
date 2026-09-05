package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-command bridge for settings that are deliberately owned by one client. */
public record ClientboundWarheadClientControlPayload(Action action, float value)
    implements CustomPacketPayload {
    public enum Action {
        STATUS(0),
        SET_CPU_MODE_PACKED(1),
        SET_CPU_MODE_LEGACY(2),
        SET_RENDER_QUALITY(3),
        RESET_RENDER_QUALITY(4),
        SET_BACKEND_AUTO(5),
        SET_BACKEND_GPU(6),
        SET_BACKEND_CPU(7),
        SET_GPU_DIAGNOSTIC_OFF(8),
        SET_GPU_DIAGNOSTIC_DEPTH_OFF(9),
        SET_GPU_DIAGNOSTIC_DEPTH_ON(10),
        SET_DEBRIS_HORIZONTAL(11),
        SET_DEBRIS_VERTICAL(12),
        RESET_DEBRIS(13),
        DEBRIS_STATUS(14);

        private final int id;

        Action(final int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Action fromId(final int id) {
            for (Action action : values()) {
                if (action.id == id) return action;
            }
            throw new IllegalArgumentException("Unknown War Mod client-control action id: " + id);
        }
    }

    public static final Type<ClientboundWarheadClientControlPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_client_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
        ClientboundWarheadClientControlPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.action.id());
                buffer.writeFloat(payload.value);
            },
            buffer -> new ClientboundWarheadClientControlPayload(
                Action.fromId(buffer.readVarInt()), buffer.readFloat()));

    public ClientboundWarheadClientControlPayload {
        Objects.requireNonNull(action, "action");
    }

    public boolean isWellFormed() {
        return Float.isFinite(value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
