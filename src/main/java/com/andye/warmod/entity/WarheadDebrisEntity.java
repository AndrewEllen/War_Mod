package com.andye.warmod.entity;

import net.minecraft.core.SectionPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Short-lived visual-only block debris; it never places blocks or creates drops. */
public final class WarheadDebrisEntity extends Entity {
	private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE = SynchedEntityData.defineId(WarheadDebrisEntity.class, EntityDataSerializers.BLOCK_STATE);
	private static final EntityDataAccessor<Float> DATA_SPIN_X = SynchedEntityData.defineId(WarheadDebrisEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_SPIN_Y = SynchedEntityData.defineId(WarheadDebrisEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_SPIN_Z = SynchedEntityData.defineId(WarheadDebrisEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_VISUAL_SCALE = SynchedEntityData.defineId(WarheadDebrisEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_MAX_LIFETIME = SynchedEntityData.defineId(WarheadDebrisEntity.class, EntityDataSerializers.INT);
	private int restingTicks;

	public WarheadDebrisEntity(final EntityType<? extends WarheadDebrisEntity> type, final Level level) {
		super(type, level);
		this.setSilent(true);
	}

	public WarheadDebrisEntity(final ServerLevel level, final BlockState state, final Vec3 position, final Vec3 velocity,
		final Vec3 angularVelocity, final int maximumLifetime, final float visualScale) {
		this(ModEntityTypes.WARHEAD_DEBRIS, level);
		this.entityData.set(DATA_BLOCK_STATE, state);
		this.entityData.set(DATA_SPIN_X, (float) angularVelocity.x);
		this.entityData.set(DATA_SPIN_Y, (float) angularVelocity.y);
		this.entityData.set(DATA_SPIN_Z, (float) angularVelocity.z);
		this.entityData.set(DATA_MAX_LIFETIME, maximumLifetime);
		this.entityData.set(DATA_VISUAL_SCALE, visualScale);
		this.setPos(position);
		this.setDeltaMovement(velocity);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		builder.define(DATA_BLOCK_STATE, Blocks.STONE.defaultBlockState());
		builder.define(DATA_SPIN_X, 0.0F);
		builder.define(DATA_SPIN_Y, 0.0F);
		builder.define(DATA_SPIN_Z, 0.0F);
		builder.define(DATA_VISUAL_SCALE, 1.0F);
		builder.define(DATA_MAX_LIFETIME, 75);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.tickCount >= this.maximumLifetime()) { this.discard(); return; }
		Vec3 velocity = this.getDeltaMovement();
		Vec3 destination = this.position().add(velocity);
		if (!this.chunkLoaded(destination)) { this.discard(); return; }
		velocity = velocity.add(0.0, -0.04, 0.0);
		this.move(MoverType.SELF, velocity);
		if (this.onGround()) {
			this.restingTicks++;
			velocity = new Vec3(velocity.x * 0.58, -velocity.y * 0.22, velocity.z * 0.58);
			if (this.restingTicks >= 10 || velocity.lengthSqr() < 0.003) { this.discard(); return; }
		} else {
			this.restingTicks = 0;
			velocity = velocity.scale(0.985);
		}
		this.setDeltaMovement(velocity);
	}

	public BlockState blockState() { return this.entityData.get(DATA_BLOCK_STATE); }
	public Vec3 angularVelocity() { return new Vec3(this.entityData.get(DATA_SPIN_X), this.entityData.get(DATA_SPIN_Y), this.entityData.get(DATA_SPIN_Z)); }
	public float visualScale() { return this.entityData.get(DATA_VISUAL_SCALE); }
	public int maximumLifetime() { return this.entityData.get(DATA_MAX_LIFETIME); }
	public int age() { return this.tickCount; }

	private boolean chunkLoaded(final Vec3 position) {
		int chunkX = SectionPos.blockToSectionCoord(position.x);
		int chunkZ = SectionPos.blockToSectionCoord(position.z);
		return !(this.level() instanceof ServerLevel server) || server.getChunkSource().hasChunk(chunkX, chunkZ);
	}

	@Override public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) { return false; }
	@Override public boolean isPickable() { return false; }
	@Override public boolean isPushable() { return false; }
	@Override public boolean canBeCollidedWith(final Entity entity) { return false; }
	@Override public boolean isAttackable() { return false; }
	@Override protected void readAdditionalSaveData(final ValueInput input) { }
	@Override protected void addAdditionalSaveData(final ValueOutput output) { }
	@Override public boolean shouldBeSaved() { return false; }
}