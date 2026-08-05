package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.network.ClientboundWarheadDebrisPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Client-only analytical debris; no server entities and no per-piece network updates. */
public final class ClientDebrisBatchManager {
	public static final ClientDebrisBatchManager INSTANCE = new ClientDebrisBatchManager();
	private static final int MAX_BATCHES = 64;
	private static final double DRAG = 0.985;
	private static final double GRAVITY = -0.04;

	private final Map<UUID, Batch> batches = new LinkedHashMap<>();
	private ClientLevel activeLevel;

	private ClientDebrisBatchManager() {
	}

	public synchronized void accept(final ClientboundWarheadDebrisPayload payload) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || payload == null || !payload.isWellFormed()) return;
		this.ensureLevel(level);
		while (this.batches.size() >= MAX_BATCHES) {
			Iterator<UUID> iterator = this.batches.keySet().iterator();
			if (!iterator.hasNext()) break;
			iterator.next();
			iterator.remove();
		}
		List<Piece> pieces = new ArrayList<>(payload.entries().size());
		for (ClientboundWarheadDebrisPayload.Entry entry : payload.entries()) {
			BlockState state = Block.BLOCK_STATE_REGISTRY.byId(entry.blockStateId());
			if (state == null || state.isAir()) state = Blocks.STONE.defaultBlockState();
			pieces.add(new Piece(
				state,
				new Vec3(entry.offsetX(), entry.offsetY(), entry.offsetZ()),
				new Vec3(entry.velocityX(), entry.velocityY(), entry.velocityZ()),
				new Vec3(entry.spinX(), entry.spinY(), entry.spinZ()),
				entry.scale(),
				entry.lifetime()
			));
		}
		this.batches.put(payload.impactId(), new Batch(
			new Vec3(payload.originX(), payload.originY(), payload.originZ()),
			payload.spawnGameTime(),
			List.copyOf(pieces)
		));
	}

	public synchronized void tick(final ClientLevel level, final long gameTime) {
		if (level == null) {
			this.clear();
			return;
		}
		this.ensureLevel(level);
		this.batches.entrySet().removeIf(entry -> entry.getValue().expired(gameTime));
	}

	public synchronized List<RenderSample> snapshot(final ClientLevel level, final long gameTime,
		final double partialTick, final Vec3 viewer, final double maximumDistance) {
		if (level == null || level != this.activeLevel || viewer == null) return List.of();
		double maximumDistanceSquared = maximumDistance * maximumDistance;
		List<RenderSample> result = new ArrayList<>();
		for (Map.Entry<UUID, Batch> batchEntry : this.batches.entrySet()) {
			Batch batch = batchEntry.getValue();
			double age = Math.max(0.0, gameTime - batch.spawnGameTime + Math.max(0.0, Math.min(1.0, partialTick)));
			for (int index = 0; index < batch.pieces.size(); index++) {
				Piece piece = batch.pieces.get(index);
				if (age >= piece.lifetime) continue;
				Vec3 position = batch.origin.add(displacement(piece.velocity, age)).add(piece.offset);
				if (!position.isFinite() || viewer.distanceToSqr(position) > maximumDistanceSquared) continue;
				boolean onGround = false;
				Vec3 currentVelocity = velocityAt(piece.velocity, age);
				TerrainSurfaceCache.SurfaceSample surface = piece.scale >= 0.55F && age > 8.0 && currentVelocity.y < 0.0
					? TerrainSurfaceCache.INSTANCE.sample(level, position.x, position.z) : null;
				if (surface != null && position.y <= surface.position().y + 0.05) {
					double settle = Math.max(0.0, age - piece.lifetime * 0.42);
					double bounce = Math.abs(Math.sin(settle * 0.55 + index)) * Math.exp(-settle * 0.10) * 0.8;
					position = new Vec3(position.x, surface.position().y + bounce, position.z);
					onGround = true;
				}
				double fadeStart = piece.lifetime * 0.82;
				float fade = age <= fadeStart ? 1.0F
					: (float) Math.max(0.0, 1.0 - (age - fadeStart) / Math.max(1.0, piece.lifetime - fadeStart));
				result.add(new RenderSample(
					batchEntry.getKey(), index, piece.state, position, currentVelocity, piece.spin,
					(float) age, piece.scale * fade, onGround
				));
			}
		}
		return List.copyOf(result);
	}

	public synchronized void clear() {
		this.batches.clear();
		this.activeLevel = null;
	}

	private void ensureLevel(final ClientLevel level) {
		if (this.activeLevel != level) {
			this.batches.clear();
			this.activeLevel = level;
		}
	}

	private static Vec3 displacement(final Vec3 initialVelocity, final double age) {
		double f = Math.pow(DRAG, age);
		double sum = (1.0 - f) / (1.0 - DRAG);
		double x = initialVelocity.x * sum;
		double z = initialVelocity.z * sum;
		double gravitySeries = DRAG * GRAVITY / (1.0 - DRAG) * (age - sum) + GRAVITY * age;
		double y = initialVelocity.y * sum + gravitySeries;
		return new Vec3(x, y, z);
	}

	private static Vec3 velocityAt(final Vec3 initialVelocity, final double age) {
		double f = Math.pow(DRAG, age);
		double vertical = f * initialVelocity.y + DRAG * GRAVITY * (1.0 - f) / (1.0 - DRAG);
		return new Vec3(initialVelocity.x * f, vertical, initialVelocity.z * f);
	}

	private record Piece(BlockState state, Vec3 offset, Vec3 velocity, Vec3 spin, float scale, int lifetime) {
	}

	private record Batch(Vec3 origin, long spawnGameTime, List<Piece> pieces) {
		private boolean expired(final long gameTime) {
			long maximumLifetime = 0L;
			for (Piece piece : this.pieces) maximumLifetime = Math.max(maximumLifetime, piece.lifetime);
			return gameTime - this.spawnGameTime >= maximumLifetime + 5L;
		}
	}

	public record RenderSample(UUID batchId, int pieceIndex, BlockState state, Vec3 position, Vec3 velocity,
		Vec3 spin, float age, float scale, boolean onGround) {
	}
}
