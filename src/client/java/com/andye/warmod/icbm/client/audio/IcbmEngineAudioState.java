package com.andye.warmod.icbm.client.audio;

import com.andye.warmod.acoustics.model.AcousticDistanceProfile;
import com.andye.warmod.icbm.IcbmFlightPlan;
import net.minecraft.world.phys.Vec3;

public final class IcbmEngineAudioState {
	final IcbmFlightPlan flightPlan;
	IcbmEngineLoopSound ignitionSound;
	IcbmEngineLoopSound sustainSound;
	IcbmEngineLoopSound shutdownSound;
	AcousticDistanceProfile selectedProfile;
	Vec3 apparentPosition;
	double apparentDistance = Double.POSITIVE_INFINITY;
	float currentGain;
	boolean started;
	boolean shutdownStarted;
	boolean cancelled;

	IcbmEngineAudioState(final IcbmFlightPlan flightPlan) {
		this.flightPlan = flightPlan;
		this.apparentPosition = flightPlan.launchPosition();
	}
}
