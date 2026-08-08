package com.andye.warmod.entity;

import com.andye.warmod.WarMod;
import com.andye.warmod.artillery.ArtilleryConstants;
import com.andye.warmod.rocket.RocketConstants;
import com.andye.warmod.warhead.WarheadConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntityTypes {
    public static final ResourceKey<EntityType<?>> INCOMING_WARHEAD_KEY = key("incoming_warhead");
    public static final ResourceKey<EntityType<?>> WARHEAD_DEBRIS_KEY = key("warhead_debris");
    public static final ResourceKey<EntityType<?>> ICBM_MISSILE_KEY = key("icbm_missile");
    public static final ResourceKey<EntityType<?>> ROCKET_PROJECTILE_KEY = key("rocket_projectile");
    public static final ResourceKey<EntityType<?>> ARTILLERY_SHELL_KEY = key("artillery_shell");
    public static final ResourceKey<EntityType<?>> PRIMED_YIELD_EXPLOSIVE_KEY = key("primed_yield_explosive");

    public static final EntityType<IncomingWarheadEntity> INCOMING_WARHEAD =
        EntityType.Builder.<IncomingWarheadEntity>of(IncomingWarheadEntity::new, MobCategory.MISC)
            .sized(.45F, 1.6F).noSummon().clientTrackingRange(0).updateInterval(1)
            .build(INCOMING_WARHEAD_KEY);
    public static final EntityType<WarheadDebrisEntity> WARHEAD_DEBRIS =
        EntityType.Builder.<WarheadDebrisEntity>of(WarheadDebrisEntity::new, MobCategory.MISC)
            .sized(.92F, .92F).noSummon().noSave().noLootTable()
            .clientTrackingRange((int)Math.ceil(WarheadConstants.VISUAL_RANGE_BLOCKS / 16.0))
            .updateInterval(2).build(WARHEAD_DEBRIS_KEY);
    public static final EntityType<IcbmMissileEntity> ICBM_MISSILE =
        EntityType.Builder.<IcbmMissileEntity>of(IcbmMissileEntity::new, MobCategory.MISC)
            .sized(1.15F, 5.5F).noSummon().clientTrackingRange(0).updateInterval(1)
            .build(ICBM_MISSILE_KEY);
    public static final EntityType<RocketProjectileEntity> ROCKET_PROJECTILE =
        EntityType.Builder.<RocketProjectileEntity>of(RocketProjectileEntity::new, MobCategory.MISC)
            .sized(.30F, .30F).noSummon()
            .clientTrackingRange((int)Math.ceil(RocketConstants.VISUAL_RANGE_BLOCKS / 16.0))
            .updateInterval(1).build(ROCKET_PROJECTILE_KEY);
    public static final EntityType<ArtilleryShellEntity> ARTILLERY_SHELL =
        EntityType.Builder.<ArtilleryShellEntity>of(ArtilleryShellEntity::new, MobCategory.MISC)
            .sized(.45F, 1.6F).noSummon()
            .clientTrackingRange((int)Math.ceil(ArtilleryConstants.MAXIMUM_RANGE_BLOCKS / 16.0) + 8)
            .updateInterval(1).build(ARTILLERY_SHELL_KEY);
    public static final EntityType<PrimedYieldExplosiveEntity> PRIMED_YIELD_EXPLOSIVE =
        EntityType.Builder.<PrimedYieldExplosiveEntity>of(PrimedYieldExplosiveEntity::new, MobCategory.MISC)
            .sized(.45F, .45F).noSummon().clientTrackingRange(16).updateInterval(1)
            .build(PRIMED_YIELD_EXPLOSIVE_KEY);

    private static boolean registered;
    private ModEntityTypes() { }

    public static void register() {
        if (registered) return;
        Registry.register(BuiltInRegistries.ENTITY_TYPE, INCOMING_WARHEAD_KEY, INCOMING_WARHEAD);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, WARHEAD_DEBRIS_KEY, WARHEAD_DEBRIS);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ICBM_MISSILE_KEY, ICBM_MISSILE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ROCKET_PROJECTILE_KEY, ROCKET_PROJECTILE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ARTILLERY_SHELL_KEY, ARTILLERY_SHELL);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, PRIMED_YIELD_EXPLOSIVE_KEY, PRIMED_YIELD_EXPLOSIVE);
        registered = true;
        WarMod.LOGGER.info("Registered warhead, debris, ICBM, artillery, and explosive entity types.");
    }

    private static ResourceKey<EntityType<?>> key(final String path) {
        return ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WarMod.MOD_ID, path));
    }
}
