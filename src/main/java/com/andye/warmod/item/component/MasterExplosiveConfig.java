package com.andye.warmod.item.component;

import com.andye.warmod.warhead.WarheadYield;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record MasterExplosiveConfig(
	MasterExplosiveDelivery delivery,
	boolean cluster,
	WarheadYield yield,
	boolean customFire
) {
	public static final MasterExplosiveConfig DEFAULT = new MasterExplosiveConfig(
		MasterExplosiveDelivery.DIRECT_WARHEAD,
		false,
		WarheadYield.CONVENTIONAL,
		false
	);

	public static final Codec<MasterExplosiveConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		MasterExplosiveDelivery.CODEC.fieldOf("delivery").forGetter(MasterExplosiveConfig::delivery),
		Codec.BOOL.fieldOf("cluster").forGetter(MasterExplosiveConfig::cluster),
		WarheadYield.CODEC.fieldOf("yield").forGetter(MasterExplosiveConfig::yield),
		Codec.BOOL.optionalFieldOf("custom_fire", false).forGetter(MasterExplosiveConfig::customFire)
	).apply(instance, MasterExplosiveConfig::new));

	public MasterExplosiveConfig {
		if (delivery == null || yield == null) throw new IllegalArgumentException("Invalid explosive configuration");
	}

	public MasterExplosiveConfig withDelivery(final MasterExplosiveDelivery value) {
		return new MasterExplosiveConfig(value, cluster, yield, customFire);
	}

	public MasterExplosiveConfig withCluster(final boolean value) {
		return new MasterExplosiveConfig(delivery, value, yield, customFire);
	}

	public MasterExplosiveConfig withYield(final WarheadYield value) {
		return new MasterExplosiveConfig(delivery, cluster, value, customFire);
	}

	public MasterExplosiveConfig withCustomFire(final boolean value) {
		return new MasterExplosiveConfig(delivery, cluster, yield, value);
	}

	public String summary() {
		return delivery.displayName() + " | " + (cluster ? "Cluster ×4" : "Single") + " | " + yield.displayName();
	}
}
