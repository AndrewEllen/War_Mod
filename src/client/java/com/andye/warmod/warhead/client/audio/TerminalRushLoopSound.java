package com.andye.warmod.warhead.client.audio;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class TerminalRushLoopSound extends AbstractTickableSoundInstance {
	private final int fadeInTicks;
	private int age;
	private int fadeRemaining=-1,fadeTotal=1;
	private float targetVolume;
	public TerminalRushLoopSound(final SoundEvent event,final boolean looping,final int fadeInTicks,final Vec3 position){super(event,SoundSource.BLOCKS,RandomSource.create());this.looping=looping;this.delay=0;this.attenuation=SoundInstance.Attenuation.LINEAR;this.relative=false;this.fadeInTicks=Math.max(1,fadeInTicks);this.volume=.001F;this.pitch=1.0F;update(position,.001F);}
	public void update(final Vec3 position,final float volume){this.x=position.x;this.y=position.y;this.z=position.z;this.targetVolume=Math.max(0,volume);}
	public void fadeOut(final int ticks){if(fadeRemaining>=0)return;fadeTotal=Math.max(1,ticks);fadeRemaining=fadeTotal;}
	@Override public boolean canStartSilent(){return true;}
	@Override public void tick(){age++;float in=Math.min(1,age/(float)fadeInTicks),out=1;if(fadeRemaining>=0){out=fadeRemaining/(float)fadeTotal;fadeRemaining--;if(fadeRemaining<0){volume=0;stop();return;}}volume=Math.max(.001F,targetVolume*in*out);if(!looping&&age>=40)stop();}
}
