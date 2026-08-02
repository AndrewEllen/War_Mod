package com.andye.warmod.particle;

import com.andye.warmod.WarMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Registry;

public final class ModParticleTypes {
	public static final SimpleParticleType WARHEAD_FIREBALL = FabricParticleTypes.simple(true);

	private ModParticleTypes() {
	}

	public static void register() {
		Registry.register(
			BuiltInRegistries.PARTICLE_TYPE,
			Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_fireball"),
			WARHEAD_FIREBALL
		);
		WarMod.LOGGER.info("War Mod particle types registered.");
	}
}
