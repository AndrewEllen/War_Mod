package com.andye.warmod.silo;

import com.andye.warmod.antiair.AntiAirMissileVariant;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadYield;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public final class MissilePayloadItems {
    private MissilePayloadItems() { }
    public static Optional<SiloMissileType> missileType(final ItemStack stack) { for (SiloMissileType type : SiloMissileType.values()) if (stack.is(type.item())) return Optional.of(type); return Optional.empty(); }
    public static Optional<WarheadPayloadType> payloadType(final ItemStack stack) { return missileType(stack).flatMap(SiloMissileType::payloadType); }
    public static Optional<WarheadYield> yield(final ItemStack stack) { return missileType(stack).flatMap(SiloMissileType::yield); }
    public static Optional<AntiAirMissileVariant> antiAirVariant(final ItemStack stack) { return missileType(stack).flatMap(SiloMissileType::antiAirVariant); }
    public static boolean isMissile(final ItemStack stack) { return missileType(stack).isPresent(); }
    public static boolean isStrategicStrikeMissile(final ItemStack stack) { return missileType(stack).map(type -> type.role() == SiloMissileRole.STRATEGIC_STRIKE).orElse(false); }
    public static boolean isInterceptor(final ItemStack stack) { return missileType(stack).map(type -> type.role() == SiloMissileRole.INTERCEPTOR).orElse(false); }
    public static boolean compatible(final ItemStack current, final ItemStack incoming) { return current.isEmpty() ? isMissile(incoming) : missileType(current).equals(missileType(incoming)); }
}