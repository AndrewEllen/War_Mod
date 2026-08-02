package com.andye.warmod.warhead.client.audio;

import com.andye.warmod.acoustics.AcousticSoundRegistry;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.acoustics.ModSoundEvents;
import com.andye.warmod.acoustics.model.AcousticDistanceProfile;
import com.andye.warmod.acoustics.model.AcousticDistanceSound;
import com.andye.warmod.acoustics.model.AcousticSoundDefinition;
import com.andye.warmod.acoustics.physics.AcousticAttenuation;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.client.WarheadVisualState;
import com.andye.warmod.warhead.network.ClientboundWarheadLaunchPayload;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

public final class ClientTerminalAudioManager {
	public static final ClientTerminalAudioManager INSTANCE=new ClientTerminalAudioManager();
	private static final int MAXIMUM_STATES=48;private final Map<UUID,TerminalAudioState> states=new LinkedHashMap<>();
	private ClientLevel activeLevel;private static boolean registered;private ClientTerminalAudioManager(){}
	public static void register(){if(registered)return;ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::tick);ClientPlayConnectionEvents.DISCONNECT.register((l,c)->INSTANCE.clear(c));ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((c,l)->INSTANCE.clear(c));registered=true;}
	public synchronized void acceptLaunch(final ClientboundWarheadLaunchPayload payload){if(!payload.isWellFormed())return;while(states.size()>=MAXIMUM_STATES){Map.Entry<UUID,TerminalAudioState> quietest=states.entrySet().stream().min(Comparator.comparingDouble(e->e.getValue().gain)).orElse(null);if(quietest==null)break;cancel(quietest.getValue());states.remove(quietest.getKey());}states.put(payload.warheadId(),new TerminalAudioState(WarheadVisualState.fromPayload(payload)));}
	public synchronized void acceptImpact(final UUID id){TerminalAudioState state=states.get(id);if(state!=null)state.ended=true;}
	public synchronized void acceptRemoval(final UUID id){TerminalAudioState state=states.get(id);if(state!=null){state.cancelled=true;cancel(state);}}
	private synchronized void tick(final Minecraft client){if(client.level==null||client.player==null){clear(client);return;}if(activeLevel!=null&&activeLevel!=client.level)clear(client);activeLevel=client.level;double now=client.level.getGameTime();Vec3 listener=client.player.getEyePosition();Iterator<Map.Entry<UUID,TerminalAudioState>> iterator=states.entrySet().iterator();while(iterator.hasNext()){TerminalAudioState state=iterator.next().getValue();TerminalAudioTrajectorySampler.Sample sample=TerminalAudioTrajectorySampler.sample(state.visualState,now,listener);if(!state.cancelled&&sample.rushActive()){if(!state.started)start(client,state,sample);state.gain=gain(sample.apparentDistance(),.42F);if(state.loop!=null)state.loop.update(sample.position(),state.gain);}else if(state.started&&state.tail==null){state.loop.fadeOut(state.cancelled?10:14);if(!state.cancelled){state.tail=new TerminalRushLoopSound(tail(state.profile),false,2,sample.position());state.tail.update(sample.position(),state.gain*.68F);client.getSoundManager().play(state.tail);}}if(state.tail!=null&&!state.tail.isStopped())state.tail.update(sample.position(),state.gain*.68F);if((state.cancelled||sample.elapsedTicks()>state.visualState.flightTicks()+40)&&(state.loop==null||state.loop.isStopped())&&(state.tail==null||state.tail.isStopped()))iterator.remove();else if(!state.started&&sample.elapsedTicks()>state.visualState.flightTicks()+160)iterator.remove();}}
	private static void start(final Minecraft client,final TerminalAudioState state,final TerminalAudioTrajectorySampler.Sample sample){state.profile=profile(sample.apparentDistance());state.gain=gain(sample.apparentDistance(),.42F);state.loop=new TerminalRushLoopSound(loop(state.profile),true,9,sample.position());state.loop.update(sample.position(),state.gain);client.getSoundManager().play(state.loop);state.started=true;}
	private static void cancel(final TerminalAudioState state){if(state.loop!=null)state.loop.fadeOut(10);if(state.tail!=null)state.tail.fadeOut(8);}
	private synchronized void clear(final Minecraft client){for(TerminalAudioState state:states.values()){if(state.loop!=null)client.getSoundManager().stop(state.loop);if(state.tail!=null)client.getSoundManager().stop(state.tail);}states.clear();activeLevel=null;}
	private static float gain(final double distance,final float volume){AcousticSoundDefinition definition=AcousticSoundRegistry.get(AcousticSounds.TERMINAL_DESCENT_RUSH_ID).orElse(null);AcousticDistanceSound selected=definition==null?null:definition.soundForDistance(distance).orElse(null);return selected==null?0:(float)AcousticAttenuation.gain(distance,selected,volume);}
	private static AcousticDistanceProfile profile(final double d){return d<120?AcousticDistanceProfile.NEAR:d<320?AcousticDistanceProfile.MEDIUM:d<750?AcousticDistanceProfile.FAR:AcousticDistanceProfile.EXTREME;}
	private static SoundEvent loop(final AcousticDistanceProfile p){return switch(p){case NEAR->ModSoundEvents.TERMINAL_RUSH_LOOP_NEAR;case MEDIUM->ModSoundEvents.TERMINAL_RUSH_LOOP_MEDIUM;case FAR->ModSoundEvents.TERMINAL_RUSH_LOOP_FAR;case EXTREME->ModSoundEvents.TERMINAL_RUSH_LOOP_EXTREME;};}
	private static SoundEvent tail(final AcousticDistanceProfile p){return switch(p){case NEAR->ModSoundEvents.TERMINAL_RUSH_TAIL_NEAR;case MEDIUM->ModSoundEvents.TERMINAL_RUSH_TAIL_MEDIUM;case FAR->ModSoundEvents.TERMINAL_RUSH_TAIL_FAR;case EXTREME->ModSoundEvents.TERMINAL_RUSH_TAIL_EXTREME;};}
}
