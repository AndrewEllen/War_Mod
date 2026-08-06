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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Client-only analytical rigid fragments; no server entities or per-tick network updates. */
public final class ClientDebrisBatchManager {
	public static final ClientDebrisBatchManager INSTANCE = new ClientDebrisBatchManager();
	private static final int MAX_BATCHES = 64;
	private static final int MAX_RENDERED_PARTS = 4_096;
	private static final double DRAG = 0.985;
	private static final double GRAVITY = -0.04;

	private final Map<UUID, Batch> batches = new LinkedHashMap<>();
	private ClientLevel activeLevel;

	private ClientDebrisBatchManager() { }

	public synchronized void accept(final ClientboundWarheadDebrisPayload payload) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || payload == null || !payload.isWellFormed()) return;
		ensureLevel(level);
		while (batches.size() >= MAX_BATCHES) {
			Iterator<UUID> iterator = batches.keySet().iterator();
			if (!iterator.hasNext()) break;
			iterator.next();
			iterator.remove();
		}
		List<Piece> pieces = new ArrayList<>(payload.entries().size());
		for (ClientboundWarheadDebrisPayload.Entry entry : payload.entries()) {
			List<Part> parts = new ArrayList<>(entry.parts().size());
			for (ClientboundWarheadDebrisPayload.Part encoded : entry.parts()) {
				BlockState state = Block.BLOCK_STATE_REGISTRY.byId(encoded.blockStateId());
				if (state == null || state.isAir()) state = Blocks.STONE.defaultBlockState();
				parts.add(new Part(state, new Vec3(encoded.offsetX(), encoded.offsetY(), encoded.offsetZ())));
			}
			pieces.add(new Piece(
				new Vec3(entry.offsetX(), entry.offsetY(), entry.offsetZ()),
				new Vec3(entry.velocityX(), entry.velocityY(), entry.velocityZ()),
				new Vec3(entry.spinX(), entry.spinY(), entry.spinZ()),
				entry.scale(), entry.lifetime(), List.copyOf(parts)
			));
		}
		batches.put(payload.impactId(), new Batch(
			new Vec3(payload.originX(), payload.originY(), payload.originZ()),
			payload.spawnGameTime(), List.copyOf(pieces)
		));
	}

	public synchronized void tick(final ClientLevel level, final long gameTime) {
		if (level == null) { clear(); return; }
		ensureLevel(level);
		batches.entrySet().removeIf(entry -> entry.getValue().expired(gameTime));
	}

	public synchronized List<RenderSample> snapshot(final ClientLevel level, final long gameTime,
		final double partialTick, final Vec3 viewer, final double maximumDistance) {
		if (level == null || level != activeLevel || viewer == null) return List.of();
		double maximumDistanceSquared = maximumDistance * maximumDistance;
		List<RenderSample> result = new ArrayList<>();
		for (Map.Entry<UUID, Batch> batchEntry : batches.entrySet()) {
			Batch batch = batchEntry.getValue();
			double age = Math.max(0.0, gameTime - batch.spawnGameTime + Math.max(0.0, Math.min(1.0, partialTick)));
			for (int pieceIndex = 0; pieceIndex < batch.pieces.size() && result.size() < MAX_RENDERED_PARTS; pieceIndex++) {
				Piece piece = batch.pieces.get(pieceIndex);
				if (age >= piece.lifetime) continue;
				Vec3 root = batch.origin.add(displacement(piece.velocity, age)).add(piece.offset);
				if (!root.isFinite() || viewer.distanceToSqr(root) > maximumDistanceSquared) continue;
				boolean onGround = false;
				Vec3 currentVelocity = velocityAt(piece.velocity, age);
				if (age > 1.0) {
					Vec3 previous = batch.origin.add(displacement(piece.velocity, Math.max(0.0, age - 1.0))).add(piece.offset);
					Vec3 delta = root.subtract(previous);
					int steps = Math.max(1, Math.min(8, (int) Math.ceil(delta.length() / 0.65)));
					for (int step = 1; step <= steps; step++) {
						Vec3 samplePosition = previous.lerp(root, step / (double) steps);
						BlockPos blockPosition = BlockPos.containing(samplePosition);
						if (!level.hasChunkAt(blockPosition)) continue;
						BlockState collisionState = level.getBlockState(blockPosition);
						if (!collisionState.isAir() && !collisionState.getCollisionShape(level, blockPosition).isEmpty()) {
							root = previous.lerp(root, Math.max(0, step - 1) / (double) steps);
							currentVelocity = Vec3.ZERO;
							onGround = true;
							break;
						}
					}
				}
				TerrainSurfaceCache.SurfaceSample surface = !onGround && age > 8.0 && currentVelocity.y < 0.0
					? TerrainSurfaceCache.INSTANCE.sample(level, root.x, root.z) : null;
				if (surface != null && root.y <= surface.position().y + 0.05) {
					double settle = Math.max(0.0, age - piece.lifetime * 0.42);
					double bounce = Math.abs(Math.sin(settle * 0.55 + pieceIndex)) * Math.exp(-settle * 0.10) * 0.55;
					root = new Vec3(root.x, surface.position().y + bounce, root.z);
					onGround = true;
				}
				double fadeStart = piece.lifetime * 0.86;
				float fade = age <= fadeStart ? 1.0F
					: (float) Math.max(0.0, 1.0 - (age - fadeStart) / Math.max(1.0, piece.lifetime - fadeStart));
				Quaternionf rotation = new Quaternionf().rotationXYZ(
					(float) piece.spin.x * (float) age,
					(float) piece.spin.y * (float) age,
					(float) piece.spin.z * (float) age
				);
				for (int partIndex = 0; partIndex < piece.parts.size() && result.size() < MAX_RENDERED_PARTS; partIndex++) {
					Part part = piece.parts.get(partIndex);
					Vector3f local = new Vector3f((float) part.offset.x, (float) part.offset.y, (float) part.offset.z)
						.mul(piece.scale).rotate(rotation);
					Vec3 position = root.add(local.x, local.y, local.z);
					result.add(new RenderSample(batchEntry.getKey(), pieceIndex, partIndex, part.state,
						position, currentVelocity, piece.spin, (float) age, piece.scale * fade, onGround));
				}
			}
		}
		return List.copyOf(result);
	}

	public synchronized void clear() { batches.clear(); activeLevel = null; }

	private void ensureLevel(final ClientLevel level) {
		if (activeLevel != level) { batches.clear(); activeLevel = level; }
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

	private record Part(BlockState state, Vec3 offset) { }
	private record Piece(Vec3 offset, Vec3 velocity, Vec3 spin, float scale, int lifetime, List<Part> parts) { }
	private record Batch(Vec3 origin, long spawnGameTime, List<Piece> pieces) {
		private boolean expired(final long gameTime) {
			long maximumLifetime = 0L;
			for (Piece piece : pieces) maximumLifetime = Math.max(maximumLifetime, piece.lifetime);
			return gameTime - spawnGameTime >= maximumLifetime + 5L;
		}
	}

	public record RenderSample(UUID batchId, int pieceIndex, int partIndex, BlockState state,
		Vec3 position, Vec3 velocity, Vec3 spin, float age, float scale, boolean onGround) { }
}
