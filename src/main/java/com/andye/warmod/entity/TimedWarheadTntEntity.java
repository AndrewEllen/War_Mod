package com.andye.warmod.entity;

import com.andye.warmod.artillery.ArtilleryPayload;
import com.andye.warmod.warhead.WarheadImpactService;
import com.andye.warmod.warhead.WarheadYield;
import com.andye.warmod.warhead.WarheadYieldRegistry;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public final class TimedWarheadTntEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_YIELD = SynchedEntityData.defineId(TimedWarheadTntEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CLUSTER = SynchedEntityData.defineId(TimedWarheadTntEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID chargeId = UUID.randomUUID(); private UUID ownerId; private WarheadYield yield = WarheadYield.CONVENTIONAL; private boolean cluster; private int fuse; private long seed;
    public TimedWarheadTntEntity(final EntityType<? extends TimedWarheadTntEntity> type, final Level level) { super(type, level); }
    public TimedWarheadTntEntity(final ServerLevel level, final UUID ownerId, final Vec3 position, final Vec3 velocity, final ArtilleryPayload payload) { this(ModEntityTypes.TIMED_WARHEAD_TNT, level); this.ownerId=ownerId; yield=payload.yield(); cluster=payload.cluster(); getEntityData().set(DATA_YIELD,yield.ordinal()); getEntityData().set(DATA_CLUSTER,cluster); fuse=yield.nuclear()?600:200; seed=java.util.concurrent.ThreadLocalRandom.current().nextLong(); setPos(position); setDeltaMovement(velocity); }
    @Override protected void defineSynchedData(final SynchedEntityData.Builder builder) { builder.define(DATA_YIELD,WarheadYield.CONVENTIONAL.ordinal()); builder.define(DATA_CLUSTER,false); }
    public WarheadYield yield() { if (!level().isClientSide()) return yield; int ordinal=getEntityData().get(DATA_YIELD); return ordinal>=0&&ordinal<WarheadYield.values().length?WarheadYield.values()[ordinal]:WarheadYield.CONVENTIONAL; }
    public boolean cluster() { return level().isClientSide()?getEntityData().get(DATA_CLUSTER):cluster; }
    @Override public void tick() { super.tick(); if (level().isClientSide()) return; Vec3 next = position().add(getDeltaMovement()); setPos(next); setDeltaMovement(getDeltaMovement().add(0.0,-0.04,0.0).scale(0.98)); if (--fuse <= 0) explode((ServerLevel)level(), next); }
    private void explode(final ServerLevel level, final Vec3 center) { ServerPlayer owner=ownerId==null?null:level.getServer().getPlayerList().getPlayer(ownerId); int count=cluster?4:1; for(int index=0;index<count;index++){ double angle=Math.PI*2*index/count; Vec3 point=count==1?center:center.add(Math.cos(angle)*4.5,0.0,Math.sin(angle)*4.5); UUID id=UUID.randomUUID(); WarheadYieldRegistry.put(level,id,yield); WarheadImpactService.impact(level,owner,id,id,point,seed+index*0x9E3779B97F4A7C15L,yield.payloadType()); } discard(); }
    @Override protected void addAdditionalSaveData(final ValueOutput out) { out.store("id",UUIDUtil.CODEC,chargeId);out.storeNullable("owner",UUIDUtil.CODEC,ownerId);out.putString("yield",yield.getSerializedName());out.putBoolean("cluster",cluster);out.putInt("fuse",fuse);out.putLong("seed",seed); }
    @Override protected void readAdditionalSaveData(final ValueInput in) { chargeId=in.read("id",UUIDUtil.CODEC).orElseGet(UUID::randomUUID);ownerId=in.read("owner",UUIDUtil.CODEC).orElse(null);yield=WarheadYield.fromSerializedName(in.getStringOr("yield","conventional")).orElse(WarheadYield.CONVENTIONAL);cluster=in.getBooleanOr("cluster",false);getEntityData().set(DATA_YIELD,yield.ordinal());getEntityData().set(DATA_CLUSTER,cluster);fuse=in.getIntOr("fuse",yield.nuclear()?600:200);seed=in.getLongOr("seed",0L); }
    @Override public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) { return false; }
}
