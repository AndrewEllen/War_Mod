package com.andye.warmod.item;

import com.andye.warmod.acoustics.physics.AcousticPropagation;
import com.andye.warmod.testtool.TestTargeting;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadLaunchService;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class NuclearTestStickItem extends Item {
	private static final int COOLDOWN_TICKS=2;public NuclearTestStickItem(final Properties p){super(p);}
	@Override public InteractionResult use(final Level level,final Player player,final InteractionHand hand){if(level.isClientSide()||!(level instanceof ServerLevel server)||!(player instanceof ServerPlayer sp))return InteractionResult.PASS;ItemStack stack=player.getItemInHand(hand);if(sp.getCooldowns().isOnCooldown(stack))return InteractionResult.PASS;Optional<BlockHitResult> target=TestTargeting.findTarget(sp,WarheadConstants.TARGET_RANGE_BLOCKS);if(target.isEmpty()){sp.sendOverlayMessage(Component.literal("No loaded block found within 1000 blocks"));return InteractionResult.SUCCESS_SERVER;}BlockHitResult hit=target.get();Vec3 intended=hit.getLocation().subtract(hit.getDirection().getStepX()*.15,hit.getDirection().getStepY()*.15,hit.getDirection().getStepZ()*.15);Optional<WarheadLaunchService.LaunchResult> launch=WarheadLaunchService.launch(server,sp,intended,WarheadPayloadType.NUCLEAR);if(launch.isEmpty()){sp.sendOverlayMessage(Component.literal("Nuclear launch failed: target area is not loaded"));return InteractionResult.SUCCESS_SERVER;}WarheadLaunchService.LaunchResult result=launch.get();double distance=sp.getEyePosition().distanceTo(result.intendedTarget());long sound=AcousticPropagation.delayTicks(distance,343.0);sp.getCooldowns().addCooldown(stack,COOLDOWN_TICKS);sp.sendOverlayMessage(Component.literal(String.format(Locale.ROOT,"Nuclear warhead inbound: %.0f blocks | Impact: %.1f s | Sound after impact: %.2f s",distance,result.flightTicks()/20.0,sound/20.0)));return InteractionResult.SUCCESS_SERVER;}
}
