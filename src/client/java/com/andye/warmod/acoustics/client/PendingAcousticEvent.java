package com.andye.warmod.acoustics.client;

import com.andye.warmod.acoustics.network.ClientboundAcousticEventPayload;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public record PendingAcousticEvent(ClientboundAcousticEventPayload payload) {
	public PendingAcousticEvent {
		Objects.requireNonNull(payload, "payload");
	}

	public Vec3 sourcePosition() {
		return new Vec3(payload.sourceX(), payload.sourceY(), payload.sourceZ());
	}
}
