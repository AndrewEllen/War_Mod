package com.andye.warmod.phalanx.client;

import com.andye.warmod.phalanx.PhalanxGunStatus;
import com.andye.warmod.phalanx.network.ClientboundPhalanxStatePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

public final class ClientPhalanxStateManager {
    public static final ClientPhalanxStateManager INSTANCE = new ClientPhalanxStateManager();
    private static final double INTERPOLATION_TICKS = 2.0;
    private static final double EXPIRY_TICKS = 100.0;
    private static final double BARREL_DEGREES_PER_TICK = 72.0;
    private final Map<UUID, Entry> states = new HashMap<>();
    private ClientPhalanxStateManager() { }
    public void update(final ClientboundPhalanxStatePayload payload, final double clientTime) {
        Entry previous = states.get(payload.turretId());
        View from = previous == null ? View.from(payload) : sample(previous, clientTime);
        Target to = Target.from(payload);
        double flashUntil = payload.firing() ? clientTime + 2.5 : previous == null ? 0.0 : previous.flashUntil();
        states.put(payload.turretId(), new Entry(from, to, clientTime, from.barrelAngle(), clientTime, flashUntil));
    }
    public void markShot(final UUID turretId, final double clientTime) {
        Entry entry = states.get(turretId);
        if (entry == null) return;
        states.put(turretId, new Entry(entry.from(), entry.to(), entry.receivedTime(), entry.baseBarrelAngle(), entry.baseBarrelTime(), Math.max(entry.flashUntil(), clientTime + 2.5)));
    }
    public @Nullable View view(final UUID turretId, final double clientTime) {
        Entry entry = states.get(turretId);
        if (entry == null) return null;
        if (clientTime - entry.receivedTime() > EXPIRY_TICKS) { states.remove(turretId); return null; }
        return sample(entry, clientTime);
    }
    public void clear() { states.clear(); }
    private static View sample(final Entry entry, final double clientTime) {
        float progress = (float) Mth.clamp((clientTime - entry.receivedTime()) / INTERPOLATION_TICKS, 0.0, 1.0);
        float barrelSpeed = Mth.lerp(progress, entry.from().barrelSpeed(), entry.to().barrelSpeed());
        float barrelAngle = (float) (entry.baseBarrelAngle() + Math.max(0.0, clientTime - entry.baseBarrelTime()) * BARREL_DEGREES_PER_TICK * Math.max(0.0F, barrelSpeed)) % 360.0F;
        return new View(Mth.rotLerp(progress, entry.from().yaw(), entry.to().yaw()), Mth.lerp(progress, entry.from().pitch(), entry.to().pitch()), barrelSpeed, barrelAngle, Mth.lerp(progress, entry.from().bloom(), entry.to().bloom()), entry.to().rounds(), entry.to().status(), entry.to().enabled(), clientTime <= entry.flashUntil());
    }
    public record View(float yaw, float pitch, float barrelSpeed, float barrelAngle, float bloom, int rounds, PhalanxGunStatus status, boolean enabled, boolean muzzleFlash) {
        private static View from(final ClientboundPhalanxStatePayload payload) { Target target = Target.from(payload); return new View(target.yaw(), target.pitch(), target.barrelSpeed(), 0.0F, target.bloom(), target.rounds(), target.status(), target.enabled(), payload.firing()); }
    }
    private record Target(float yaw, float pitch, float barrelSpeed, float bloom, int rounds, PhalanxGunStatus status, boolean enabled) {
        private static Target from(final ClientboundPhalanxStatePayload payload) { PhalanxGunStatus[] statuses = PhalanxGunStatus.values(); PhalanxGunStatus status = payload.status() >= 0 && payload.status() < statuses.length ? statuses[payload.status()] : PhalanxGunStatus.IDLE; return new Target(payload.yaw(), payload.pitch(), payload.barrelSpin(), payload.bloom(), payload.rounds(), status, payload.enabled()); }
    }
    private record Entry(View from, Target to, double receivedTime, float baseBarrelAngle, double baseBarrelTime, double flashUntil) { }
}