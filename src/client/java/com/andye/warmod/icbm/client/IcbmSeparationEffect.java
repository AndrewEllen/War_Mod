package com.andye.warmod.icbm.client;

import com.andye.warmod.icbm.network.ClientboundIcbmSeparationPayload;
import java.util.SplittableRandom;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;

public final class IcbmSeparationEffect {private IcbmSeparationEffect(){}public static void spawn(final ClientLevel level,final ClientboundIcbmSeparationPayload p){SplittableRandom r=new SplittableRandom(p.visualSeed()^0x5345504152415445L);spawn(level,p,r,ParticleTypes.CLOUD,r.nextInt(8,17));spawn(level,p,r,ParticleTypes.POOF,r.nextInt(6,13));spawn(level,p,r,ParticleTypes.LARGE_SMOKE,r.nextInt(4,9));level.addAlwaysVisibleParticle(ColorParticleOption.create(ParticleTypes.FLASH,0xFFFFE8B0),true,p.separationPosition().x,p.separationPosition().y,p.separationPosition().z,0,0,0);}private static void spawn(final ClientLevel level,final ClientboundIcbmSeparationPayload p,final SplittableRandom r,final net.minecraft.core.particles.ParticleOptions type,final int count){for(int i=0;i<count;i++)level.addAlwaysVisibleParticle(type,true,p.separationPosition().x+r.nextDouble(-1,1),p.separationPosition().y+r.nextDouble(-1,1),p.separationPosition().z+r.nextDouble(-1,1),r.nextDouble(-.12,.12),r.nextDouble(-.04,.16),r.nextDouble(-.12,.12));}}
