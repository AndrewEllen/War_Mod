package com.andye.warmod.particle.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/** Deterministic reference checks for the GPU shader allocation protocol. */
final class GpuParticleAllocationProtocolTest {
    @Test
    void initialDeadCountEqualsCapacity() {
        AllocationModel model = new AllocationModel(32, 3);

        assertEquals(32, model.deadCount());
        assertEquals(0, model.nextAliveCount());
    }

    @Test
    void spawningNParticlesDecreasesDeadCountByN() {
        AllocationModel model = new AllocationModel(32, 3);

        model.spawn(11, 0, 1);

        assertEquals(21, model.deadCount());
        assertEquals(11, model.nextAliveCount());
    }

    @Test
    void spawnedParticlesAppearExactlyOnceInNextAliveList() {
        AllocationModel model = new AllocationModel(32, 3);

        model.spawn(20, 0, 1);

        assertEquals(20, new HashSet<>(model.nextAliveIds()).size());
        assertEquals(20, model.nextAliveIds().size());
    }

    @Test
    void updatingLiveParticlesPreservesEachExactlyOnce() {
        AllocationModel model = populatedModel(24, 13);

        model.update(Set.of());

        assertEquals(model.currentAliveIds().size(), model.nextAliveIds().size());
        assertEquals(new HashSet<>(model.currentAliveIds()),
            new HashSet<>(model.nextAliveIds()));
    }

    @Test
    void expiredParticlesReturnToDeadListExactlyOnce() {
        AllocationModel model = populatedModel(24, 13);
        Set<Integer> expired = Set.copyOf(model.currentAliveIds().subList(0, 5));
        int deadBefore = model.deadCount();

        model.update(expired);

        assertEquals(deadBefore + expired.size(), model.deadCount());
        assertTrue(model.nextAliveIds().stream().noneMatch(expired::contains));
        assertEquals(model.deadCount(), new HashSet<>(model.deadIds()).size());
    }

    @Test
    void rejectedSpawnsDoNotCorruptCountersOrLists() {
        AllocationModel model = new AllocationModel(4, 2);

        model.spawn(9, 1, 1);

        assertEquals(4, model.nextAliveCount());
        assertEquals(0, model.deadCount());
        assertEquals(5, model.rejectedSpawns());
        assertEquals(4, new HashSet<>(model.nextAliveIds()).size());
    }

    @Test
    void aliveCountNeverExceedsCapacity() {
        AllocationModel model = new AllocationModel(8, 2);

        model.spawn(100, 0, 1);
        model.advanceFrame();
        model.update(Set.of());
        model.spawn(100, 1, 2);

        assertTrue(model.nextAliveCount() <= model.capacity());
        assertEquals(8, new HashSet<>(model.nextAliveIds()).size());
    }

    @Test
    void visibleIdsNeverExceedCapacity() {
        AllocationModel model = populatedModel(16, 16);

        model.cull(id -> true);

        assertTrue(model.visibleIds().stream()
            .allMatch(id -> id >= 0 && id < model.capacity()));
        assertTrue(model.visibleIds().size() <= model.capacity());
    }

    @Test
    void indirectInstanceCountsMatchVisibleListsPerType() {
        AllocationModel model = new AllocationModel(16, 3);
        model.spawn(4, 0, 1);
        model.spawn(5, 1, 1);
        model.spawn(3, 2, 1);
        model.advanceFrame();

        model.cull(id -> (id & 1) == 0);

        for (int type = 0; type < 3; type++) {
            final int expectedType = type;
            long expected = model.visibleIds().stream()
                .filter(id -> model.typeOf(id) == expectedType).count();
            assertEquals(expected, model.indirectCount(type));
        }
    }

    @Test
    void switchingAliveBuffersDoesNotLoseOrDuplicateParticles() {
        AllocationModel model = populatedModel(20, 12);
        Set<Integer> first = Set.copyOf(model.currentAliveIds());

        model.update(Set.of());
        model.advanceFrame();

        assertEquals(first, Set.copyOf(model.currentAliveIds()));
        assertEquals(first.size(), model.currentAliveIds().size());
    }

