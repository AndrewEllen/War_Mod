package com.andye.warmod.entity;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.WarheadImpactService;
import com.andye.warmod.warhead.WarheadTrajectory;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class IncomingWarheadEntity extends Entity {
	private UUID warheadId;
	private UUID ownerPlayerId;
	private Vec3 startPosition;
	private Vec3 intendedTarget;
	private long launchGameTime;
	private int flightTicks;
	private long visualSeed;
	private boolean impacted;

	public IncomingWarheadEntity(final EntityType<IncomingWarheadEntity> type, final Level level) {
		super(type, level);
		this.warheadId = null;
		this.ownerPlayerId = null;
		this.startPosition = Vec3.ZERO;
		this.intendedTarget = Vec3.ZERO;
		this.launchGameTime = Long.MIN_VALUE;
		this.flightTicks = 0;
		this.visualSeed = 0L;
		this.impacted = false;
		this.noPhysics = true;
		this.setNoGravity(true);
		this.setSilent(true);
	}

	public IncomingWarheadEntity(
		final EntityType<IncomingWarheadEntity> type,
		final ServerLevel level,
		final UUID warheadId,
		final UUID ownerPlayerId,
		final Vec3 startPosition,
		final Vec3 intendedTarget,
		final long launchGameTime,
		final int flightTicks,
		final long visualSeed
	) {
		this(type, level);
		this.warheadId = Objects.requireNonNull(warheadId, "warheadId");
		this.ownerPlayerId = ownerPlayerId;
		this.startPosition = Objects.requireNonNull(startPosition, "startPosition");
		this.intendedTarget = Objects.requireNonNull(intendedTarget, "intendedTarget");
		this.launchGameTime = launchGameTime;
		this.flightTicks = flightTicks;
		this.visualSeed = visualSeed;
		this.setPos(startPosition);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (this.isRemoved() || this.impacted || !(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		if (!this.isValidState()) {
			this.cancelVisual(serverLevel);
			this.discard();
			return;
		}

		long elapsedGameTicks = serverLevel.getGameTime() - this.launchGameTime;
		double elapsedTicks = Math.max(0.0, Math.min(Integer.MAX_VALUE, elapsedGameTicks));
		Vec3 previousPosition = WarheadTrajectory.position(
			this.startPosition,
			this.intendedTarget,
			Math.max(0.0, elapsedTicks - 1.0),
			this.flightTicks
		);
		Vec3 nextPosition = WarheadTrajectory.position(
			this.startPosition,
			this.intendedTarget,
			elapsedTicks,
			this.flightTicks
		);
		if (!nextPosition.isFinite() || !previousPosition.isFinite()) {
			this.cancelVisual(serverLevel);
			this.discard();
			return;
		}

		if (!isChunkLoaded(serverLevel, nextPosition)) {
			this.cancelVisual(serverLevel);
			this.discard();
			return;
		}

		RaycastResult raycast = raycastLoaded(serverLevel, previousPosition, nextPosition);
		if (raycast.missingChunk()) {
			this.cancelVisual(serverLevel);
			this.discard();
			return;
		}

		if (raycast.hit().isPresent()) {
			this.impact(serverLevel, raycast.hit().get().getLocation());
		} else if (elapsedGameTicks >= this.flightTicks) {
			this.impact(serverLevel, this.intendedTarget);
		} else {
			this.setPos(nextPosition);
			this.setDeltaMovement(nextPosition.subtract(previousPosition));
			this.updateTravelRotation(nextPosition.subtract(previousPosition));
		}
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(final Entity entity) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(final double distance) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		this.warheadId = input.read("WarheadId", UUIDUtil.STRING_CODEC).orElse(null);
		this.ownerPlayerId = input.read("OwnerPlayerId", UUIDUtil.STRING_CODEC).orElse(null);
		this.startPosition = input.read("StartPosition", Vec3.CODEC).orElse(Vec3.ZERO);
		this.intendedTarget = input.read("IntendedTarget", Vec3.CODEC).orElse(Vec3.ZERO);
		this.launchGameTime = input.getLongOr("LaunchGameTime", Long.MIN_VALUE);
		this.flightTicks = input.getIntOr("FlightTicks", 0);
		this.visualSeed = input.getLongOr("VisualSeed", 0L);
		this.impacted = input.getBooleanOr("Impacted", false);
		if (!this.isValidState() || this.impacted) {
			this.discard();
			return;
		}
		this.setPos(this.startPosition);
		this.setNoGravity(true);
		this.setSilent(true);
		this.noPhysics = true;
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		output.storeNullable("WarheadId", UUIDUtil.STRING_CODEC, this.warheadId);
		output.storeNullable("OwnerPlayerId", UUIDUtil.STRING_CODEC, this.ownerPlayerId);
		if (this.startPosition != null && this.startPosition.isFinite()) {
			output.store("StartPosition", Vec3.CODEC, this.startPosition);
		}
		if (this.intendedTarget != null && this.intendedTarget.isFinite()) {
			output.store("IntendedTarget", Vec3.CODEC, this.intendedTarget);
		}
		output.putLong("LaunchGameTime", this.launchGameTime);
		output.putInt("FlightTicks", this.flightTicks);
		output.putLong("VisualSeed", this.visualSeed);
		output.putBoolean("Impacted", this.impacted);
	}

	private boolean isValidState() {
		return this.warheadId != null
			&& this.startPosition != null
			&& this.intendedTarget != null
			&& this.startPosition.isFinite()
			&& this.intendedTarget.isFinite()
			&& this.startPosition.distanceTo(this.intendedTarget) <= 8192.0
			&& this.launchGameTime != Long.MIN_VALUE
			&& this.flightTicks >= 1
			&& this.flightTicks <= 200;
	}

	private void impact(final ServerLevel serverLevel, final Vec3 hitPosition) {
		if (this.impacted || !hitPosition.isFinite()) {
			return;
		}

		this.impacted = true;
		ServerPlayer owner = null;
		if (this.ownerPlayerId != null && serverLevel.getServer() != null) {
			ServerPlayer candidate = serverLevel.getServer().getPlayerList().getPlayer(this.ownerPlayerId);
			if (candidate != null && candidate.level() == serverLevel) {
				owner = candidate;
			}
		}

		if (SharedConstants.IS_RUNNING_IN_IDE) {
			WarMod.LOGGER.info(
				"Warhead {} impacted: position={}, flight={}",
				this.warheadId,
				hitPosition,
				Math.max(0L, serverLevel.getGameTime() - this.launchGameTime)
			);
		}
		WarheadImpactService.impact(serverLevel, owner, this.warheadId, hitPosition, this.visualSeed);
		this.discard();
	}

	private void cancelVisual(final ServerLevel serverLevel) {
		if (this.warheadId != null && this.intendedTarget != null && this.intendedTarget.isFinite()) {
			WarheadVisualNetworking.sendRemove(serverLevel, this.warheadId, this.intendedTarget);
		}
	}

	private void updateTravelRotation(final Vec3 velocity) {
		if (velocity.lengthSqr() < 1.0E-8) {
			return;
		}
		this.setYRot((float)(Math.atan2(velocity.z, velocity.x) * 180.0 / Math.PI) - 90.0F);
		this.setXRot((float)(-Math.atan2(velocity.y, velocity.horizontalDistance()) * 180.0 / Math.PI));
	}

	private static boolean isChunkLoaded(final ServerLevel level, final Vec3 position) {
		return level.getChunkSource().hasChunk(
			SectionPos.blockToSectionCoord(position.x),
			SectionPos.blockToSectionCoord(position.z)
		);
	}

	private RaycastResult raycastLoaded(final ServerLevel level, final Vec3 from, final Vec3 to) {
		AtomicBoolean missingChunk = new AtomicBoolean(false);
		ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this);
		Optional<BlockHitResult> hit = BlockGetter.traverseBlocks(
			from,
			to,
			context,
			(clipContext, pos) -> {
				if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
					missingChunk.set(true);
					return Optional.empty();
				}
				BlockState state = level.getBlockState(pos);
				VoxelShape shape = clipContext.getBlockShape(state, level, pos);
				BlockHitResult blockHit = level.clipWithInteractionOverride(from, to, pos, shape, state);
				return blockHit == null ? null : Optional.of(blockHit);
			},
			ignored -> Optional.empty()
		);
		return new RaycastResult(hit == null ? Optional.empty() : hit, missingChunk.get());
	}

	private record RaycastResult(Optional<BlockHitResult> hit, boolean missingChunk) {
	}
}