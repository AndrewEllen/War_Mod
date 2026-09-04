package com.andye.warmod.silo;

import com.andye.warmod.antiair.AntiAirMissileVariant;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadYield;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class MissilePayloadItems {
    private MissilePayloadItems() {}

    public static int guidanceTier(final ItemStack stack) {
        return Math.clamp(
                stack.getOrDefault(
                        com.andye.warmod.item.component.ModDataComponents.MISSILE_GUIDANCE_TIER, 1),
                1,
                3);
    }

    public static ItemStack withGuidance(final net.minecraft.world.item.Item item, final int tier) {
        ItemStack stack = new ItemStack(item);
        stack.set(com.andye.warmod.item.component.ModDataComponents.MISSILE_GUIDANCE_TIER, tier);
        return stack;
    }

    public static Optional<SiloMissileType> missileType(final ItemStack stack) {
        for (SiloMissileType type : SiloMissileType.values())
            if (stack.is(type.item())) return Optional.of(type);
        return Optional.empty();
    }

    public static Optional<WarheadPayloadType> payloadType(final ItemStack stack) {
        return missileType(stack).flatMap(SiloMissileType::payloadType);
    }

    public static Optional<WarheadYield> yield(final ItemStack stack) {
        return missileType(stack).flatMap(SiloMissileType::yield);
    }

    public static Optional<AntiAirMissileVariant> antiAirVariant(final ItemStack stack) {
        return missileType(stack).flatMap(SiloMissileType::antiAirVariant);
    }

    public static boolean isMissile(final ItemStack stack) {
        return missileType(stack).isPresent();
    }

    public static boolean isStrategicStrikeMissile(final ItemStack stack) {
        return missileType(stack)
                .map(type -> type.role() == SiloMissileRole.STRATEGIC_STRIKE)
                .orElse(false);
    }

    public static boolean isInterceptor(final ItemStack stack) {
        return missileType(stack)
                .map(type -> type.role() == SiloMissileRole.INTERCEPTOR)
                .orElse(false);
    }

    public static boolean compatible(final ItemStack current, final ItemStack incoming) {
        return current.isEmpty()
                ? isMissile(incoming)
                : ItemStack.isSameItemSameComponents(current, incoming);
    }
}
