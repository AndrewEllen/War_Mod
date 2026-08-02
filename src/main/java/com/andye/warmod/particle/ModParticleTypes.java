package com.andye.warmod.particle;

import com.andye.warmod.WarMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class ModParticleTypes {
	public static final SimpleParticleType WARHEAD_FIREBALL = FabricParticleTypes.simple(true);
	public static final SimpleParticleType WARHEAD_SMOKE = FabricParticleTypes.simple(true);

	private ModParticleTypes() { }

	public static void register() {
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_fireball"), WARHEAD_FIREBALL);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_smoke"), WARHEAD_SMOKE);
		WarMod.LOGGER.info("War Mod particle types registered.");
	}
}