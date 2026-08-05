package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One compact visual-only debris batch for an impact. */
public record ClientboundWarheadDebrisPayload(
	UUID impactId,
	double originX,
	double originY,
	double originZ,
	long spawnGameTime,
	List<Entry> entries
) implements CustomPacketPayload {
	private static final int MAX_ENTRIES = 1_024;
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
			buffer.writeVarInt(payload.entries.size());
			for (Entry entry : payload.entries) entry.write(buffer);
		},
		buffer -> {
			UUID id = buffer.readUUID();
			double x = buffer.readDouble();
			double y = buffer.readDouble();
			double z = buffer.readDouble();
			long gameTime = buffer.readLong();
			int encodedCount = Math.max(0, buffer.readVarInt());
			int count = Math.min(MAX_ENTRIES, encodedCount);
			List<Entry> entries = new ArrayList<>(count);
			for (int index = 0; index < encodedCount; index++) {
				Entry entry = Entry.read(buffer);
				if (index < MAX_ENTRIES) entries.add(entry);
			}
			return new ClientboundWarheadDebrisPayload(id, x, y, z, gameTime, List.copyOf(entries));
		}
	);

	public ClientboundWarheadDebrisPayload {
		entries = entries == null ? List.of() : List.copyOf(entries.subList(0, Math.min(entries.size(), MAX_ENTRIES)));
	}

	public boolean isWellFormed() {
		if (this.impactId == null || !Double.isFinite(this.originX) || !Double.isFinite(this.originY)
			|| !Double.isFinite(this.originZ) || this.entries.size() > MAX_ENTRIES) return false;
		for (Entry entry : this.entries) if (entry == null || !entry.isWellFormed()) return false;
		return true;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public record Entry(
		int blockStateId,
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
		int lifetime
	) {
		private void write(final RegistryFriendlyByteBuf buffer) {
			buffer.writeVarInt(this.blockStateId);
			buffer.writeFloat(this.offsetX); buffer.writeFloat(this.offsetY); buffer.writeFloat(this.offsetZ);
			buffer.writeFloat(this.velocityX); buffer.writeFloat(this.velocityY); buffer.writeFloat(this.velocityZ);
			buffer.writeFloat(this.spinX); buffer.writeFloat(this.spinY); buffer.writeFloat(this.spinZ);
			buffer.writeFloat(this.scale);
			buffer.writeVarInt(this.lifetime);
		}

		private static Entry read(final RegistryFriendlyByteBuf buffer) {
			return new Entry(
				buffer.readVarInt(),
				buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
				buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
				buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
				buffer.readFloat(), buffer.readVarInt()
			);
		}

		private boolean isWellFormed() {
			return this.blockStateId >= 0 && finite(this.offsetX) && finite(this.offsetY) && finite(this.offsetZ)
				&& finite(this.velocityX) && finite(this.velocityY) && finite(this.velocityZ)
				&& finite(this.spinX) && finite(this.spinY) && finite(this.spinZ)
				&& finite(this.scale) && this.scale > 0.05F && this.scale <= 4.0F
				&& this.lifetime >= 1 && this.lifetime <= 400;
		}

		private static boolean finite(final float value) {
			return Float.isFinite(value);
		}
	}
}
