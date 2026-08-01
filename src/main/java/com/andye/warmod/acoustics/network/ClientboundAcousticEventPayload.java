package com.andye.warmod.acoustics.network;

import com.andye.warmod.WarMod;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

public record ClientboundAcousticEventPayload(
	UUID eventId,
	Identifier definitionId,
	double sourceX,
	double sourceY,
	double sourceZ,
	long emissionGameTime,
	float volume,
	float pitch,
	long randomSeed,
	SoundSource soundSource
) implements CustomPacketPayload {
	public static final Type<ClientboundAcousticEventPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "acoustic_event")
	);

	private static final Identifier INVALID_DEFINITION_ID = Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "invalid_acoustic_definition");
	private static final StreamCodec<ByteBuf, Identifier> SAFE_IDENTIFIER_CODEC = ByteBufCodecs.STRING_UTF8.map(
		value -> {
			Identifier parsed = Identifier.tryParse(value);
			return parsed == null ? INVALID_DEFINITION_ID : parsed;
		},
		Identifier::toString
	);
	private static final StreamCodec<ByteBuf, SoundSource> SOUND_SOURCE_CODEC = ByteBufCodecs.VAR_INT.map(
		value -> value >= 0 && value < SoundSource.values().length ? SoundSource.values()[value] : SoundSource.BLOCKS,
		SoundSource::ordinal
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAcousticEventPayload> STREAM_CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC,
		ClientboundAcousticEventPayload::eventId,
		SAFE_IDENTIFIER_CODEC,
		ClientboundAcousticEventPayload::definitionId,
		ByteBufCodecs.DOUBLE,
		ClientboundAcousticEventPayload::sourceX,
		ByteBufCodecs.DOUBLE,
		ClientboundAcousticEventPayload::sourceY,
		ByteBufCodecs.DOUBLE,
		ClientboundAcousticEventPayload::sourceZ,
		ByteBufCodecs.LONG,
		ClientboundAcousticEventPayload::emissionGameTime,
		ByteBufCodecs.FLOAT,
		ClientboundAcousticEventPayload::volume,
		ByteBufCodecs.FLOAT,
		ClientboundAcousticEventPayload::pitch,
		ByteBufCodecs.LONG,
		ClientboundAcousticEventPayload::randomSeed,
		SOUND_SOURCE_CODEC,
		ClientboundAcousticEventPayload::soundSource,
		ClientboundAcousticEventPayload::new
	);

	public ClientboundAcousticEventPayload {
		if (eventId == null || definitionId == null || soundSource == null) {
			throw new IllegalArgumentException("Acoustic payload identifiers and sound source cannot be null");
		}
	}

	public boolean isWellFormed() {
		return eventId != null
			&& definitionId != null
			&& Double.isFinite(sourceX)
			&& Double.isFinite(sourceY)
			&& Double.isFinite(sourceZ)
			&& Float.isFinite(volume)
			&& volume >= 0.0F
			&& Float.isFinite(pitch)
			&& pitch > 0.0F
			&& soundSource != null;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
