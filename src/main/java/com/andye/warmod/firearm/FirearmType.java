package com.andye.warmod.firearm;

import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.item.ModItems;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** Shared weapon tuning for authoritative bullets and client tracer prediction. */
public enum FirearmType {
    PISTOL(14.0, 0.024, 0.0016, 72, 6, 0.0035, 12.0F, 7.0F, 4.5F),
    ASSAULT_RIFLE(19.0, 0.020, 0.0014, 88, 2, 0.0070, 15.0F, 8.5F, 5.5F),
    SNIPER_RIFLE(26.0, 0.015, 0.0018, 112, 20, 0.00035, 40.0F, 16.0F, 9.0F);

    private final double muzzleSpeed;
    private final double gravity;
    private final double windInfluence;
    private final int maximumAge;
    private final int intervalTicks;
    private final double spread;
    private final float headDamage;
    private final float bodyDamage;
    private final float legDamage;

    FirearmType(final double muzzleSpeed, final double gravity,
        final double windInfluence, final int maximumAge, final int intervalTicks,
        final double spread, final float headDamage, final float bodyDamage,
        final float legDamage) {
        this.muzzleSpeed = muzzleSpeed;
        this.gravity = gravity;
        this.windInfluence = windInfluence;
        this.maximumAge = maximumAge;
        this.intervalTicks = intervalTicks;
        this.spread = spread;
        this.headDamage = headDamage;
        this.bodyDamage = bodyDamage;
        this.legDamage = legDamage;
    }

    public double muzzleSpeed() { return muzzleSpeed; }
    public double gravity() { return gravity; }
    public double windInfluence() { return windInfluence; }
    public int maximumAge() { return maximumAge; }
    public int intervalTicks() { return intervalTicks; }
    public double spread() { return spread; }
    public float headDamage() { return headDamage; }
    public float bodyDamage() { return bodyDamage; }
    public float legDamage() { return legDamage; }
    public boolean automatic() { return this == ASSAULT_RIFLE; }
    public boolean scoped() { return this == SNIPER_RIFLE; }

    public Item ammunition() {
        return switch (this) {
            case PISTOL -> ModItems.PISTOL_AMMO;
            case ASSAULT_RIFLE -> ModItems.RIFLE_AMMO;
            case SNIPER_RIFLE -> ModItems.SNIPER_AMMO;
        };
    }

    public Identifier acousticDefinition() {
        return switch (this) {
            case PISTOL -> AcousticSounds.PISTOL_FIRE_ID;
            case ASSAULT_RIFLE -> AcousticSounds.RIFLE_FIRE_ID;
            case SNIPER_RIFLE -> AcousticSounds.SNIPER_FIRE_ID;
        };
    }
}
