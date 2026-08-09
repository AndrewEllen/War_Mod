package com.andye.warmod.silo;

import com.andye.warmod.antiair.AntiAirMissileVariant;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.warhead.StrategicMissilePayload;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadYield;
import java.util.Optional;
import net.minecraft.world.item.Item;

public enum SiloMissileType {
    CONVENTIONAL_ICBM(SiloMissileRole.STRATEGIC_STRIKE, WarheadPayloadType.CONVENTIONAL, WarheadDeliveryMode.SINGLE, WarheadYield.CONVENTIONAL, null, false),
    CONVENTIONAL_CLUSTER_ICBM(SiloMissileRole.STRATEGIC_STRIKE, WarheadPayloadType.CONVENTIONAL, WarheadDeliveryMode.CLUSTER_FOUR, WarheadYield.CONVENTIONAL, null, false),
    NUCLEAR_ICBM(SiloMissileRole.STRATEGIC_STRIKE, WarheadPayloadType.NUCLEAR, WarheadDeliveryMode.SINGLE, WarheadYield.STRATEGIC_NUCLEAR, null, false),
    NUCLEAR_CLUSTER_ICBM(SiloMissileRole.STRATEGIC_STRIKE, WarheadPayloadType.NUCLEAR, WarheadDeliveryMode.CLUSTER_FOUR, WarheadYield.STRATEGIC_NUCLEAR, null, false),
    HIGH_EXPLOSIVE_MISSILE(WarheadYield.HIGH_EXPLOSIVE, false), HIGH_EXPLOSIVE_CLUSTER_MISSILE(WarheadYield.HIGH_EXPLOSIVE, true),
    HIGH_CAPACITY_HE_MISSILE(WarheadYield.HIGH_CAPACITY_HE, false), HIGH_CAPACITY_HE_CLUSTER_MISSILE(WarheadYield.HIGH_CAPACITY_HE, true),
    CONVENTIONAL_MISSILE(WarheadYield.CONVENTIONAL, false), CONVENTIONAL_CLUSTER_MISSILE(WarheadYield.CONVENTIONAL, true),
    HEAVY_CONVENTIONAL_MISSILE(WarheadYield.HEAVY_CONVENTIONAL, false), HEAVY_CONVENTIONAL_CLUSTER_MISSILE(WarheadYield.HEAVY_CONVENTIONAL, true),
    TACTICAL_NUCLEAR_MISSILE(WarheadYield.TACTICAL_NUCLEAR, false), TACTICAL_NUCLEAR_CLUSTER_MISSILE(WarheadYield.TACTICAL_NUCLEAR, true),
    STRATEGIC_NUCLEAR_MISSILE(WarheadYield.STRATEGIC_NUCLEAR, false), STRATEGIC_NUCLEAR_CLUSTER_MISSILE(WarheadYield.STRATEGIC_NUCLEAR, true),
    HEAVY_NUCLEAR_MISSILE(WarheadYield.HEAVY_NUCLEAR, false), HEAVY_NUCLEAR_CLUSTER_MISSILE(WarheadYield.HEAVY_NUCLEAR, true),
    ANTI_AIR_MK_I(SiloMissileRole.INTERCEPTOR, null, null, null, AntiAirMissileVariant.MK_I, false),
    ANTI_AIR_MK_II(SiloMissileRole.INTERCEPTOR, null, null, null, AntiAirMissileVariant.MK_II, false);

    private final SiloMissileRole role; private final WarheadPayloadType payload; private final WarheadDeliveryMode delivery; private final WarheadYield yield; private final AntiAirMissileVariant antiAir; private final boolean generated;
    SiloMissileType(final WarheadYield yield, final boolean cluster) { this(SiloMissileRole.STRATEGIC_STRIKE, yield.payloadType(), cluster ? WarheadDeliveryMode.CLUSTER_FOUR : WarheadDeliveryMode.SINGLE, yield, null, true); }
    SiloMissileType(final SiloMissileRole role, final WarheadPayloadType payload, final WarheadDeliveryMode delivery, final WarheadYield yield, final AntiAirMissileVariant antiAir, final boolean generated) { this.role=role;this.payload=payload;this.delivery=delivery;this.yield=yield;this.antiAir=antiAir;this.generated=generated; }
    public String serializedName(){return name().toLowerCase(java.util.Locale.ROOT);}
    public SiloMissileRole role(){return role;} public Optional<WarheadPayloadType> payloadType(){return Optional.ofNullable(payload);} public WarheadDeliveryMode deliveryMode(){return delivery==null?WarheadDeliveryMode.SINGLE:delivery;} public Optional<WarheadYield> yield(){return Optional.ofNullable(yield);} public StrategicMissilePayload strategicPayload(){return new StrategicMissilePayload(payload,deliveryMode());} public Optional<AntiAirMissileVariant> antiAirVariant(){return Optional.ofNullable(antiAir);} public boolean requiresStoredTarget(){return role==SiloMissileRole.STRATEGIC_STRIKE;} public boolean automaticTargetAcquisition(){return role==SiloMissileRole.INTERCEPTOR;}
    public Item item(){ return switch(this) { case CONVENTIONAL_ICBM -> ModItems.CONVENTIONAL_ICBM; case CONVENTIONAL_CLUSTER_ICBM -> ModItems.CONVENTIONAL_CLUSTER_ICBM; case NUCLEAR_ICBM -> ModItems.NUCLEAR_ICBM; case NUCLEAR_CLUSTER_ICBM -> ModItems.NUCLEAR_CLUSTER_ICBM; case ANTI_AIR_MK_I -> ModItems.ANTI_AIR_MISSILE_MK1; case ANTI_AIR_MK_II -> ModItems.ANTI_AIR_MISSILE_MK2; default -> ModItems.yieldMissile(yield, deliveryMode()==WarheadDeliveryMode.CLUSTER_FOUR); }; }
}