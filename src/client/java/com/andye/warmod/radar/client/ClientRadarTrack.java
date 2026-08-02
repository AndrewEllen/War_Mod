package com.andye.warmod.radar.client;

import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.icbm.IcbmTrajectory;
import com.andye.warmod.radar.*;
import com.andye.warmod.warhead.WarheadTrajectory;
import java.util.*;
import net.minecraft.world.phys.Vec3;

public final class ClientRadarTrack {
	private static final int CARRIER_SEGMENTS = 96;
	private static final int TERMINAL_SEGMENTS = 48;
	private RadarTrackSnapshot snapshot;
	private List<Vec3> carrierRoute = List.of(), terminalRoute = List.of();
	private double carrierDuration, terminalDuration;
	public ClientRadarTrack(final RadarTrackSnapshot snapshot) { this.snapshot = snapshot; rebuildRoutes(); }
	public void update(final RadarTrackSnapshot snapshot) { this.snapshot = snapshot; rebuildRoutes(); }
	public RadarTrackSnapshot snapshot() { return snapshot; }
	public UUID id() { return snapshot.trackId(); }
	public List<Vec3> carrierRoute() { return carrierRoute; }
	public List<Vec3> terminalRoute() { return terminalRoute; }
	public double carrierDuration() { return carrierDuration; }
	public double terminalDuration() { return terminalDuration; }
	public Vec3 position(final double time) { if (snapshot.phase() == RadarTrackPhase.IMPACT) return target(); if (snapshot.terminalPlan().isPresent()) { RadarTerminalPlanSnapshot plan = snapshot.terminalPlan().get(); return WarheadTrajectory.position(plan.startPosition(), plan.targetPosition(), Math.max(0, time - plan.launchGameTime()), plan.flightTicks()); } if (snapshot.carrierPlan().isPresent()) return IcbmTrajectory.position(asPlan(snapshot.carrierPlan().get()), Math.max(0, time - snapshot.carrierPlan().get().launchGameTime())); return Vec3.ZERO; }
	public Vec3 velocity(final double time) { if (snapshot.terminalPlan().isPresent()) { RadarTerminalPlanSnapshot plan = snapshot.terminalPlan().get(); return WarheadTrajectory.velocity(plan.startPosition(), plan.targetPosition(), Math.max(0, time - plan.launchGameTime()), plan.flightTicks()); } if (snapshot.carrierPlan().isPresent()) return IcbmTrajectory.velocity(asPlan(snapshot.carrierPlan().get()), Math.max(0, time - snapshot.carrierPlan().get().launchGameTime())); return Vec3.ZERO; }
	public double carrierElapsed(final double time) { return snapshot.carrierPlan().map(plan -> Math.max(0.0, time - plan.launchGameTime())).orElse(0.0); }
	public double terminalElapsed(final double time) { return snapshot.terminalPlan().map(plan -> Math.max(0.0, time - plan.launchGameTime())).orElse(0.0); }
	public Vec3 target() { return snapshot.terminalPlan().map(RadarTerminalPlanSnapshot::targetPosition).orElseGet(() -> snapshot.carrierPlan().map(RadarCarrierPlanSnapshot::intendedTarget).orElse(Vec3.ZERO)); }
	public Vec3 launch() { return snapshot.carrierPlan().map(RadarCarrierPlanSnapshot::launchPosition).orElseGet(() -> snapshot.terminalPlan().map(RadarTerminalPlanSnapshot::startPosition).orElse(Vec3.ZERO)); }
	private void rebuildRoutes() { carrierRoute = snapshot.carrierPlan().map(this::sampleCarrierRoute).orElseGet(List::of); terminalRoute = snapshot.terminalPlan().map(this::sampleTerminalRoute).orElseGet(List::of); }
	private List<Vec3> sampleCarrierRoute(final RadarCarrierPlanSnapshot carrier) { IcbmFlightPlan plan = asPlan(carrier); carrierDuration = plan.separationTick(); ArrayList<Vec3> samples = new ArrayList<>(CARRIER_SEGMENTS + 1); for (int index = 0; index <= CARRIER_SEGMENTS; index++) samples.add(IcbmTrajectory.position(plan, carrierDuration * index / CARRIER_SEGMENTS)); return List.copyOf(samples); }
	private List<Vec3> sampleTerminalRoute(final RadarTerminalPlanSnapshot terminal) { terminalDuration = terminal.flightTicks(); ArrayList<Vec3> samples = new ArrayList<>(TERMINAL_SEGMENTS + 1); for (int index = 0; index <= TERMINAL_SEGMENTS; index++) samples.add(WarheadTrajectory.position(terminal.startPosition(), terminal.targetPosition(), terminalDuration * index / TERMINAL_SEGMENTS, terminal.flightTicks())); return List.copyOf(samples); }
	private IcbmFlightPlan asPlan(final RadarCarrierPlanSnapshot plan) { return new IcbmFlightPlan(snapshot.trackId(), snapshot.ownerPlayerId(), plan.launchPosition(), plan.burnoutPosition(), plan.separationPosition(), plan.intendedTarget(), plan.launchGameTime(), plan.ignitionTicks(), plan.boostTicks(), plan.coastTicks(), plan.visualSeed(), snapshot.payloadType()); }
}