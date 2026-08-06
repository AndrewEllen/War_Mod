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

/**
 * Client-side rigid fragment approximation for connected captured terrain.
 * Fragments are simulated once per client game tick, use swept multi-point
 * collision, retain stable settled transforms, and keep a short real path for
 * dust trails.
 */
public final class ClientDebrisBatchManager {
	public static final ClientDebrisBatchManager INSTANCE = new ClientDebrisBatchManager();
	private static final int MAX_BATCHES = 64;
	private static final int MAX_RENDERED_PARTS = 8_192;
	private static final int TRAIL_SAMPLES = 28;
	private static final double GRAVITY = -0.045;
	private static final double AIR_DRAG = 0.992;
	private static final double FLOOR_FRICTION = 0.58;
	private static final double SETTLE_SPEED_SQUARED = 0.010;

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
			double maximumOffset = 0.5;
			for (ClientboundWarheadDebrisPayload.Part encoded : entry.parts()) {
				BlockState state = Block.BLOCK_STATE_REGISTRY.byId(encoded.blockStateId());
				if (state == null || state.isAir()) state = Blocks.STONE.defaultBlockState();
				Vec3 offset = new Vec3(encoded.offsetX(), encoded.offsetY(), encoded.offsetZ());
				maximumOffset = Math.max(maximumOffset, offset.length() + 0.87);
				parts.add(new Part(state, offset));
			}
			if (parts.isEmpty()) continue;
			double mass = Math.max(1.0, parts.size()) * Math.max(0.35, entry.scale() * entry.scale() * entry.scale());
			Vec3 initialVelocity = new Vec3(entry.velocityX(), entry.velocityY(), entry.velocityZ());
			double massDamping = 1.0 / Math.sqrt(Math.max(1.0, mass / 6.0));
			Vec3 velocity = new Vec3(initialVelocity.x * massDamping,
				Math.max(initialVelocity.y * massDamping, 0.28 + Math.min(0.75, maximumOffset * 0.06)),
				initialVelocity.z * massDamping);
			Vec3 angularVelocity = new Vec3(entry.spinX(), entry.spinY(), entry.spinZ())
				.scale(Math.min(1.0, 2.8 / Math.sqrt(mass)));
			pieces.add(new Piece(new Vec3(entry.offsetX(), entry.offsetY(), entry.offsetZ()), velocity,
				angularVelocity, entry.scale(), entry.lifetime(), List.copyOf(parts), mass,
				Math.max(0.38, maximumOffset * entry.scale())));
		}
		batches.put(payload.impactId(), new Batch(new Vec3(payload.originX(), payload.originY(), payload.originZ()),
			payload.spawnGameTime(), payload.spawnGameTime() - 1L, List.copyOf(pieces)));
	}

	public synchronized void tick(final ClientLevel level, final long gameTime) {
		if (level == null) { clear(); return; }
		ensureLevel(level);
		for (Batch batch : batches.values()) batch.simulateTo(level, gameTime);
		batches.entrySet().removeIf(entry -> entry.getValue().expired(gameTime));
	}

	public synchronized List<RenderSample> snapshot(final ClientLevel level, final long gameTime,
		final double partialTick, final Vec3 viewer, final double maximumDistance) {
		if (level == null || viewer == null) return List.of();
		ensureLevel(level);
		for (Batch batch : batches.values()) batch.simulateTo(level, gameTime);
		batches.entrySet().removeIf(entry -> entry.getValue().expired(gameTime));
		double maximumDistanceSquared = maximumDistance * maximumDistance;
		float partial = (float) Math.max(0.0, Math.min(1.0, partialTick));
		List<RenderSample> result = new ArrayList<>();
		for (Map.Entry<UUID, Batch> batchEntry : batches.entrySet()) {
			Batch batch = batchEntry.getValue();
			for (int pieceIndex = 0; pieceIndex < batch.pieces.size() && result.size() < MAX_RENDERED_PARTS; pieceIndex++) {
				Piece piece = batch.pieces.get(pieceIndex);
				if (piece.age >= piece.lifetime) continue;
				Vec3 root = piece.previousPosition.lerp(piece.position, partial);
				Vec3 worldRoot = batch.origin.add(piece.offset).add(root);
				if (!worldRoot.isFinite() || viewer.distanceToSqr(worldRoot) > maximumDistanceSquared) continue;
				Vec3 interpolatedRotation = piece.previousRotation.lerp(piece.rotation, partial);
				Quaternionf quaternion = new Quaternionf().rotationXYZ((float) interpolatedRotation.x,
					(float) interpolatedRotation.y, (float) interpolatedRotation.z);
				List<Vec3> trail = piece.trail(batch.origin.add(piece.offset));
				for (int partIndex = 0; partIndex < piece.parts.size() && result.size() < MAX_RENDERED_PARTS; partIndex++) {
					Part part = piece.parts.get(partIndex);
					Vector3f local = new Vector3f((float) part.offset.x, (float) part.offset.y, (float) part.offset.z)
						.mul(piece.scale).rotate(quaternion);
					Vec3 position = worldRoot.add(local.x, local.y, local.z);
					result.add(new RenderSample(batchEntry.getKey(), pieceIndex, partIndex, part.state, position,
						piece.velocity, interpolatedRotation, piece.age + partial, piece.scale, piece.settled, trail));
				}
			}
		}
		return List.copyOf(result);
	}

	public synchronized int activeFragmentCount() {
		int count = 0;
		for (Batch batch : batches.values()) {
			for (Piece piece : batch.pieces) if (piece.age < piece.lifetime) count++;
		}
		return count;
	}

	public synchronized void clear() { batches.clear(); activeLevel = null; }

	private void ensureLevel(final ClientLevel level) {
		if (activeLevel != level) { batches.clear(); activeLevel = level; }
	}

	private static boolean collides(final ClientLevel level, final Vec3 position, final double extent) {
		if (solid(level, position)) return true;
		double sample = Math.max(0.22, Math.min(1.35, extent * 0.72));
		return solid(level, position.add(sample, 0.0, 0.0))
			|| solid(level, position.add(-sample, 0.0, 0.0))
			|| solid(level, position.add(0.0, sample, 0.0))
			|| solid(level, position.add(0.0, -sample, 0.0))
			|| solid(level, position.add(0.0, 0.0, sample))
			|| solid(level, position.add(0.0, 0.0, -sample));
	}

	private static boolean solid(final ClientLevel level, final Vec3 position) {
		BlockPos blockPosition = BlockPos.containing(position);
		if (!level.hasChunkAt(blockPosition)) return false;
		BlockState state = level.getBlockState(blockPosition);
		return !state.isAir() && !state.getCollisionShape(level, blockPosition).isEmpty();
	}

	private record Part(BlockState state, Vec3 offset) { }

	private static final class Piece {
		private final Vec3 offset;
		private final float scale;
		private final int lifetime;
		private final List<Part> parts;
		private final double mass;
		private final double collisionExtent;
		private final double[] trailX = new double[TRAIL_SAMPLES];
		private final double[] trailY = new double[TRAIL_SAMPLES];
		private final double[] trailZ = new double[TRAIL_SAMPLES];
		private Vec3 position = Vec3.ZERO;
		private Vec3 previousPosition = Vec3.ZERO;
		private Vec3 velocity;
		private Vec3 rotation = Vec3.ZERO;
		private Vec3 previousRotation = Vec3.ZERO;
		private Vec3 angularVelocity;
		private int age;
		private int trailHead;
		private int trailCount;
		private int contactTicks;
		private boolean settled;

		private Piece(final Vec3 offset, final Vec3 velocity, final Vec3 angularVelocity, final float scale,
			final int lifetime, final List<Part> parts, final double mass, final double collisionExtent) {
			this.offset = offset;
			this.velocity = velocity;
			this.angularVelocity = angularVelocity;
			this.scale = scale;
			this.lifetime = lifetime;
			this.parts = parts;
			this.mass = mass;
			this.collisionExtent = collisionExtent;
			recordTrail(position);
		}

		private void tick(final ClientLevel level, final Vec3 worldOrigin) {
			if (age >= lifetime) return;
			previousPosition = position;
			previousRotation = rotation;
			if (settled) {
				velocity = Vec3.ZERO;
				angularVelocity = Vec3.ZERO;
				age++;
				return;
			}
			Vec3 accelerated = new Vec3(velocity.x * AIR_DRAG,
				velocity.y * AIR_DRAG + GRAVITY, velocity.z * AIR_DRAG);
			Vec3 proposed = position.add(accelerated);
			Vec3 worldStart = worldOrigin.add(previousPosition);
			Vec3 worldEnd = worldOrigin.add(proposed);
			Vec3 delta = worldEnd.subtract(worldStart);
			int steps = Math.max(1, Math.min(18, (int) Math.ceil(delta.length() / Math.max(0.22, collisionExtent * 0.30))));
			Vec3 safe = previousPosition;
			boolean hit = false;
			for (int step = 1; step <= steps; step++) {
				double fraction = step / (double) steps;
				Vec3 candidate = previousPosition.lerp(proposed, fraction);
				if (collides(level, worldOrigin.add(candidate), collisionExtent)) { hit = true; break; }
				safe = candidate;
			}
			if (hit) {
				position = safe;
				double impactSpeed = accelerated.length();
				velocity = new Vec3(accelerated.x * FLOOR_FRICTION, Math.max(0.0, -accelerated.y * 0.06),
					accelerated.z * FLOOR_FRICTION);
				angularVelocity = angularVelocity.scale(impactSpeed > 0.55 ? 0.08 : 0.18);
				contactTicks++;
				if (velocity.lengthSqr() < SETTLE_SPEED_SQUARED || contactTicks >= 3) settled = true;
			} else {
				position = proposed;
				velocity = accelerated;
				contactTicks = 0;
			}
			if (!settled) {
				double angularDamping = hit ? 0.10 : Math.max(0.972, 0.992 - mass * 0.00008);
				angularVelocity = angularVelocity.scale(angularDamping);
				rotation = rotation.add(angularVelocity);
			}
			recordTrail(position);
			age++;
		}

		private void recordTrail(final Vec3 point) {
			trailX[trailHead] = point.x;
			trailY[trailHead] = point.y;
			trailZ[trailHead] = point.z;
			trailHead = (trailHead + 1) % TRAIL_SAMPLES;
			trailCount = Math.min(TRAIL_SAMPLES, trailCount + 1);
		}

		private List<Vec3> trail(final Vec3 origin) {
			if (trailCount < 2) return List.of();
			ArrayList<Vec3> points = new ArrayList<>(trailCount);
			int first = Math.floorMod(trailHead - trailCount, TRAIL_SAMPLES);
			for (int index = 0; index < trailCount; index++) {
				int slot = (first + index) % TRAIL_SAMPLES;
				points.add(origin.add(trailX[slot], trailY[slot], trailZ[slot]));
			}
			return List.copyOf(points);
		}
	}

	private static final class Batch {
		private final Vec3 origin;
		private final long spawnGameTime;
		private long lastSimulatedGameTime;
		private final List<Piece> pieces;

		private Batch(final Vec3 origin, final long spawnGameTime, final long lastSimulatedGameTime,
			final List<Piece> pieces) {
			this.origin = origin;
			this.spawnGameTime = spawnGameTime;
			this.lastSimulatedGameTime = lastSimulatedGameTime;
			this.pieces = pieces;
		}

		private void simulateTo(final ClientLevel level, final long gameTime) {
			long target = Math.max(spawnGameTime, gameTime);
			if (target < lastSimulatedGameTime) return;
			long steps = Math.min(400L, target - lastSimulatedGameTime);
			for (long step = 0; step < steps; step++) {
				for (Piece piece : pieces) piece.tick(level, origin.add(piece.offset));
				lastSimulatedGameTime++;
			}
		}

		private boolean expired(final long gameTime) {
			long maximumLifetime = 0L;
			for (Piece piece : pieces) maximumLifetime = Math.max(maximumLifetime, piece.lifetime);
			return gameTime - spawnGameTime >= maximumLifetime + 20L;
		}
	}

	public record RenderSample(UUID batchId, int pieceIndex, int partIndex, BlockState state,
		Vec3 position, Vec3 velocity, Vec3 spin, float age, float scale, boolean onGround,
		List<Vec3> trailPositions) { }
}
