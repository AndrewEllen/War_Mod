package com.andye.warmod.silo;

import com.andye.warmod.antiair.AntiAirMissileVariant;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.item.YieldMissileItem;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadYield;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public final class MissilePayloadItems {
    private MissilePayloadItems() { }

    public static Optional<SiloMissileType> missileType(final ItemStack stack) {
        if (stack.getItem() instanceof YieldMissileItem missile) {
            if (missile.yield().nuclear()) {
                return Optional.of(missile.cluster()
                    ? SiloMissileType.NUCLEAR_CLUSTER_ICBM : SiloMissileType.NUCLEAR_ICBM);
            }
            return Optional.of(missile.cluster()
                ? SiloMissileType.CONVENTIONAL_CLUSTER_ICBM : SiloMissileType.CONVENTIONAL_ICBM);
        }
        if (stack.is(ModItems.CONVENTIONAL_ICBM)) return Optional.of(SiloMissileType.CONVENTIONAL_ICBM);
        if (stack.is(ModItems.CONVENTIONAL_CLUSTER_ICBM)) return Optional.of(SiloMissileType.CONVENTIONAL_CLUSTER_ICBM);
        if (stack.is(ModItems.NUCLEAR_ICBM)) return Optional.of(SiloMissileType.NUCLEAR_ICBM);
        if (stack.is(ModItems.NUCLEAR_CLUSTER_ICBM)) return Optional.of(SiloMissileType.NUCLEAR_CLUSTER_ICBM);
        if (stack.is(ModItems.ANTI_AIR_MISSILE_MK1)) return Optional.of(SiloMissileType.ANTI_AIR_MK_I);
        if (stack.is(ModItems.ANTI_AIR_MISSILE_MK2)) return Optional.of(SiloMissileType.ANTI_AIR_MK_II);
        return Optional.empty();
    }

    public static Optional<WarheadYield> yield(final ItemStack stack) {
        if (stack.getItem() instanceof YieldMissileItem missile) return Optional.of(missile.yield());
        if (stack.is(ModItems.NUCLEAR_ICBM) || stack.is(ModItems.NUCLEAR_CLUSTER_ICBM)) {
            return Optional.of(WarheadYield.STRATEGIC_NUCLEAR);
        }
        if (stack.is(ModItems.CONVENTIONAL_ICBM) || stack.is(ModItems.CONVENTIONAL_CLUSTER_ICBM)) {
            return Optional.of(WarheadYield.CONVENTIONAL);
        }
        return Optional.empty();
    }

    public static Optional<WarheadPayloadType> payloadType(final ItemStack stack) {
        return missileType(stack).flatMap(SiloMissileType::payloadType);
    }
    public static Optional<AntiAirMissileVariant> antiAirVariant(final ItemStack stack) {
        return missileType(stack).flatMap(SiloMissileType::antiAirVariant);
    }
    public static boolean isMissile(final ItemStack stack) { return missileType(stack).isPresent(); }
    public static boolean isStrategicStrikeMissile(final ItemStack stack) {
        return missileType(stack).map(t -> t.role() == SiloMissileRole.STRATEGIC_STRIKE).orElse(false);
    }
    public static boolean isInterceptor(final ItemStack stack) {
        return missileType(stack).map(t -> t.role() == SiloMissileRole.INTERCEPTOR).orElse(false);
    }

    /** Never co-mingle two explicit yields in the same silo stack. */
    public static boolean compatible(final ItemStack a, final ItemStack b) {
        if (a.isEmpty()) return isMissile(b);
        if (!isMissile(a) || !isMissile(b)) return false;
        if (a.getItem() instanceof YieldMissileItem || b.getItem() instanceof YieldMissileItem) {
            return a.getItem() == b.getItem();
        }
        return missileType(a).equals(missileType(b));
    }
}
