package com.andye.warmod.item;

import com.andye.warmod.antiair.AntiAirMissileVariant;
import com.andye.warmod.silo.MissilePayloadItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public final class AntiAirMissileItem extends Item {
    private final AntiAirMissileVariant variant;

    public AntiAirMissileItem(Properties properties, AntiAirMissileVariant variant) {
        super(properties);
        this.variant = variant;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(
                "Anti-Air Missile - Tier "
                        + MissilePayloadItems.guidanceTier(stack)
                        + (variant.ballisticFallback()
                                ? " (Ballistic fallback)"
                                : " (Self-destruct)"));
    }

    public AntiAirMissileVariant variant() {
        return variant;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> lines,
            TooltipFlag flag) {
        lines.accept(Component.literal("Automatic target acquisition"));
        lines.accept(
                Component.literal(
                        variant.ballisticFallback()
                                ? "Missed missiles return to the ground"
                                : "Safe aerial self-destruction"));
        lines.accept(Component.literal("Maximum stack: 16"));
    }
}
