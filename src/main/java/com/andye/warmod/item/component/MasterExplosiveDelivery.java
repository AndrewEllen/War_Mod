package com.andye.warmod.item.component;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum MasterExplosiveDelivery implements StringRepresentable {
	DIRECT_WARHEAD("direct_warhead", "Direct warhead"),
	ICBM("icbm", "Full ICBM");

	public static final Codec<MasterExplosiveDelivery> CODEC = StringRepresentable.fromEnum(
		MasterExplosiveDelivery::values
	);

	private final String serializedName;
	private final String displayName;

	MasterExplosiveDelivery(final String serializedName, final String displayName) {
		this.serializedName = serializedName;
		this.displayName = displayName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	public String displayName() {
		return displayName;
	}

	public MasterExplosiveDelivery toggle() {
		return this == DIRECT_WARHEAD ? ICBM : DIRECT_WARHEAD;
	}
}
