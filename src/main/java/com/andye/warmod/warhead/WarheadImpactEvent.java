package com.andye.warmod.warhead;

import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.phys.Vec3;

/** One authoritative epoch from which all impact-side systems derive time and identity. */
public record WarheadImpactEvent(UUID impactId, long impactSequence, long impactServerTick,
    Vec3 impactPosition, WarheadYield yield, long seed) {
    private static final AtomicLong NEXT_SEQUENCE = new AtomicLong();

    public static WarheadImpactEvent create(final UUID id, final long serverTick,
        final Vec3 position, final WarheadYield yield, final long seed) {
        return new WarheadImpactEvent(id, NEXT_SEQUENCE.incrementAndGet(), serverTick,
            position, yield, seed);
    }

    public ClientboundWarheadImpactPayload visualPayload() {
        return visualPayload(Vec3.ZERO);
    }

    public ClientboundWarheadImpactPayload visualPayload(final Vec3 ambientWind) {
        Vec3 safeWind = ambientWind == null || !ambientWind.isFinite()
            ? Vec3.ZERO : ambientWind;
        return new ClientboundWarheadImpactPayload(impactId, impactPosition.x,
            impactPosition.y, impactPosition.z, impactServerTick, seed,
            yield.payloadType(), yield.visualScale(), (float) safeWind.x,
            (float) safeWind.z, yield.effectProfile());
    }

    public UUID acousticEventId(final String layer) {
        String value = impactId + ":" + impactSequence + ":" + layer;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
