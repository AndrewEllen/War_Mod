package com.andye.warmod.fire.client;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.FireSurfaceAnchor;
import com.andye.warmod.fire.network.ClientboundFireStatePayload;
import java.util.HashSet;
	import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class ClientFireVisualManager {
    public static final ClientFireVisualManager INSTANCE = new ClientFireVisualManager();
    private static final int EXPIRY_TICKS = 28;
	private static final Direction[] HORIZONTAL_TANGENTS = {Direction.NORTH, Direction.SOUTH,
		Direction.EAST, Direction.WEST};
	private static final Direction[] NORTH_SOUTH_TANGENTS = {Direction.UP, Direction.DOWN,
		Direction.EAST, Direction.WEST};
	private static final Direction[] EAST_WEST_TANGENTS = {Direction.UP, Direction.DOWN,
		Direction.NORTH, Direction.SOUTH};
    private final Map<Long, VisualPatch> patches = new LinkedHashMap<>();
	private final Map<Long, VisualEmber> embers = new LinkedHashMap<>();
    private ClientLevel activeLevel;

    private ClientFireVisualManager() { }

    public synchronized void accept(final ClientboundFireStatePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!payload.isWellFormed() || !ensureCurrentLevel(level)) return;
        long receivedAt = level.getGameTime();
        boolean patchUpdate = payload.complete() || !payload.entries().isEmpty();
        HashSet<Long> received = new HashSet<>(payload.entries().size());
        for (ClientboundFireStatePayload.Entry entry : payload.entries()) {
            received.add(entry.id());
            VisualPatch previous = patches.get(entry.id());
            Vec3 incomingWind = new Vec3(entry.windX(), entry.windY(), entry.windZ());
            Vec3 wind = previous == null ? incomingWind : previous.wind().lerp(incomingWind, 0.34);
            float heat = lerp(previous == null ? entry.heat() : previous.heat(), entry.heat(), 0.44F);
            float coverage = lerp(previous == null ? entry.coverage() : previous.coverage(),
                entry.coverage(), 0.38F);
            float smoke = lerp(previous == null ? entry.smoke() : previous.smoke(),
                entry.smoke(), 0.40F);
            Direction face = Direction.values()[Byte.toUnsignedInt(entry.face())];
            FireSurfaceAnchor anchor = new FireSurfaceAnchor(BlockPos.of(entry.packedHost()), face,
                entry.localX(), entry.localY(), entry.localZ());
            patches.put(entry.id(), new VisualPatch(entry.id(), anchor, entry.intensity(),
                heat, coverage, smoke, entry.phase(), entry.seed(), entry.ignitionGameTime(),
                wind, previous == null ? 0.0F : previous.clumpStrength(), receivedAt));
        }
        if (payload.complete()) patches.keySet().removeIf(id -> !received.contains(id));
		if (patchUpdate) recomputeClumps();
		HashSet<Long> receivedEmbers = new HashSet<>(payload.embers().size());
		for (ClientboundFireStatePayload.EmberEntry entry : payload.embers()) {
			receivedEmbers.add(entry.id());
			VisualEmber previous = embers.get(entry.id());
			Vec3 incoming = new Vec3(entry.x(), entry.y(), entry.z());
			Vec3 position = incoming;
			Vec3 velocity = new Vec3(entry.velocityX(), entry.velocityY(), entry.velocityZ());
			Vec3 wind = new Vec3(entry.windX(), entry.windY(), entry.windZ());
			embers.put(entry.id(), new VisualEmber(entry.id(), position, velocity, wind,
				entry.intensity(), entry.seed(), entry.startGameTime(), entry.lifetime(),
                payload.serverGameTime(), receivedAt));
		}
		if (payload.emberComplete()) embers.keySet().removeIf(id -> !receivedEmbers.contains(id));
    }

    public synchronized void tick(final Minecraft client) {
        if (!ensureCurrentLevel(client.level)) return;
        long now = client.level.getGameTime();
        Iterator<VisualPatch> iterator = patches.values().iterator();
        while (iterator.hasNext())
            if (now - iterator.next().lastSeenClientTick() > EXPIRY_TICKS) iterator.remove();
		Iterator<VisualEmber> emberIterator = embers.values().iterator();
		while (emberIterator.hasNext()) {
			VisualEmber ember = emberIterator.next();
			if (now - ember.lastSeenClientTick() > 24
				|| now - ember.startGameTime() > ember.lifetime() + 4L) emberIterator.remove();
		}
    }

    public synchronized List<VisualPatch> snapshot(final ClientLevel level) {
        return level == null || activeLevel != level ? List.of() : List.copyOf(patches.values());
    }

	public synchronized List<VisualEmber> emberSnapshot(final ClientLevel level) {
		return level == null || activeLevel != level ? List.of() : List.copyOf(embers.values());
	}

    public synchronized void clear() { patches.clear(); embers.clear(); activeLevel = null; }

    private boolean ensureCurrentLevel(final ClientLevel level) {
        if (level == null) { clear(); return false; }
		if (activeLevel != level) { patches.clear(); embers.clear(); activeLevel = level; }
        return true;
    }

    private static float lerp(final float from, final float to, final float amount) {
        return from + (to - from) * amount;
    }

	private void recomputeClumps() {
		List<VisualPatch> visible = List.copyOf(patches.values());
		Map<FaceHostKey, List<VisualPatch>> buckets = new HashMap<>();
		for (VisualPatch patch : visible) buckets.computeIfAbsent(faceHostKey(patch),
			ignored -> new java.util.ArrayList<>()).add(patch);
		for (VisualPatch patch : visible) {
			float energy = 0.0F;
			int members = 0;
			List<VisualPatch> candidates = new java.util.ArrayList<>(
				buckets.getOrDefault(faceHostKey(patch), List.of()));
			for (Direction direction : tangentDirections(patch.anchor().face()))
				candidates.addAll(buckets.getOrDefault(new FaceHostKey(
					patch.anchor().host().relative(direction).asLong(),
					(byte) patch.anchor().face().ordinal()), List.of()));
			for (VisualPatch candidate : candidates) {
				if (candidate.phase() == FirePhase.SMOLDERING
					|| candidate.phase() == FirePhase.DECAYING) continue;
				energy += candidate.heat() * candidate.coverage();
				members++;
			}
			float strength = members < 2 ? 0.0F
				: Math.min(1.75F, Math.max(0.0F, (energy - 0.45F) / 3.25F));
			patches.put(patch.id(), new VisualPatch(patch.id(), patch.anchor(), patch.intensity(),
				patch.heat(), patch.coverage(), patch.smoke(), patch.phase(), patch.seed(),
				patch.ignitionGameTime(), patch.wind(), strength, patch.lastSeenClientTick()));
		}
	}

	private static FaceHostKey faceHostKey(final VisualPatch patch) {
		return new FaceHostKey(patch.anchor().host().asLong(),
			(byte) patch.anchor().face().ordinal());
	}

	private static Direction[] tangentDirections(final Direction face) {
		return switch (face) {
			case UP, DOWN -> HORIZONTAL_TANGENTS;
			case NORTH, SOUTH -> NORTH_SOUTH_TANGENTS;
			case EAST, WEST -> EAST_WEST_TANGENTS;
		};
	}

    public record VisualPatch(long id, FireSurfaceAnchor anchor, float intensity,
        float heat, float coverage, float smoke, FirePhase phase, long seed,
        long ignitionGameTime, Vec3 wind, float clumpStrength, long lastSeenClientTick) { }
	public record VisualEmber(long id, Vec3 position, Vec3 velocity, Vec3 wind,
        float intensity, long seed, long startGameTime, int lifetime,
        long serverSampleGameTime, long lastSeenClientTick) { }
	private record FaceHostKey(long packedHost, byte face) { }
}
