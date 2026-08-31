package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Compact visual-only debris batches preserving real connected block fragments. */
public record ClientboundWarheadDebrisPayload(
	UUID impactId,
	double originX,
	double originY,
	double originZ,
	long spawnGameTime,
	boolean nuclear,
	List<Entry> entries
) implements CustomPacketPayload {
	private static final int MAX_ENTRIES = 320;
	private static final int MAX_PARTS_PER_ENTRY = 48;
	public static final Type<ClientboundWarheadDebrisPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_debris")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarheadDebrisPayload> STREAM_CODEC = StreamCodec.of(
		(buffer, payload) -> {
			buffer.writeUUID(payload.impactId);
			buffer.writeDouble(payload.originX);
			buffer.writeDouble(payload.originY);
			buffer.writeDouble(payload.originZ);
			buffer.writeLong(payload.spawnGameTime);
			buffer.writeBoolean(payload.nuclear);
			buffer.writeVarInt(payload.entries.size());
			for (Entry entry : payload.entries) entry.write(buffer);
		},
		buffer -> {
			UUID id = buffer.readUUID();
			double x = buffer.readDouble();
			double y = buffer.readDouble();
			double z = buffer.readDouble();
			long gameTime = buffer.readLong();
			boolean nuclear = buffer.readBoolean();
			int encodedCount = Math.max(0, buffer.readVarInt());
			int count = Math.min(MAX_ENTRIES, encodedCount);
			List<Entry> entries = new ArrayList<>(count);
			for (int index = 0; index < encodedCount; index++) {
				Entry entry = Entry.read(buffer);
				if (index < MAX_ENTRIES) entries.add(entry);
			}
			return new ClientboundWarheadDebrisPayload(id, x, y, z, gameTime,
				nuclear, List.copyOf(entries));
		}
	);

	public ClientboundWarheadDebrisPayload {
		entries = entries == null ? List.of() : List.copyOf(entries.subList(0, Math.min(entries.size(), MAX_ENTRIES)));
	}

	public boolean isWellFormed() {
		if (impactId == null || !Double.isFinite(originX) || !Double.isFinite(originY)
			|| !Double.isFinite(originZ) || entries.size() > MAX_ENTRIES) return false;
		for (Entry entry : entries) if (entry == null || !entry.isWellFormed()) return false;
		return true;
	}

	public ClientboundWarheadDebrisPayload withNuclear(final boolean value) {
		return value == nuclear ? this : new ClientboundWarheadDebrisPayload(
			impactId, originX, originY, originZ, spawnGameTime, value, entries);
	}

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

	public record Part(int blockStateId, byte offsetX, byte offsetY, byte offsetZ) {
		private void write(final RegistryFriendlyByteBuf buffer) {
			buffer.writeVarInt(blockStateId);
			buffer.writeByte(offsetX);
			buffer.writeByte(offsetY);
			buffer.writeByte(offsetZ);
		}

		private static Part read(final RegistryFriendlyByteBuf buffer) {
			return new Part(buffer.readVarInt(), buffer.readByte(), buffer.readByte(), buffer.readByte());
		}

		private boolean isWellFormed() {
			return blockStateId >= 0 && Math.abs((int) offsetX) <= 12
				&& Math.abs((int) offsetY) <= 12 && Math.abs((int) offsetZ) <= 12;
		}
	}

	public record Entry(
		float offsetX,
		float offsetY,
		float offsetZ,
		float velocityX,
		float velocityY,
		float velocityZ,
		float spinX,
		float spinY,
		float spinZ,
		float scale,
		int lifetime,
		List<Part> parts
	) {
		public Entry {
			parts = parts == null ? List.of() : List.copyOf(parts.subList(0, Math.min(parts.size(), MAX_PARTS_PER_ENTRY)));
		}

		private void write(final RegistryFriendlyByteBuf buffer) {
			buffer.writeFloat(offsetX); buffer.writeFloat(offsetY); buffer.writeFloat(offsetZ);
			buffer.writeFloat(velocityX); buffer.writeFloat(velocityY); buffer.writeFloat(velocityZ);
			buffer.writeFloat(spinX); buffer.writeFloat(spinY); buffer.writeFloat(spinZ);
			buffer.writeFloat(scale);
			buffer.writeVarInt(lifetime);
			buffer.writeVarInt(parts.size());
			for (Part part : parts) part.write(buffer);
		}

		private static Entry read(final RegistryFriendlyByteBuf buffer) {
			float offsetX = buffer.readFloat();
			float offsetY = buffer.readFloat();
			float offsetZ = buffer.readFloat();
			float velocityX = buffer.readFloat();
			float velocityY = buffer.readFloat();
			float velocityZ = buffer.readFloat();
			float spinX = buffer.readFloat();
			float spinY = buffer.readFloat();
			float spinZ = buffer.readFloat();
			float scale = buffer.readFloat();
			int lifetime = buffer.readVarInt();
			int encodedParts = Math.max(0, buffer.readVarInt());
			List<Part> parts = new ArrayList<>(Math.min(encodedParts, MAX_PARTS_PER_ENTRY));
			for (int index = 0; index < encodedParts; index++) {
				Part part = Part.read(buffer);
				if (index < MAX_PARTS_PER_ENTRY) parts.add(part);
			}
			return new Entry(offsetX, offsetY, offsetZ, velocityX, velocityY, velocityZ,
				spinX, spinY, spinZ, scale, lifetime, List.copyOf(parts));
		}

		private boolean isWellFormed() {
			if (!finite(offsetX) || !finite(offsetY) || !finite(offsetZ)
				|| !finite(velocityX) || !finite(velocityY) || !finite(velocityZ)
				|| !finite(spinX) || !finite(spinY) || !finite(spinZ)
				|| !finite(scale) || scale < 0.70F || scale > 1.15F
				|| lifetime < 1 || lifetime > 400 || parts.isEmpty()
				|| parts.size() > MAX_PARTS_PER_ENTRY) return false;
			for (Part part : parts) if (part == null || !part.isWellFormed()) return false;
			return true;
		}

		private static boolean finite(final float value) { return Float.isFinite(value); }
	}
}
