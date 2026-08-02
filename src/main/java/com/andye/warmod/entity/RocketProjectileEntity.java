package com.andye.warmod.entity;

import com.andye.warmod.rocket.RocketCollisionDetector;
import com.andye.warmod.rocket.RocketConstants;
import com.andye.warmod.rocket.RocketImpactService;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RocketProjectileEntity extends Entity {
    private @Nullable UUID ownerId;
    private boolean impactHandled;

    public RocketProjectileEntity(final EntityType<? extends RocketProjectileEntity> type, final Level level) {
        super(type, level);
    }

    public RocketProjectileEntity(final ServerLevel level, final UUID ownerId, final Vec3 position,
        final Vec3 velocity) {
        this(ModEntityTypes.ROCKET_PROJECTILE, level);
        this.ownerId = ownerId;
        this.setPos(position);
        this.setDeltaMovement(velocity);
    }

    @Override protected void defineSynchedData(final SynchedEntityData.Builder builder) { }

    @Override public void tick() {
        super.tick();
        if (this.tickCount >= RocketConstants.LIFETIME_TICKS || !this.position().isFinite()) { this.discard(); return; }
        Vec3 destination = this.position().add(this.getDeltaMovement());
        if (!(this.level() instanceof ServerLevel server)
            || !server.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(destination.x),
                SectionPos.blockToSectionCoord(destination.z))) { this.discard(); return; }
        HitResult hit = RocketCollisionDetector.detect(this);
        if (hit.getType() != HitResult.Type.MISS) {
            if (!this.impactHandled) {
                this.impactHandled = true;
                RocketImpactService.impact(server, this, hit.getLocation());
            }
            this.discard();
            return;
        }
        this.setPos(destination);
    }

    public @Nullable UUID ownerId() { return this.ownerId; }
    public int age() { return this.tickCount; }

    @Override protected void readAdditionalSaveData(final ValueInput input) {
        this.ownerId = input.read("owner", UUIDUtil.CODEC).orElse(null);
        this.impactHandled = input.getBooleanOr("impact_handled", false);
    }
    @Override protected void addAdditionalSaveData(final ValueOutput output) {
        output.storeNullable("owner", UUIDUtil.CODEC, this.ownerId);
        output.putBoolean("impact_handled", this.impactHandled);
    }
    @Override public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith(final Entity entity) { return false; }
}
