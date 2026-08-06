package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.network.ClientboundWarheadDebrisPayload;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Client-side rigid terrain fragments with swept collision and long smoke trails. */
public final class ClientDebrisBatchManager {
    public static final ClientDebrisBatchManager INSTANCE = new ClientDebrisBatchManager();
    private static final int MAX_BATCHES = 64;
    private static final int MAX_RENDERED_PARTS = 12_288;
    private static final int TRAIL_SAMPLES = 64;
    private static final int MAX_CATCH_UP_STEPS = 6;
    private static final int SETTLED_LIFETIME_TICKS = 40;
    private static final int MINIMUM_AIRBORNE_TICKS = 9;
    private static final double GRAVITY = -0.052;
    private static final double AIR_DRAG = 0.992;
    private static final byte CACHE_MISS = -1;
    private static final byte CACHE_EMPTY = 0;
    private static final byte CACHE_SOLID = 1;

    private final Map<UUID, Batch> batches = new LinkedHashMap<>();
    private final Long2ByteOpenHashMap terrainCollisionCache =
        new Long2ByteOpenHashMap(16_384);
    private ClientLevel activeLevel;

    private ClientDebrisBatchManager() {
        terrainCollisionCache.defaultReturnValue(CACHE_MISS);
    }

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

            double mass = Math.max(1.0, parts.size()) * Math.max(0.35,
                entry.scale() * entry.scale() * entry.scale());
            double massDamping = 1.0 / Math.pow(Math.max(1.0, mass / 7.0), 0.34);
            Vec3 encodedVelocity = new Vec3(entry.velocityX(), entry.velocityY(),
                entry.velocityZ());
            double horizontalLength = Math.sqrt(encodedVelocity.x * encodedVelocity.x
                + encodedVelocity.z * encodedVelocity.z);
            double maximumHorizontal = 1.02 + 2.55 / Math.sqrt(Math.max(1.0, mass));
            double horizontalScale = horizontalLength > maximumHorizontal
                ? maximumHorizontal / horizontalLength : 1.0;
            double maximumVertical = 0.72 + 1.55 / Math.sqrt(Math.max(1.0, mass));
            Vec3 velocity = new Vec3(
                encodedVelocity.x * massDamping * horizontalScale * 1.14,
                Mth.clamp(encodedVelocity.y * massDamping * 1.12, 0.18, maximumVertical),
                encodedVelocity.z * massDamping * horizontalScale * 1.14);
            Vec3 angularVelocity = new Vec3(entry.spinX(), entry.spinY(), entry.spinZ())
                .scale(Math.min(0.72, 2.2 / Math.sqrt(mass)));
            float visualScale = Mth.clamp(entry.scale()
                * (1.36F + (float) Math.sqrt(parts.size()) * 0.11F), 1.30F, 2.85F);
            int lifetime = Math.max(160, entry.lifetime());
            int collisionGrace = MINIMUM_AIRBORNE_TICKS
                + Math.min(8, Math.round(visualScale * 2.0F));
            /* Collision uses physical block extent, not the enlarged display scale. */
            double collisionExtent = Math.max(0.34,
                maximumOffset * Mth.clamp(entry.scale(), 0.46F, 1.10F));
            pieces.add(new Piece(
                new Vec3(entry.offsetX(), entry.offsetY(), entry.offsetZ()),
                velocity, angularVelocity, visualScale, lifetime, List.copyOf(parts),
                mass, collisionExtent, collisionGrace));
        }
        batches.put(payload.impactId(), new Batch(
            new Vec3(payload.originX(), payload.originY(), payload.originZ()),
            payload.spawnGameTime(), payload.spawnGameTime() - 1L,
            List.copyOf(pieces)));
    }

    public synchronized void tick(final ClientLevel level, final long gameTime) {
        if (level == null) {
            clear();
            return;
        }
        ensureLevel(level);
        batches.entrySet().removeIf(entry -> entry.getValue().expired(gameTime));
    }

    public synchronized List<RenderSample> snapshot(final ClientLevel level,
        final long gameTime, final double partialTick, final Vec3 viewer,
        final Quaternionf cameraOrientation, final double maximumDistance) {
        if (level == null || viewer == null || cameraOrientation == null) return List.of();
        ensureLevel(level);
        double maximumDistanceSquared = maximumDistance * maximumDistance;
        float partial = (float) Mth.clamp(partialTick, 0.0, 1.0);
        List<RenderSample> result = new ArrayList<>();

        for (Map.Entry<UUID, Batch> batchEntry : batches.entrySet()) {
            Batch batch = batchEntry.getValue();
            if (!visibleBatch(batch.origin, viewer, cameraOrientation, maximumDistance)) {
                batch.skipTo(gameTime);
                continue;
            }
            batch.simulateTo(level, gameTime, terrainCollisionCache);
            for (int pieceIndex = 0; pieceIndex < batch.pieces.size()
                && result.size() < MAX_RENDERED_PARTS; pieceIndex++) {
                Piece piece = batch.pieces.get(pieceIndex);
                if (piece.terminal) continue;
                Vec3 root = piece.previousPosition.lerp(piece.position, partial);
                Vec3 worldRoot = batch.origin.add(piece.offset).add(root);
                if (!worldRoot.isFinite()
                    || viewer.distanceToSqr(worldRoot) > maximumDistanceSquared) continue;
                Vec3 interpolatedRotation = piece.previousRotation.lerp(piece.rotation, partial);
                Quaternionf quaternion = new Quaternionf().rotationXYZ(
                    (float) interpolatedRotation.x, (float) interpolatedRotation.y,
                    (float) interpolatedRotation.z);
                float renderedAge = Math.max(0.001F, piece.age + partial);
                Vec3 effectiveSpin = piece.settled ? Vec3.ZERO
                    : interpolatedRotation.scale(1.0 / renderedAge);
                List<Vec3> trail = piece.trail(batch.origin.add(piece.offset));
                for (int partIndex = 0; partIndex < piece.parts.size()
                    && result.size() < MAX_RENDERED_PARTS; partIndex++) {
                    Part part = piece.parts.get(partIndex);
                    Vector3f local = new Vector3f((float) part.offset.x,
                        (float) part.offset.y, (float) part.offset.z)
                        .mul(piece.scale).rotate(quaternion);
                    Vec3 position = worldRoot.add(local.x, local.y, local.z);
                    result.add(new RenderSample(batchEntry.getKey(), pieceIndex,
                        partIndex, part.state, position, piece.velocity, effectiveSpin,
                        renderedAge, piece.scale, piece.settled, trail));
                }
            }
        }
        batches.entrySet().removeIf(entry -> entry.getValue().expired(gameTime));
        return List.copyOf(result);
    }

    public synchronized int activeFragmentCount() {
        int count = 0;
        for (Batch batch : batches.values()) {
            for (Piece piece : batch.pieces) if (!piece.terminal) count++;
        }
        return count;
    }

    public synchronized void clear() {
        batches.clear();
        terrainCollisionCache.clear();
        activeLevel = null;
    }

    private void ensureLevel(final ClientLevel level) {
        if (activeLevel != level) {
            batches.clear();
            terrainCollisionCache.clear();
            activeLevel = level;
        }
    }

    private static boolean visibleBatch(final Vec3 origin, final Vec3 viewer,
        final Quaternionf cameraOrientation, final double maximumDistance) {
        Vec3 delta = origin.subtract(viewer);
        double distance = delta.length();
        if (!Double.isFinite(distance) || distance > maximumDistance + 192.0) return false;
        if (distance < 256.0) return true;
        Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F).rotate(cameraOrientation);
        if (forward.lengthSquared() < 1.0E-6F) return true;
        forward.normalize();
        double facing = (delta.x * forward.x + delta.y * forward.y
            + delta.z * forward.z) / Math.max(1.0E-6, distance);
        return facing > -0.34;
    }

    private static boolean collides(final ClientLevel level, final Vec3 position,
        final double extent, final Long2ByteOpenHashMap collisionCache) {
        if (solid(level, position, collisionCache)) return true;
        double sample = Math.max(0.20, Math.min(1.05, extent * 0.68));
        return solid(level, position.add(sample, 0.0, 0.0), collisionCache)
            || solid(level, position.add(-sample, 0.0, 0.0), collisionCache)
            || solid(level, position.add(0.0, sample, 0.0), collisionCache)
            || solid(level, position.add(0.0, -sample, 0.0), collisionCache)
            || solid(level, position.add(0.0, 0.0, sample), collisionCache)
            || solid(level, position.add(0.0, 0.0, -sample), collisionCache);
    }

    private static boolean solid(final ClientLevel level, final Vec3 position,
        final Long2ByteOpenHashMap collisionCache) {
        BlockPos blockPosition = BlockPos.containing(position);
        long packedPosition = blockPosition.asLong();
        byte cached = collisionCache.get(packedPosition);
        if (cached != CACHE_MISS) return cached == CACHE_SOLID;
        boolean solid = false;
        if (level.hasChunkAt(blockPosition)) {
            BlockState state = level.getBlockState(blockPosition);
            solid = !state.isAir() && !state.getCollisionShape(level, blockPosition).isEmpty();
        }
        collisionCache.put(packedPosition, solid ? CACHE_SOLID : CACHE_EMPTY);
        return solid;
    }

    private record Part(BlockState state, Vec3 offset) { }

    private static final class Piece {
        private final Vec3 offset;
        private final float scale;
        private final int lifetime;
        private final List<Part> parts;
        private final double mass;
        private final double collisionExtent;
        private final int collisionGraceTicks;
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
        private int settledAge;
        private int trailHead;
        private int trailCount;
        private int trailRevision;
        private int cachedTrailRevision = -1;
        private List<Vec3> cachedTrail = List.of();
        private boolean settled;
        private boolean terminal;

        private Piece(final Vec3 offset, final Vec3 velocity,
            final Vec3 angularVelocity, final float scale, final int lifetime,
            final List<Part> parts, final double mass,
            final double collisionExtent, final int collisionGraceTicks) {
            this.offset = offset;
            this.velocity = velocity;
            this.angularVelocity = angularVelocity;
            this.scale = scale;
            this.lifetime = lifetime;
            this.parts = parts;
            this.mass = mass;
            this.collisionExtent = collisionExtent;
            this.collisionGraceTicks = collisionGraceTicks;
            recordTrail(position);
        }

        private void tick(final ClientLevel level, final Vec3 worldOrigin,
            final Long2ByteOpenHashMap collisionCache) {
            if (terminal) return;
            if (settled) {
                previousPosition = position;
                previousRotation = rotation;
                age++;
                settledAge++;
                if (settledAge >= SETTLED_LIFETIME_TICKS) terminal = true;
                return;
            }
            if (age >= lifetime) {
                terminal = true;
                return;
            }

            previousPosition = position;
            previousRotation = rotation;
            Vec3 accelerated = new Vec3(velocity.x * AIR_DRAG,
                velocity.y * AIR_DRAG + GRAVITY, velocity.z * AIR_DRAG);
            Vec3 proposed = position.add(accelerated);
            boolean checkCollision = age >= collisionGraceTicks;
            if (checkCollision) {
                Vec3 delta = proposed.subtract(previousPosition);
                int steps = Math.max(1, Math.min(22,
                    (int) Math.ceil(delta.length()
                        / Math.max(0.16, collisionExtent * 0.24))));
                Vec3 safe = previousPosition;
                for (int step = 1; step <= steps; step++) {
                    double fraction = step / (double) steps;
                    Vec3 candidate = previousPosition.lerp(proposed, fraction);
                    if (collides(level, worldOrigin.add(candidate),
                        collisionExtent, collisionCache)) {
                        position = safe;
                        previousPosition = safe;
                        velocity = Vec3.ZERO;
                        angularVelocity = Vec3.ZERO;
                        settled = true;
                        settledAge = 0;
                        recordTrail(position);
                        age++;
                        return;
                    }
                    safe = candidate;
                }
            }
            position = proposed;
            velocity = accelerated;
            angularVelocity = angularVelocity.scale(
                Math.max(0.965, 0.990 - mass * 0.00008));
            rotation = rotation.add(angularVelocity);
            recordTrail(position);
            age++;
        }

        private void recordTrail(final Vec3 point) {
            trailX[trailHead] = point.x;
            trailY[trailHead] = point.y;
            trailZ[trailHead] = point.z;
            trailHead = (trailHead + 1) % TRAIL_SAMPLES;
            trailCount = Math.min(TRAIL_SAMPLES, trailCount + 1);
            trailRevision++;
        }

        private List<Vec3> trail(final Vec3 origin) {
            if (trailCount < 2) return List.of();
            if (cachedTrailRevision == trailRevision) return cachedTrail;
            ArrayList<Vec3> points = new ArrayList<>(trailCount);
            int first = Math.floorMod(trailHead - trailCount, TRAIL_SAMPLES);
            for (int index = 0; index < trailCount; index++) {
                int slot = (first + index) % TRAIL_SAMPLES;
                points.add(origin.add(trailX[slot], trailY[slot], trailZ[slot]));
            }
            cachedTrail = List.copyOf(points);
            cachedTrailRevision = trailRevision;
            return cachedTrail;
        }
    }

    private static final class Batch {
        private final Vec3 origin;
        private final long spawnGameTime;
        private long lastSimulatedGameTime;
        private final List<Piece> pieces;

        private Batch(final Vec3 origin, final long spawnGameTime,
            final long lastSimulatedGameTime, final List<Piece> pieces) {
            this.origin = origin;
            this.spawnGameTime = spawnGameTime;
            this.lastSimulatedGameTime = lastSimulatedGameTime;
            this.pieces = pieces;
        }

        private void simulateTo(final ClientLevel level, final long gameTime,
            final Long2ByteOpenHashMap collisionCache) {
            long target = Math.max(spawnGameTime, gameTime);
            if (target < lastSimulatedGameTime) return;
            long steps = Math.min(MAX_CATCH_UP_STEPS, target - lastSimulatedGameTime);
            for (long step = 0; step < steps; step++) {
                collisionCache.clear();
                for (Piece piece : pieces) piece.tick(level, origin.add(piece.offset), collisionCache);
                lastSimulatedGameTime++;
            }
        }

        private void skipTo(final long gameTime) {
            lastSimulatedGameTime = Math.max(lastSimulatedGameTime, gameTime);
        }

        private boolean expired(final long gameTime) {
            boolean anyActive = false;
            long maximumLifetime = 0L;
            for (Piece piece : pieces) {
                maximumLifetime = Math.max(maximumLifetime,
                    piece.lifetime + SETTLED_LIFETIME_TICKS);
                if (!piece.terminal) anyActive = true;
            }
            return !anyActive || gameTime - spawnGameTime >= maximumLifetime + 80L;
        }
    }

    public record RenderSample(UUID batchId, int pieceIndex, int partIndex,
        BlockState state, Vec3 position, Vec3 velocity, Vec3 spin, float age,
        float scale, boolean onGround, List<Vec3> trailPositions) { }
}
