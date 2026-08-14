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
public record ClientboundFireStatePayload(long serverGameTime, boolean complete, List<Entry> entries,
    boolean emberComplete, List<EmberEntry> embers)
    implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 768;
	public static final int MAX_EMBERS = 96;
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
		buffer.writeBoolean(payload.emberComplete);
		int emberCount = Math.min(MAX_EMBERS, payload.embers.size());
		buffer.writeVarInt(emberCount);
		for (int index = 0; index < emberCount; index++) {
			EmberEntry ember = payload.embers.get(index);
			buffer.writeLong(ember.id); buffer.writeDouble(ember.x);
			buffer.writeDouble(ember.y); buffer.writeDouble(ember.z);
			buffer.writeFloat(ember.velocityX); buffer.writeFloat(ember.velocityY);
			buffer.writeFloat(ember.velocityZ); buffer.writeFloat(ember.windX);
            buffer.writeFloat(ember.windY); buffer.writeFloat(ember.windZ);
            buffer.writeFloat(ember.intensity);
			buffer.writeLong(ember.seed); buffer.writeLong(ember.startGameTime);
			buffer.writeVarInt(ember.lifetime);
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
		boolean emberComplete = buffer.readBoolean();
		int emberCount = buffer.readVarInt();
		if (emberCount < 0 || emberCount > MAX_EMBERS)
			throw new IllegalArgumentException("Invalid firebrand entry count");
		List<EmberEntry> embers = new ArrayList<>(emberCount);
		for (int index = 0; index < emberCount; index++) embers.add(new EmberEntry(
			buffer.readLong(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
			buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
			buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
			buffer.readLong(), buffer.readLong(), buffer.readVarInt()));
        return new ClientboundFireStatePayload(gameTime, complete, List.copyOf(entries),
			emberComplete, List.copyOf(embers));
    }

    public boolean isWellFormed() {
        if (entries == null || entries.size() > MAX_ENTRIES || embers == null
			|| embers.size() > MAX_EMBERS) return false;
        for (Entry entry : entries) if (entry == null || !entry.isWellFormed()) return false;
		for (EmberEntry ember : embers) if (ember == null || !ember.isWellFormed()) return false;
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

	public record EmberEntry(long id, double x, double y, double z,
		float velocityX, float velocityY, float velocityZ,
        float windX, float windY, float windZ, float intensity,
		long seed, long startGameTime, int lifetime) {
		public boolean isWellFormed() {
			return id > 0L && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
				&& finite(velocityX, 4.0F) && finite(velocityY, 4.0F)
				&& finite(velocityZ, 4.0F) && finite(windX, 2.5F)
                && finite(windY, 2.5F) && finite(windZ, 2.5F)
                && finiteRange(intensity, 0.0F, 1.2F)
				&& lifetime > 0 && lifetime <= 200;
		}
		private static boolean finite(final float value, final float limit) {
			return Float.isFinite(value) && Math.abs(value) <= limit;
		}
		private static boolean finiteRange(final float value, final float minimum,
			final float maximum) {
			return Float.isFinite(value) && value >= minimum && value <= maximum;
		}
	}
}
