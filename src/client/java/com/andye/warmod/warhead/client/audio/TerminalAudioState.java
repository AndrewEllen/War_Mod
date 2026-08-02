package com.andye.warmod.warhead.client.audio;
import com.andye.warmod.acoustics.model.AcousticDistanceProfile;
import com.andye.warmod.warhead.client.WarheadVisualState;
public final class TerminalAudioState {
	final WarheadVisualState visualState;TerminalRushLoopSound loop,tail;AcousticDistanceProfile profile;
	float gain;boolean started,ended,cancelled;
	TerminalAudioState(final WarheadVisualState visualState){this.visualState=visualState;}
}
