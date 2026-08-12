package com.andye.warmod.fire.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.fire.FirePhase;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundFireStatePayload(long serverGameTime, List<Entry> entries)
    implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 512;
    public static final Type<ClientboundFireStatePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "fire_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFireStatePayload> STREAM_CODEC =
        StreamCodec.of(ClientboundFireStatePayload::write, ClientboundFireStatePayload::read);

    private static void write(final RegistryFriendlyByteBuf buffer,
        final ClientboundFireStatePayload payload) {
        buffer.writeLong(payload.serverGameTime);
        int count = Math.min(MAX_ENTRIES, payload.entries.size());
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            Entry entry = payload.entries.get(index);
            buffer.writeLong(entry.packedPosition);
            buffer.writeFloat(entry.intensity);
            buffer.writeFloat(entry.heat);
            buffer.writeByte(entry.phase.ordinal());
            buffer.writeLong(entry.seed);
            buffer.writeFloat(entry.windX);
            buffer.writeFloat(entry.windY);
            buffer.writeFloat(entry.windZ);
        }
    }

    private static ClientboundFireStatePayload read(final RegistryFriendlyByteBuf buffer) {
        long gameTime = buffer.readLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid fire entry count");
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long position = buffer.readLong();
            float intensity = buffer.readFloat();
            float heat = buffer.readFloat();
            int phase = buffer.readUnsignedByte();
            long seed = buffer.readLong();
            float windX = buffer.readFloat();
            float windY = buffer.readFloat();
            float windZ = buffer.readFloat();
            entries.add(new Entry(position, intensity, heat,
                phase >= 0 && phase < FirePhase.values().length
                    ? FirePhase.values()[phase] : FirePhase.SMOLDERING,
                seed, windX, windY, windZ));
        }
        return new ClientboundFireStatePayload(gameTime, List.copyOf(entries));
    }

    public boolean isWellFormed() {
        if (entries == null || entries.size() > MAX_ENTRIES) return false;
        for (Entry entry : entries) if (entry == null || !entry.isWellFormed()) return false;
        return true;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(long packedPosition, float intensity, float heat, FirePhase phase,
        long seed, float windX, float windY, float windZ) {
        public boolean isWellFormed() {
            return phase != null && Float.isFinite(intensity) && intensity >= 0.0F && intensity <= 1.5F
                && Float.isFinite(heat) && heat >= 0.0F && heat <= 1.5F
                && Float.isFinite(windX) && Float.isFinite(windY) && Float.isFinite(windZ);
        }
    }
}
