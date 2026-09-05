package com.andye.warmod.item;

import com.andye.warmod.antiair.AntiAirLaunchDecision;
import com.andye.warmod.antiair.AntiAirLaunchPlanner;
import com.andye.warmod.antiair.AntiAirLaunchService;
import com.andye.warmod.antiair.AntiAirMissileVariant;
import com.andye.warmod.item.component.AntiAirTestVariant;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.testtool.TestTargeting;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public final class AntiAirTestStickItem extends Item {
    public AntiAirTestStickItem(Properties properties) {
        super(properties);
    }

    private static AntiAirMissileVariant variant(ItemStack stack) {
        AntiAirTestVariant selected = stack.get(ModDataComponents.ANTI_AIR_TEST_VARIANT);
        return selected == null ? AntiAirMissileVariant.MK_I : selected.variant();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()
                || !(level instanceof ServerLevel server)
                || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            AntiAirMissileVariant next =
                    variant(stack) == AntiAirMissileVariant.MK_I
                            ? AntiAirMissileVariant.MK_II
                            : AntiAirMissileVariant.MK_I;
            stack.set(ModDataComponents.ANTI_AIR_TEST_VARIANT, new AntiAirTestVariant(next));
            serverPlayer.sendOverlayMessage(
                    Component.literal(
                            "Anti-Air Test Stick: "
                                    + (next.ballisticFallback()
                                            ? "Ballistic fallback"
                                            : "Self-destruct")));
            return InteractionResult.SUCCESS_SERVER;
        }
        var hit = TestTargeting.findTarget(serverPlayer, 1000);
        if (hit.isEmpty()) {
            serverPlayer.sendOverlayMessage(
                    Component.literal("No loaded block found within 1000 blocks"));
            return InteractionResult.SUCCESS_SERVER;
        }
        Vec3 origin = hit.get().getLocation().add(0.0, 1.0, 0.0);
        AntiAirLaunchDecision decision =
                AntiAirLaunchPlanner.plan(
                                server,
                                origin,
                                AntiAirLaunchService.estimatedBurnout(server, origin))
                        .orElse(null);
        if (decision == null) {
            serverPlayer.sendOverlayMessage(Component.literal("Anti-air test launch rejected"));
            return InteractionResult.SUCCESS_SERVER;
        }
        var launched =
                AntiAirLaunchService.launchDebug(
                        server,
                        null,
                        "SERVER",
                        origin,
                        variant(stack),
                        decision);
        if (launched.isEmpty()) {
            serverPlayer.sendOverlayMessage(Component.literal("Anti-air test launch rejected"));
            return InteractionResult.SUCCESS_SERVER;
        }
        serverPlayer.getCooldowns().addCooldown(stack, 2);
        String message =
                switch (decision.mode()) {
                    case TRACKED_INTERCEPT -> "Interceptor launched";
                    case BEST_EFFORT_INTERCEPT -> "Best-effort interceptor launched";
                    case NO_TARGET_ASCENT -> "No intercept target found - test ascent launched";
                };
        serverPlayer.sendOverlayMessage(Component.literal(message));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> lines,
            TooltipFlag flag) {
        lines.accept(Component.literal("Launches a test interceptor from the selected block"));
        lines.accept(Component.literal("Crouch-use to change fallback"));
        lines.accept(
                Component.literal(
                        "Fallback: "
                                + (variant(stack).ballisticFallback()
                                        ? "Ballistic"
                                        : "Self-destruct")));
    }
}
