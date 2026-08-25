package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundWarheadRemovePayload(UUID warheadId, long stateSequence,
    WarheadNetworkState state, long serverGameTime, double x, double y, double z)
    implements CustomPacketPayload {
	public static final Type<ClientboundWarheadRemovePayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_remove")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarheadRemovePayload>
        STREAM_CODEC = StreamCodec.of(ClientboundWarheadRemovePayload::write,
            ClientboundWarheadRemovePayload::read);

    public ClientboundWarheadRemovePayload(final UUID warheadId) {
        this(warheadId, 1L, WarheadNetworkState.REMOVED, 0L, 0.0, 0.0, 0.0);
    }

	public boolean isWellFormed() {
		return this.warheadId != null && stateSequence > 0L
            && state == WarheadNetworkState.REMOVED && Double.isFinite(x)
            && Double.isFinite(y) && Double.isFinite(z);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

    private static void write(final RegistryFriendlyByteBuf buffer,
        final ClientboundWarheadRemovePayload payload) {
        buffer.writeUUID(payload.warheadId); buffer.writeLong(payload.stateSequence);
        buffer.writeVarInt(payload.state.ordinal()); buffer.writeLong(payload.serverGameTime);
        buffer.writeDouble(payload.x); buffer.writeDouble(payload.y); buffer.writeDouble(payload.z);
    }

    private static ClientboundWarheadRemovePayload read(final RegistryFriendlyByteBuf buffer) {
        UUID id = buffer.readUUID(); long sequence = buffer.readLong();
        int stateOrdinal = buffer.readVarInt();
        if (stateOrdinal != WarheadNetworkState.REMOVED.ordinal())
            throw new IllegalArgumentException("Invalid remove state");
        return new ClientboundWarheadRemovePayload(id, sequence, WarheadNetworkState.REMOVED,
            buffer.readLong(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
