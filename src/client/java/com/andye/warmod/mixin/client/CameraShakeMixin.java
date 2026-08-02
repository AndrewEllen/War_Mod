package com.andye.warmod.mixin.client;

import com.andye.warmod.acoustics.client.ExplosionShakeManager;
import com.andye.warmod.acoustics.client.ExplosionShakeSample;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraShakeMixin {
	@Shadow protected abstract void setRotation(float yaw, float pitch);
	@Shadow protected abstract void move(float forward, float up, float left);
	@Shadow public abstract float xRot();
	@Shadow public abstract float yRot();
	@Shadow @Final private Quaternionf rotation;

	@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;calculateFov(F)F", shift = At.Shift.BEFORE))
	private void warMod$applyAcousticShake(final DeltaTracker deltaTracker, final CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused() || !minecraft.mouseHandler.isMouseGrabbed()) return;
		ExplosionShakeSample sample = ExplosionShakeManager.INSTANCE.sample(deltaTracker.getGameTimeDeltaPartialTick(true));
		if (sample == ExplosionShakeSample.ZERO) return;
		this.setRotation(this.yRot() + sample.yaw(), this.xRot() + sample.pitch());
		this.rotation.rotateZ((float) Math.toRadians(sample.roll()));
		this.move(sample.forward(), sample.vertical(), sample.lateral());
	}
}