package com.andye.warmod.warhead;

import com.mojang.serialization.Codec;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;

/** Gameplay yield presets used by production warheads and explosive test tools. */
public enum WarheadYield implements StringRepresentable {
	HIGH_EXPLOSIVE(
		"high_explosive", "High Explosive", WarheadPayloadType.CONVENTIONAL,
		0.34F, 0.46F, 1.12F, 120, 42, 0.50
	),
	HIGH_CAPACITY_HE(
		"high_capacity_he", "High-Capacity HE", WarheadPayloadType.CONVENTIONAL,
		0.64F, 0.72F, 1.06F, 260, 96, 0.68
	),
	CONVENTIONAL(
		"conventional", "Conventional", WarheadPayloadType.CONVENTIONAL,
		1.00F, 1.00F, 1.00F, 520, 220, 0.82
	),
	HEAVY_CONVENTIONAL(
		"heavy_conventional", "Heavy Conventional", WarheadPayloadType.CONVENTIONAL,
		1.38F, 1.28F, 0.96F, 860, 390, 0.92
	),
	TACTICAL_NUCLEAR(
		"tactical_nuclear", "Tactical Nuclear", WarheadPayloadType.NUCLEAR,
		1.82F, 1.78F, 0.98F, 1_080, 520, 0.92
	),
	STRATEGIC_NUCLEAR(
		"strategic_nuclear", "Strategic Nuclear", WarheadPayloadType.NUCLEAR,
		2.70F, 2.28F, 0.96F, 1_420, 720, 0.98
	),
	HEAVY_NUCLEAR(
		"heavy_nuclear", "Heavy Nuclear", WarheadPayloadType.NUCLEAR,
		3.55F, 2.86F, 0.91F, 1_760, 920, 1.04
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

	@Override public String getSerializedName() { return serializedName; }
	public String displayName() { return displayName; }
	public WarheadPayloadType payloadType() { return payloadType; }
	public float visualScale() { return visualScale; }
	public float acousticVolume() { return acousticVolume; }
	public float acousticPitch() { return acousticPitch; }
	public int maximumDebris() { return maximumDebris; }
	public int maximumLargeDebris() { return maximumLargeDebris; }
	public double debrisVelocityScale() { return debrisVelocityScale; }
	public boolean nuclear() { return payloadType == WarheadPayloadType.NUCLEAR; }

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
		/* Stage 6 compatibility: existing saved test-stick selections still load. */
		if (normalized.equals("medium_explosive") || normalized.equals("enhanced_high_explosive")) {
			return Optional.of(HIGH_CAPACITY_HE);
		}
		for (WarheadYield yield : values()) {
			if (yield.serializedName.equals(normalized)) return Optional.of(yield);
		}
		return Optional.empty();
	}
}