    @Test
    void resetClearsEveryListAndCounter() {
        AllocationModel model = populatedModel(12, 12);
        model.cull(id -> true);
        model.spawn(2, 0, 2);

        model.reset();

        assertEquals(12, model.deadCount());
        assertEquals(0, model.currentAliveIds().size());
        assertEquals(0, model.nextAliveIds().size());
        assertEquals(0, model.visibleIds().size());
        assertEquals(0, model.rejectedSpawns());
        assertTrue(Arrays.stream(model.indirectCounts()).allMatch(count -> count == 0));
    }

    @Test
    void persistentEmitterUsesChangingSpawnEpoch() {
        AllocationModel model = new AllocationModel(1, 1);
        model.spawn(1, 0, 100);
        int firstSeed = model.seedOf(model.nextAliveIds().getFirst());

        model.reset();
        model.spawn(1, 0, 101);
        int secondSeed = model.seedOf(model.nextAliveIds().getFirst());

        assertNotEquals(firstSeed, secondSeed);
    }

    @Test
    void activeParticleIsNeverOverwrittenToMakeSpace() {
        AllocationModel model = new AllocationModel(2, 1);
        model.spawn(2, 0, 9);
        List<Integer> ids = List.copyOf(model.nextAliveIds());
        int firstSeed = model.seedOf(ids.getFirst());
        int secondSeed = model.seedOf(ids.getLast());

        model.spawn(1, 0, 10);

        assertEquals(ids, model.nextAliveIds());
        assertEquals(firstSeed, model.seedOf(ids.getFirst()));
        assertEquals(secondSeed, model.seedOf(ids.getLast()));
        assertEquals(1, model.rejectedSpawns());
    }

    @Test
    void duplicateAliveIdIsRejectedByReferenceInvariant() {
        AllocationModel model = new AllocationModel(2, 1);
        model.spawn(1, 0, 1);

        assertThrows(IllegalStateException.class,
            () -> model.appendExistingForInvariantTest(model.nextAliveIds().getFirst()));
    }

    @Test
    void productionShadersRetainTheValidatedSafetyPrimitives() throws IOException {
        String spawn = source("src/client/resources/assets/war_mod/shaders/"
            + "gpu_particles/spawn.comp");
        String update = source("src/client/resources/assets/war_mod/shaders/"
            + "gpu_particles/update.comp");
        String cull = source("src/client/resources/assets/war_mod/shaders/"
            + "gpu_particles/cull.comp");
        String engine = source("src/client/java/com/andye/warmod/particle/gpu/"
            + "GpuParticleEngine.java");

        assertTrue(spawn.contains(
            "atomicCompSwap(deadCount, available, available - 1u)"));
        assertTrue(spawn.contains("atomicAdd(outputAliveCount, 1u)"));
        assertTrue(update.contains("atomicAdd(deadCount, 1u)"));
        assertTrue(update.contains("atomicAdd(outputAliveCount, 1u)"));
        assertTrue(cull.contains("if (id >= particleCapacity) return"));
        assertTrue(cull.contains("atomicAdd(commands[type].instanceCount, 1u)"));
        assertTrue(engine.contains(
            "glUniform1ui(spawnUniforms.spawnEpoch(), (int) frameSequence)"));
        assertTrue(engine.contains("aliveBufferIndex = 1 - aliveBufferIndex"));
        assertFalse(engine.contains("GL_TIMEOUT_IGNORED"),
            "GPU telemetry must never block the render thread indefinitely");
        assertTrue(engine.contains("glClientWaitSync(statsFences[slot], 0, 0L)"));
        assertFalse(spawn.contains("particleId = gl_GlobalInvocationID.x"),
            "Spawning must allocate from the dead list, not overwrite an indexed slot");
    }

    private static AllocationModel populatedModel(final int capacity, final int count) {
        AllocationModel model = new AllocationModel(capacity, 3);
        model.spawn(count, 0, 1);
        model.advanceFrame();
        return model;
    }

