package com.andye.warmod.item;

import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.icbm.IcbmLaunchService;
import com.andye.warmod.item.component.IcbmTestDeliveryMode;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.testtool.TestTargeting;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class IcbmTestStickItem extends Item {
    private static final int COOLDOWN_TICKS = 2;
    public IcbmTestStickItem(Properties properties) { super(properties); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            IcbmTestDeliveryMode mode = stack.getOrDefault(ModDataComponents.ICBM_TEST_DELIVERY_MODE, IcbmTestDeliveryMode.SINGLE).toggle();
            stack.set(ModDataComponents.ICBM_TEST_DELIVERY_MODE, mode);
            sp.sendOverlayMessage(Component.literal("Conventional ICBM Test Stick: " + (mode == IcbmTestDeliveryMode.SINGLE ? "Single warhead" : "Cluster - 4 warheads")));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (sp.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
        Optional<BlockHitResult> target = TestTargeting.findTarget(sp, WarheadConstants.TARGET_RANGE_BLOCKS);
        if (target.isEmpty()) { sp.sendOverlayMessage(Component.literal("No loaded block found within 1000 blocks")); return InteractionResult.SUCCESS_SERVER; }
        Vec3 intended = inside(target.get());
        IcbmTestDeliveryMode mode = stack.getOrDefault(ModDataComponents.ICBM_TEST_DELIVERY_MODE, IcbmTestDeliveryMode.SINGLE);
        WarheadDeliveryMode delivery = mode == IcbmTestDeliveryMode.CLUSTER_FOUR ? WarheadDeliveryMode.CLUSTER_FOUR : WarheadDeliveryMode.SINGLE;
        Optional<IcbmLaunchService.LaunchResult> launch = IcbmLaunchService.launch(server, sp, intended, WarheadPayloadType.CONVENTIONAL, delivery);
        if (launch.isEmpty()) { sp.sendOverlayMessage(Component.literal("ICBM launch failed: invalid target or flight plan")); return InteractionResult.SUCCESS_SERVER; }
        IcbmFlightPlan plan = launch.get().flightPlan(); sp.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        sp.sendOverlayMessage(Component.literal(String.format(Locale.ROOT, "ICBM launched (%s): %.0f blocks | Boost: %.1f s | Coast: %.1f s | Terminal: %.1f s", mode == IcbmTestDeliveryMode.CLUSTER_FOUR ? "cluster" : "single", sp.getEyePosition().distanceTo(intended), plan.boostTicks() / 20.0, plan.coastTicks() / 20.0, launch.get().terminalTicks() / 20.0)));
        return InteractionResult.SUCCESS_SERVER;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        IcbmTestDeliveryMode mode = stack.getOrDefault(ModDataComponents.ICBM_TEST_DELIVERY_MODE, IcbmTestDeliveryMode.SINGLE);
        tooltip.accept(Component.literal("Mode: " + (mode == IcbmTestDeliveryMode.SINGLE ? "Single warhead" : "Cluster - 4 warheads")));
    }
    private static Vec3 inside(BlockHitResult hit) { return hit.getLocation().subtract(hit.getDirection().getStepX() * .15, hit.getDirection().getStepY() * .15, hit.getDirection().getStepZ() * .15); }
}