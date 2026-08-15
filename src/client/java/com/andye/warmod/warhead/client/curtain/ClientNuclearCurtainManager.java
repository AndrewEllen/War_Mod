package com.andye.warmod.warhead.client.curtain;

import com.andye.warmod.warhead.curtain.network.ClientboundNuclearCurtainPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Owns all client curtain state; it does not consume impact, shockfront, or dust state. */
public final class ClientNuclearCurtainManager {
    public static final ClientNuclearCurtainManager INSTANCE = new ClientNuclearCurtainManager();
    private static final int MAX_IMPACTS = 8;
    private static final int MAX_BANDS_PER_IMPACT = 32;
    private static final int ANCHORS_PER_BAND = 160;
    private static final int BAND_LIFETIME_TICKS = 64;
    private final Map<UUID, CurtainImpact> impacts = new LinkedHashMap<>();
    private ClientLevel activeLevel;

    private ClientNuclearCurtainManager() { }

    public synchronized void accept(final ClientboundNuclearCurtainPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (payload == null || !payload.isWellFormed() || !ensureLevel(level)) return;
        CurtainImpact impact = impacts.get(payload.impactId());
        if (impact == null) {
            while (impacts.size() >= MAX_IMPACTS) {
                Iterator<UUID> iterator = impacts.keySet().iterator();
                if (!iterator.hasNext()) break;
                iterator.next(); iterator.remove();
            }
            impact = new CurtainImpact(payload.impactId(), new Vec3(payload.centerX(),
                payload.centerY(), payload.centerZ()), payload.visualSeed(), payload.visualScale());
            impacts.put(payload.impactId(), impact);
        }
        impact.accept(level, payload);
    }

    public synchronized void tick(final Minecraft client) {
        if (!ensureLevel(client.level)) return;
        long now = client.level.getGameTime();
        Iterator<CurtainImpact> iterator = impacts.values().iterator();
        while (iterator.hasNext()) {
            CurtainImpact impact = iterator.next();
            impact.expire(now);
            if (impact.bands.isEmpty()) iterator.remove();
        }
    }

    public synchronized List<CurtainImpactView> snapshot(final ClientLevel level) {
        if (level == null || activeLevel != level) return List.of();
        return impacts.values().stream().map(CurtainImpact::snapshot).toList();
    }

    public synchronized void clear() { impacts.clear(); activeLevel = null; }

    private boolean ensureLevel(final ClientLevel level) {
        if (level == null) { clear(); return false; }
        if (activeLevel != level) { impacts.clear(); activeLevel = level; }
        return true;
    }

    public record CurtainImpactView(UUID id, Vec3 center, float visualScale,
        long completionGameTime,
        List<CurtainBandView> bands) { }
    public record CurtainBandView(long spawnGameTime, long seed, float visualScale,
        List<CurtainAnchor> anchors) { }
    public record CurtainAnchor(Vec3 position, float radialOffset, float height,
        float width, long seed) { }

    private static final class CurtainImpact {
        private final UUID id;
        private final Vec3 center;
        private final long visualSeed;
        private final float visualScale;
        private final ArrayDeque<CurtainBand> bands = new ArrayDeque<>();
        private long lastServerTick = Long.MIN_VALUE;
        private float lastRadius;
		private long completionGameTime = Long.MIN_VALUE;

        private CurtainImpact(final UUID id, final Vec3 center, final long visualSeed,
            final float visualScale) {
            this.id = id; this.center = center; this.visualSeed = visualSeed;
            this.visualScale = visualScale;
        }

        private void accept(final ClientLevel level, final ClientboundNuclearCurtainPayload payload) {
            if (payload.serverGameTime() < lastServerTick
                || payload.currentRadius() + 0.01F < lastRadius) return;
            float from = Math.max(lastRadius, payload.previousRadius());
            float to = Math.max(from, payload.currentRadius());
			if (payload.finalBand()) {
				completionGameTime = payload.serverGameTime();
				lastServerTick = payload.serverGameTime();
				lastRadius = to;
				return;
			}
            if (to - from < 0.35F && !payload.finalBand()) return;
            CurtainBand band = createBand(level, center, visualSeed, visualScale, from, to,
                payload.serverGameTime());
            if (band != null) {
                bands.addLast(band);
                while (bands.size() > MAX_BANDS_PER_IMPACT) bands.removeFirst();
            }
            lastServerTick = payload.serverGameTime();
            lastRadius = to;
        }

        private void expire(final long now) {
			bands.removeIf(band -> now - band.spawnGameTime > BAND_LIFETIME_TICKS);
			if (completionGameTime == Long.MIN_VALUE
				&& lastServerTick != Long.MIN_VALUE && now - lastServerTick > 600L) {
				bands.clear();
			}
        }

        private CurtainImpactView snapshot() {
            List<CurtainBandView> views = new ArrayList<>(bands.size());
            for (CurtainBand band : bands) views.add(new CurtainBandView(band.spawnGameTime,
                band.seed, visualScale, List.copyOf(band.anchors)));
            return new CurtainImpactView(id, center, visualScale, completionGameTime,
				List.copyOf(views));
        }
    }

    private static CurtainBand createBand(final ClientLevel level, final Vec3 center,
        final long impactSeed, final float scale, final float from, final float to,
        final long gameTime) {
        if (level == null) return null;
        long bandSeed = mix(impactSeed ^ Double.doubleToLongBits(to) ^ gameTime);
        double phase = unit(bandSeed) * Mth.TWO_PI;
        double span = Math.max(1.5, to - from);
        List<CurtainAnchor> anchors = new ArrayList<>(ANCHORS_PER_BAND);
        for (int index = 0; index < ANCHORS_PER_BAND; index++) {
            long seed = mix(bandSeed ^ index * 0x9E3779B97F4A7C15L);
            double angle = phase + Mth.TWO_PI * index / ANCHORS_PER_BAND
                + signed(seed, 0) * 0.016;
            double radius = from + span * unit(seed, 1) + unit(seed, 2) * 4.0;
            Vec3 position = NuclearCurtainWorldSampler.sample(level, center, radius, angle);
            if (position == null) position = NuclearCurtainWorldSampler.fallback(
                level, center, radius, angle);
            if (position == null) continue;
			double spacing = Mth.TWO_PI * Math.max(8.0, radius) / ANCHORS_PER_BAND;
			float height = (float) ((2.4 + scale * 0.85) * (0.78 + unit(seed, 3) * 0.46));
			float width = (float) Math.max(4.5 + scale * 0.80,
				spacing * (0.86 + unit(seed, 4) * 0.28));
            anchors.add(new CurtainAnchor(position, (float) (radius - to), height, width, seed));
        }
        return anchors.isEmpty() ? null : new CurtainBand(gameTime, bandSeed, anchors);
    }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
    private static double unit(long value) { return (mix(value) >>> 11) * 0x1.0p-53; }
    private static double unit(long value, int lane) { return unit(value + lane * 0x9E3779B97F4A7C15L); }
    private static double signed(long value, int lane) { return unit(value, lane) * 2.0 - 1.0; }
    private record CurtainBand(long spawnGameTime, long seed, List<CurtainAnchor> anchors) { }
}
