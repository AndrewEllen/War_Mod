package com.andye.warmod.entity;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Compatibility-only entity type for old saves. New ICBMs use IcbmFlightControllerManager. */
public final class IcbmMissileEntity extends Entity {
	public IcbmMissileEntity(final EntityType<IcbmMissileEntity> type, final Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.setSilent(true);
	}
	@Override protected void defineSynchedData(final SynchedEntityData.Builder builder) { }
	@Override public void tick() { super.tick(); if (!this.level().isClientSide()) this.discard(); }
	@Override protected void readAdditionalSaveData(final ValueInput input) { this.discard(); }
	@Override protected void addAdditionalSaveData(final ValueOutput output) { }
	@Override public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) { return false; }
	@Override public boolean isPickable() { return false; }
	@Override public boolean isPushable() { return false; }
	@Override public boolean canBeCollidedWith(final Entity entity) { return false; }
	@Override public boolean isAttackable() { return false; }
	@Override public boolean shouldRenderAtSqrDistance(final double distance) { return false; }
}