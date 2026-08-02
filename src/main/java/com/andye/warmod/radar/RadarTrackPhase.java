package com.andye.warmod.radar;

public enum RadarTrackPhase {
	IGNITION("Stage 1 - Ignition"), BOOST("Stage 1 - Boost"), MIDCOURSE("Stage 2 - Midcourse"),
	PAYLOAD_DELIVERY("Stage 3 - Payload Delivery"), IMPACT("Detonation");
	private final String label;
	RadarTrackPhase(final String label) { this.label = label; }
	public String label() { return this.label; }
}
