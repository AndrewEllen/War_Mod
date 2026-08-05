package com.andye.warmod.warhead;

import com.mojang.serialization.Codec;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;

/**
 * Gameplay yield presets used by the master explosive test stick and the
 * custom strategic crater engine.
 *
 * <p>Production conventional and nuclear missiles continue to resolve to the
 * conventional and strategic-nuclear defaults unless an explicit test yield
 * has been registered for their radar root track.</p>
 */
public enum WarheadYield implements StringRepresentable {
	HIGH_EXPLOSIVE(
		"high_explosive", "High Explosive", WarheadPayloadType.CONVENTIONAL,
		0.34F, 0.46F, 1.12F, 32, 8, 0.52
	),
	CONVENTIONAL(
		"conventional", "Conventional", WarheadPayloadType.CONVENTIONAL,
		1.00F, 1.00F, 1.00F, 160, 64, 1.00
	),
	HEAVY_CONVENTIONAL(
		"heavy_conventional", "Heavy Conventional", WarheadPayloadType.CONVENTIONAL,
		1.48F, 1.32F, 0.96F, 192, 80, 1.14
	),
	TACTICAL_NUCLEAR(
		"tactical_nuclear", "Tactical Nuclear", WarheadPayloadType.NUCLEAR,
		1.90F, 1.82F, 0.98F, 224, 96, 1.05
	),
	STRATEGIC_NUCLEAR(
		"strategic_nuclear", "Strategic Nuclear", WarheadPayloadType.NUCLEAR,
		3.00F, 2.40F, 0.96F, 256, 112, 1.10
	),
	HEAVY_NUCLEAR(
		"heavy_nuclear", "Heavy Nuclear", WarheadPayloadType.NUCLEAR,
		4.25F, 3.10F, 0.91F, 320, 144, 1.18
	);

	public static final Codec<WarheadYield> CODEC = StringRepresentable.fromEnum(WarheadYield::values);
	private final String serializedName;
	private final String displayName;
	private final WarheadPayloadType payloadType;
	private final float visualScale;
	private final float acousticVolume;
	private final float acousticPitch;
	private final int maximumDebris;
	private final int maximumLargeDebris;
	private final double debrisVelocityScale;

	WarheadYield(
		final String serializedName,
		final String displayName,
		final WarheadPayloadType payloadType,
		final float visualScale,
		final float acousticVolume,
		final float acousticPitch,
		final int maximumDebris,
		final int maximumLargeDebris,
		final double debrisVelocityScale
	) {
		this.serializedName = serializedName;
		this.displayName = displayName;
		this.payloadType = payloadType;
		this.visualScale = visualScale;
		this.acousticVolume = acousticVolume;
		this.acousticPitch = acousticPitch;
		this.maximumDebris = maximumDebris;
		this.maximumLargeDebris = maximumLargeDebris;
		this.debrisVelocityScale = debrisVelocityScale;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	public String displayName() {
		return displayName;
	}

	public WarheadPayloadType payloadType() {
		return payloadType;
	}

	public float visualScale() {
		return visualScale;
	}

	public float acousticVolume() {
		return acousticVolume;
	}

	public float acousticPitch() {
		return acousticPitch;
	}

	public int maximumDebris() {
		return maximumDebris;
	}

	public int maximumLargeDebris() {
		return maximumLargeDebris;
	}

	public double debrisVelocityScale() {
		return debrisVelocityScale;
	}

	public boolean nuclear() {
		return payloadType == WarheadPayloadType.NUCLEAR;
	}

	public WarheadEffectProfile effectProfile() {
		if (this == HIGH_EXPLOSIVE) return WarheadEffectProfile.TACTICAL_HE;
		return nuclear() ? WarheadEffectProfile.NUCLEAR : WarheadEffectProfile.CONVENTIONAL;
	}

	public static WarheadYield defaultFor(final WarheadPayloadType payloadType) {
		return payloadType == WarheadPayloadType.NUCLEAR ? STRATEGIC_NUCLEAR : CONVENTIONAL;
	}

	public WarheadYield next() {
		WarheadYield[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public WarheadYield previous() {
		WarheadYield[] values = values();
		return values[Math.floorMod(ordinal() - 1, values.length)];
	}

	public static Optional<WarheadYield> fromSerializedName(final String value) {
		if (value == null) return Optional.empty();
		String normalized = value.toLowerCase(Locale.ROOT);
		for (WarheadYield yield : values()) {
			if (yield.serializedName.equals(normalized)) return Optional.of(yield);
		}
		return Optional.empty();
	}
}