    private static String source(final String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir"), relativePath));
    }

    private static final class AllocationModel {
        private final int capacity;
        private final int[] particleTypes;
        private final int[] particleSeeds;
        private final boolean[] active;
        private final ArrayDeque<Integer> dead = new ArrayDeque<>();
        private final ArrayList<Integer> currentAlive = new ArrayList<>();
        private final ArrayList<Integer> nextAlive = new ArrayList<>();
        private final ArrayList<Integer> visible = new ArrayList<>();
        private final int[] indirectCounts;
        private int rejected;

        private AllocationModel(final int capacity, final int typeCount) {
            this.capacity = capacity;
            particleTypes = new int[capacity];
            particleSeeds = new int[capacity];
            active = new boolean[capacity];
            indirectCounts = new int[typeCount];
            reset();
        }

        private void spawn(final int count, final int type, final int epoch) {
            for (int lane = 0; lane < count; lane++) {
                Integer id = dead.pollLast();
                if (id == null) {
                    rejected++;
                    continue;
                }
                if (active[id]) throw new IllegalStateException("overwriting active particle");
                active[id] = true;
                particleTypes[id] = type;
                particleSeeds[id] = mix32(lane ^ id * 131 ^ epoch * 0x9E3779B9);
                appendUnique(nextAlive, id);
            }
        }

        private void update(final Set<Integer> expired) {
            for (int id : currentAlive) {
                if (expired.contains(id)) {
                    if (!active[id]) throw new IllegalStateException("double expiry");
                    active[id] = false;
                    dead.addLast(id);
                } else {
                    appendUnique(nextAlive, id);
                }
            }
        }

        private void cull(final IntPredicate visiblePredicate) {
            visible.clear();
            Arrays.fill(indirectCounts, 0);
            for (int id : currentAlive) {
                if (!visiblePredicate.test(id)) continue;
                if (id < 0 || id >= capacity)
                    throw new IllegalStateException("visible id outside capacity");
                visible.add(id);
                indirectCounts[particleTypes[id]]++;
            }
        }

        private void advanceFrame() {
            currentAlive.clear();
            currentAlive.addAll(nextAlive);
            nextAlive.clear();
            visible.clear();
            Arrays.fill(indirectCounts, 0);
        }

        private void reset() {
            dead.clear();
            currentAlive.clear();
            nextAlive.clear();
            visible.clear();
            Arrays.fill(active, false);
            Arrays.fill(particleTypes, 0);
            Arrays.fill(particleSeeds, 0);
            Arrays.fill(indirectCounts, 0);
            rejected = 0;
            for (int id = 0; id < capacity; id++) dead.addLast(id);
        }

        private void appendExistingForInvariantTest(final int id) {
            appendUnique(nextAlive, id);
        }

        private static void appendUnique(final List<Integer> destination, final int id) {
            if (destination.contains(id))
                throw new IllegalStateException("duplicate alive id " + id);
            destination.add(id);
        }

        private int capacity() { return capacity; }
        private int deadCount() { return dead.size(); }
        private List<Integer> deadIds() { return List.copyOf(dead); }
        private int nextAliveCount() { return nextAlive.size(); }
        private List<Integer> nextAliveIds() { return List.copyOf(nextAlive); }
        private List<Integer> currentAliveIds() { return List.copyOf(currentAlive); }
        private List<Integer> visibleIds() { return List.copyOf(visible); }
        private int rejectedSpawns() { return rejected; }
        private int typeOf(final int id) { return particleTypes[id]; }
        private int seedOf(final int id) { return particleSeeds[id]; }
        private int indirectCount(final int type) { return indirectCounts[type]; }
        private int[] indirectCounts() { return indirectCounts.clone(); }

        private static int mix32(int value) {
            value ^= value >>> 16;
            value *= 0x7FEB352D;
            value ^= value >>> 15;
            value *= 0x846CA68B;
            return value ^ value >>> 16;
        }
    }
}
