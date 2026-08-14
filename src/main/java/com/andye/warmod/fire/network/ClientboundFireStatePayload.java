package com.andye.warmod.fire.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.fire.FirePhase;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sparse authoritative fire-patch snapshot; particles are reconstructed client-side. */
public record ClientboundFireStatePayload(long serverGameTime, boolean complete, List<Entry> entries)
    implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 768;
    public static final Type<ClientboundFireStatePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "fire_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFireStatePayload> STREAM_CODEC =
        StreamCodec.of(ClientboundFireStatePayload::write, ClientboundFireStatePayload::read);

    private static void write(final RegistryFriendlyByteBuf buffer,
        final ClientboundFireStatePayload payload) {
        buffer.writeLong(payload.serverGameTime);
        buffer.writeBoolean(payload.complete);
        int count = Math.min(MAX_ENTRIES, payload.entries.size());
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            Entry entry = payload.entries.get(index);
            buffer.writeLong(entry.id); buffer.writeLong(entry.packedHost);
            buffer.writeByte(entry.face); buffer.writeFloat(entry.localX);
            buffer.writeFloat(entry.localY); buffer.writeFloat(entry.localZ);
            buffer.writeFloat(entry.intensity); buffer.writeFloat(entry.heat);
            buffer.writeFloat(entry.coverage); buffer.writeFloat(entry.smoke);
            buffer.writeByte(entry.phase.ordinal()); buffer.writeLong(entry.seed);
            buffer.writeLong(entry.ignitionGameTime);
            buffer.writeFloat(entry.windX); buffer.writeFloat(entry.windY);
            buffer.writeFloat(entry.windZ);
        }
    }

    private static ClientboundFireStatePayload read(final RegistryFriendlyByteBuf buffer) {
        long gameTime = buffer.readLong();
        boolean complete = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES)
            throw new IllegalArgumentException("Invalid fire entry count");
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long id = buffer.readLong(), host = buffer.readLong();
            byte face = buffer.readByte();
            float localX = buffer.readFloat(), localY = buffer.readFloat(), localZ = buffer.readFloat();
            float intensity = buffer.readFloat(), heat = buffer.readFloat();
            float coverage = buffer.readFloat(), smoke = buffer.readFloat();
            int phaseIndex = buffer.readUnsignedByte();
            FirePhase phase = phaseIndex < FirePhase.values().length
                ? FirePhase.values()[phaseIndex] : FirePhase.SMOLDERING;
            long seed = buffer.readLong(), ignition = buffer.readLong();
            entries.add(new Entry(id, host, face, localX, localY, localZ,
                intensity, heat, coverage, smoke, phase, seed, ignition,
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));
        }
        return new ClientboundFireStatePayload(gameTime, complete, List.copyOf(entries));
    }

    public boolean isWellFormed() {
        if (entries == null || entries.size() > MAX_ENTRIES) return false;
        for (Entry entry : entries) if (entry == null || !entry.isWellFormed()) return false;
        return true;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(long id, long packedHost, byte face,
        float localX, float localY, float localZ, float intensity, float heat,
        float coverage, float smoke, FirePhase phase, long seed,
        long ignitionGameTime, float windX, float windY, float windZ) {
        public boolean isWellFormed() {
            return id > 0L && Byte.toUnsignedInt(face) < 6 && phase != null
                && finiteRange(localX, 0.0F, 1.0F) && finiteRange(localY, 0.0F, 1.0F)
                && finiteRange(localZ, 0.0F, 1.0F) && finiteRange(intensity, 0.0F, 1.2F)
                && finiteRange(heat, 0.0F, 1.5F) && finiteRange(coverage, 0.0F, 1.0F)
                && finiteRange(smoke, 0.0F, 1.0F) && Float.isFinite(windX)
                && Float.isFinite(windY) && Float.isFinite(windZ);
        }
        private static boolean finiteRange(final float value, final float minimum,
            final float maximum) {
            return Float.isFinite(value) && value >= minimum && value <= maximum;
        }
    }
}
