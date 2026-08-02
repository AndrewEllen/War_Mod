package com.andye.warmod.warhead;

import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public enum WarheadPayloadType {
	CONVENTIONAL("conventional"), NUCLEAR("nuclear");
	public static final StreamCodec<ByteBuf, WarheadPayloadType> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(
		value -> fromSerializedName(value).orElse(CONVENTIONAL), WarheadPayloadType::serializedName);
	private final String serializedName;
	WarheadPayloadType(final String serializedName) { this.serializedName = serializedName; }
	public String serializedName() { return this.serializedName; }
	public static Optional<WarheadPayloadType> fromSerializedName(final String value) {
		if (value == null) return Optional.empty();
		String normalized = value.toLowerCase(Locale.ROOT);
		for (WarheadPayloadType type : values()) if (type.serializedName.equals(normalized)) return Optional.of(type);
		return Optional.empty();
	}
}