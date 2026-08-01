package com.andye.warmod.entity;

import com.andye.warmod.WarMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntityTypes {
	public static final ResourceKey<EntityType<?>> INCOMING_WARHEAD_KEY = ResourceKey.create(
		Registries.ENTITY_TYPE,
		Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "incoming_warhead")
	);
	public static final EntityType<IncomingWarheadEntity> INCOMING_WARHEAD = EntityType.Builder.<IncomingWarheadEntity>of(
		IncomingWarheadEntity::new,
		MobCategory.MISC
	)
		.sized(0.45F, 1.6F)
		.noSummon()
		.clientTrackingRange(0)
		.updateInterval(1)
		.build(INCOMING_WARHEAD_KEY);

	private static boolean registered;

	private ModEntityTypes() {
	}

	public static void register() {
		if (registered) {
			return;
		}

		Registry.register(BuiltInRegistries.ENTITY_TYPE, INCOMING_WARHEAD_KEY, INCOMING_WARHEAD);
		registered = true;
		WarMod.LOGGER.info("Registered {} as a server-authoritative incoming warhead entity.", INCOMING_WARHEAD_KEY.identifier());
	}
}