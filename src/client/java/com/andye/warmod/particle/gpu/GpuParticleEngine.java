package com.andye.warmod.particle.gpu;

import static org.lwjgl.opengl.GL43C.*;

import com.andye.warmod.WarMod;
import com.andye.warmod.diagnostics.client.ClientPerformanceTelemetry;
import com.andye.warmod.particle.gpu.GpuVfxScheduler.CameraInfo;
import com.andye.warmod.particle.gpu.GpuVfxScheduler.ScheduledFrame;
import com.andye.warmod.warhead.client.render.WarheadRenderSettings;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Effect-aware GPU VFX backend. Important visual layers receive independent
 * minima and weighted screen-space allocation before semantic emitters reach
 * the compute shaders. Unsupported OpenGL contexts retain the established
 * packed CPU-assembled renderers.
 */
public final class GpuParticleEngine {
    public enum Backend { UNINITIALIZED, GPU_COMPUTE, CPU_FALLBACK }
    public enum BackendPreference { AUTO, GPU, CPU }
    public enum Readiness {
        UNINITIALIZED,
        INITIALIZED_UNVERIFIED,
        PROBING,
        READY,
        FAILED
    }
    public enum EffectiveBackend { CPU, GPU }
    public enum DiagnosticMode { OFF, DEPTH_DISABLED, DEPTH_ENABLED }
    public enum EffectClass { NUCLEAR, CONVENTIONAL, FIRE_FIELD, CURTAIN, LEGACY }

    public enum VisualLayer {
        FIREBALL(180, 900, 5_000, 8_000, 2.20F, false),
        MUSHROOM_CLOUD(140, 800, 3_600, 6_000, 2.00F, false),
        STEM(80, 420, 1_800, 3_200, 1.75F, false),
        SHOCKWAVE(120, 520, 2_400, 4_000, 2.10F, false),
        GROUND_CURTAIN(100, 480, 3_000, 5_000, 1.85F, false),
        GROUND_DUST(24, 180, 2_400, 4_500, 1.10F, false),
        DEBRIS(12, 80, 1_200, 2_400, 0.85F, false),
        FLAMES(180, 1_200, 45_000, 70_000, 2.05F, false),
        SMOKE(120, 900, 35_000, 55_000, 1.65F, false),
        EMBERS(8, 40, 2_500, 5_000, 0.70F, false),
        DETAIL(0, 0, 2_000, 4_000, 0.25F, true);

        private final int criticalMinimum, qualityMinimum, target, maximum;
        private final float priority;
        private final boolean canDisappear;

        VisualLayer(final int criticalMinimum, final int qualityMinimum,
            final int target, final int maximum, final float priority,
            final boolean canDisappear) {
            this.criticalMinimum = criticalMinimum;
            this.qualityMinimum = qualityMinimum;
            this.target = target;
            this.maximum = maximum;
            this.priority = priority;
            this.canDisappear = canDisappear;
        }

        int criticalMinimum() { return criticalMinimum; }
        int qualityMinimum() { return qualityMinimum; }
        int target() { return target; }
        int maximum() { return maximum; }
        float priority() { return priority; }
        boolean canDisappear() { return canDisappear; }
    }

    public enum ParticleType {
        FIRE(0, VisualLayer.FLAMES), SMOKE(1, VisualLayer.SMOKE),
        EMBER(2, VisualLayer.EMBERS), EXPLOSION_FIRE(3, VisualLayer.FIREBALL),
        EXPLOSION_SMOKE(4, VisualLayer.MUSHROOM_CLOUD),
        GROUND_DUST(5, VisualLayer.GROUND_DUST), CURTAIN(6, VisualLayer.GROUND_CURTAIN);

        private final int shaderId;
        private final VisualLayer defaultLayer;
        ParticleType(final int shaderId, final VisualLayer defaultLayer) {
            this.shaderId = shaderId; this.defaultLayer = defaultLayer;
        }
    }

    private static final int PARTICLE_CAPACITY = 262_144;
    private static final int TYPE_COUNT = ParticleType.values().length;
    private static final int MAX_EMITTERS_PER_FRAME = GpuVfxScheduler.MAX_SCHEDULED_EMITTERS;
    private static final int PARTICLE_STRIDE = 64;
    private static final int EMITTER_STRIDE = 80;
    private static final int INDIRECT_COMMAND_STRIDE = 16;
    private static final int STATS_UINTS = TYPE_COUNT * 4 + 4;
    private static final int STATS_RING_SIZE = 6;
    private static final String SHADER_ROOT = "/assets/war_mod/shaders/gpu_particles/";
    private static final Set<VisualLayer> GPU_CAPABLE_LAYERS = Set.copyOf(EnumSet.of(
        VisualLayer.FIREBALL, VisualLayer.MUSHROOM_CLOUD, VisualLayer.STEM,
        VisualLayer.GROUND_DUST, VisualLayer.FLAMES, VisualLayer.SMOKE,
        VisualLayer.EMBERS));
    private static final LinkedHashMap<EffectKey, PendingEffect> PENDING_EFFECTS =
        new LinkedHashMap<>();
    private static final LinkedHashMap<Long, FireFieldSubmission> PENDING_FIRE_FIELDS =
        new LinkedHashMap<>();
    private static final ByteBuffer EMITTER_STAGING = MemoryUtil.memAlloc(
        MAX_EMITTERS_PER_FRAME * EMITTER_STRIDE);

    private static volatile Backend backend = Backend.UNINITIALIZED;
    private static volatile BackendPreference backendPreference = BackendPreference.AUTO;
    private static volatile Readiness readiness = Readiness.UNINITIALIZED;
    private static volatile DiagnosticMode diagnosticMode = DiagnosticMode.OFF;
    private static boolean registered, resetRequested, extensionShaderPath;
    private static boolean backendSwitchRequested, diagnosticStateLogged;
    private static int particleBuffer, emitterBuffer, visibleBuffer, indirectBuffer;
    private static int deadListBuffer, dispatchBuffer, vertexArray, statsScratchBuffer;
    private static final int[] aliveBuffers = new int[2];
    private static final int[] statsBuffers = new int[STATS_RING_SIZE];
    private static final long[] statsFences = new long[STATS_RING_SIZE];
    private static final boolean[] statsInFlight = new boolean[STATS_RING_SIZE];
    private static int debugParticleBuffer, debugVisibleBuffer;
    private static int updateProgram, spawnProgram, cullProgram, prepareProgram, renderProgram;
    private static final int[][] timeQueries = new int[GpuStage.values().length][2];
    private static final boolean[][] timeQueryIssued =
        new boolean[GpuStage.values().length][2];
    private static final int[] timeQueryCursor = new int[GpuStage.values().length];
    private static final int[] sampleQueries = new int[2];
    private static final boolean[] sampleQueryIssued = new boolean[2];
    private static final ProbeStage[] sampleProbeStages = new ProbeStage[2];
    private static final boolean[] sampleAutomatic = new boolean[2];
    private static int sampleQueryCursor;
    private static final long[] visibleByType = new long[TYPE_COUNT];
    private static final long[] activeByType = new long[TYPE_COUNT];
    private static final int[] requestedByType = new int[TYPE_COUNT];
    private static final int[] spawnedByType = new int[TYPE_COUNT];
    private static final int[] rejectedByType = new int[TYPE_COUNT];
    @SuppressWarnings("unchecked")
    private static final ArrayDeque<Long>[] GPU_TIMES_NANOS = new ArrayDeque[] {
        new ArrayDeque<Long>(120), new ArrayDeque<Long>(120),
        new ArrayDeque<Long>(120), new ArrayDeque<Long>(120)
    };
    private static final double[] gpuStageEwmaMillis = new double[GpuStage.values().length];
    private static int aliveBufferIndex, statsCursor;
    private static long previousFrameNanos, submittedParticles, requestedParticles;
    private static long rejectedParticles, frameSequence;
    private static double adaptiveQuality = 1.0, gpuTimeEwmaMillis;
    private static int scheduledLayerCount, scheduledEmitterCount;
    private static int clientFirePatches, fireFieldSubmissions;
    private static int acceptedFirePackets, rejectedFirePackets, staleFirePackets;
    private static int receivedFirePatchEntries, storedFirePatches;
    private static long deadSlots = PARTICLE_CAPACITY;
    private static long distanceCulled, sizeCulled, frustumCulled;
    private static long statsReadbackSkipped;
    private static long debugSamplesPassed = -1L;
    private static ProbeStage automaticProbeStage = ProbeStage.DEPTH_DISABLED;
    private static boolean depthDisabledProbePassed, depthEnabledProbePassed;

    private GpuParticleEngine() { }

    public static synchronized void register() {
        if (registered) return;
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(GpuParticleEngine::render);
        registered = true;
    }

    public static Backend backend() { return backend; }
    public static BackendPreference backendPreference() { return backendPreference; }
    public static Readiness readiness() { return readiness; }
    public static DiagnosticMode diagnosticMode() { return diagnosticMode; }
    public static boolean isGpuInitialized() {
        return backend == Backend.GPU_COMPUTE;
    }
    public static boolean isGpuReady() {
        return isGpuInitialized() && readiness == Readiness.READY
            && backendPreference != BackendPreference.CPU && !backendSwitchRequested;
    }
    public static EffectiveBackend effectiveBackend() {
        return isGpuReady() ? EffectiveBackend.GPU : EffectiveBackend.CPU;
    }
    public static boolean canRender(final VisualLayer layer) {
        return layer != null && isGpuReady() && GPU_CAPABLE_LAYERS.contains(layer);
    }
    /** Compatibility alias for callers that only need the effective backend. */
    public static boolean isGpuActive() {
        return isGpuReady();
    }
    public static synchronized void setBackendPreference(final BackendPreference preference) {
        if (preference == null) throw new IllegalArgumentException("preference");
        if (backendPreference == preference && !backendSwitchRequested) return;
        backendPreference = preference;
        backendSwitchRequested = true;
        resetRequested = true;
    }
    public static synchronized void setDiagnosticMode(final DiagnosticMode mode) {
        if (mode == null) throw new IllegalArgumentException("mode");
        diagnosticMode = mode;
        diagnosticStateLogged = false;
        debugSamplesPassed = -1L;
    }
    public static long stableId(final UUID id) {
        return id == null ? 0L : mix64(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
    }

    public static EffectHandle beginEffect(final EffectClass effectClass, final long id,
        final Vec3 position, final float boundsRadius, final float temporalImportance) {
        return new EffectHandle(new EffectDescriptor(effectClass, id, position,
            boundsRadius, temporalImportance));
    }

    /** Compatibility entry point; callers should prefer semantic effects or fire fields. */
    public static synchronized void submit(final EmitterCommand command) {
        if (command == null || !command.valid()) return;
        long id = mix64(Integer.toUnsignedLong(command.seed())
            ^ ((long) command.type().shaderId << 56));
        EffectDescriptor descriptor = new EffectDescriptor(EffectClass.LEGACY, id,
            command.position(), Math.max(command.size(), command.spread()) * 2.0F, 0.65F);
        submitLayer(descriptor, command.type().defaultLayer, List.of(command));
    }

    public static synchronized void submitFireField(final FireFieldSubmission field) {
        if (field == null || !field.valid() || backendPreference == BackendPreference.CPU) return;
        PENDING_FIRE_FIELDS.merge(field.regionId(), field, FireFieldSubmission::merge);
        fireFieldSubmissions++;
    }

    public static synchronized void recordClientFirePatches(final int count) {
        clientFirePatches = Math.max(0, count);
        fireFieldSubmissions = 0;
    }

    public static synchronized void recordFirePacket(final boolean accepted,
        final boolean stale, final int receivedEntries, final int storedPatches) {
        if (accepted) acceptedFirePackets++;
        else { rejectedFirePackets++; if (stale) staleFirePackets++; }
        if (accepted) {
            receivedFirePatchEntries = Math.max(0, receivedEntries);
            storedFirePatches = Math.max(0, storedPatches);
        }
    }

    public static synchronized void clearLevel() {
        PENDING_EFFECTS.clear(); PENDING_FIRE_FIELDS.clear();
        GpuVfxScheduler.clear(); resetRequested = true;
        Arrays.fill(activeByType, 0L); Arrays.fill(visibleByType, 0L);
        Arrays.fill(requestedByType, 0); Arrays.fill(spawnedByType, 0);
        Arrays.fill(rejectedByType, 0);
        clientFirePatches = fireFieldSubmissions = receivedFirePatchEntries = storedFirePatches = 0;
        acceptedFirePackets = rejectedFirePackets = staleFirePackets = 0;
        submittedParticles = requestedParticles = rejectedParticles = 0L;
    }

    public static synchronized DebugSnapshot debugSnapshot() {
        long active = Arrays.stream(activeByType).sum();
        long visible = Math.min(active, Arrays.stream(visibleByType).sum());
        long vram = isGpuInitialized() ? (long) PARTICLE_CAPACITY * PARTICLE_STRIDE
            + (long) PARTICLE_CAPACITY * TYPE_COUNT * Integer.BYTES
            + (long) MAX_EMITTERS_PER_FRAME * EMITTER_STRIDE
            + (long) TYPE_COUNT * INDIRECT_COMMAND_STRIDE
            + 3L * (PARTICLE_CAPACITY + 1L) * Integer.BYTES
            + (long) STATS_RING_SIZE * STATS_UINTS * Integer.BYTES : 0L;
        FireDebugCounters fire = new FireDebugCounters(clientFirePatches,
            fireFieldSubmissions, spawnedByType[ParticleType.FIRE.shaderId],
            visibleByType[ParticleType.FIRE.shaderId],
            spawnedByType[ParticleType.SMOKE.shaderId],
            visibleByType[ParticleType.SMOKE.shaderId], acceptedFirePackets,
            rejectedFirePackets, staleFirePackets, receivedFirePatchEntries,
            storedFirePatches);
        return new DebugSnapshot(backend, backendPreference, readiness, effectiveBackend(),
            active, visible,
            Math.max(0L, active - visible), submittedParticles, requestedParticles,
            rejectedParticles, deadSlots,
            PARTICLE_CAPACITY, vram, gpuTimings(), adaptiveQuality,
            scheduledLayerCount, scheduledEmitterCount, distanceCulled, sizeCulled,
            frustumCulled, statsReadbackSkipped, diagnosticMode, debugSamplesPassed, fire);
    }

    private static synchronized void submitLayer(final EffectDescriptor descriptor,
        final VisualLayer layer, final List<EmitterCommand> commands) {
        if (descriptor == null || !descriptor.valid() || layer == null
            || commands == null || commands.isEmpty()
            || backendPreference == BackendPreference.CPU
            || !GPU_CAPABLE_LAYERS.contains(layer)) return;
        EffectKey key = new EffectKey(descriptor.effectClass(), descriptor.id());
        PendingEffect effect = PENDING_EFFECTS.computeIfAbsent(key,
            ignored -> new PendingEffect(descriptor));
        effect.descriptor = descriptor;
        ArrayList<EmitterCommand> destination = effect.layers.computeIfAbsent(layer,
            ignored -> new ArrayList<>());
        for (EmitterCommand command : commands)
            if (command != null && command.valid()) destination.add(command);
    }

    private static void render(final LevelRenderContext context) {
        long cpuStarted = System.nanoTime();
        RenderSystem.assertOnRenderThread();
        if (backendSwitchRequested) applyBackendPreference();
        else if (backend == Backend.UNINITIALIZED) initialize();
        long extractionStarted = System.nanoTime();
        FrameSubmissions submissions = drainSubmissions();
        ClientPerformanceTelemetry.recordGpuExtractionNanos(
            Math.max(0L, System.nanoTime() - extractionStarted));
        if (backend != Backend.GPU_COMPUTE) return;
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (camera == null || camera.pos == null || camera.projectionMatrix == null
            || camera.viewRotationMatrix == null) return;
        long now = System.nanoTime();
        float deltaSeconds = previousFrameNanos == 0L ? 1.0F / 60.0F
            : Math.min(0.10F, Math.max(0.001F,
                (now - previousFrameNanos) / 1_000_000_000.0F));
        previousFrameNanos = now; frameSequence++;

        GlState state = GlState.capture();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Matrix4f viewProjection = new Matrix4f(camera.projectionMatrix)
                .mul(camera.viewRotationMatrix);
            if (diagnosticMode != DiagnosticMode.OFF) {
                drawDiagnostic(camera, viewProjection, state, stack,
                    diagnosticMode == DiagnosticMode.DEPTH_ENABLED, true,
                    diagnosticMode == DiagnosticMode.DEPTH_ENABLED
                        ? ProbeStage.DEPTH_ENABLED : ProbeStage.DEPTH_DISABLED,
                    false);
                return;
            }
            if (readiness != Readiness.READY) {
                if (readiness == Readiness.FAILED) return;
                readiness = Readiness.PROBING;
                drawDiagnostic(camera, viewProjection, state, stack,
                    automaticProbeStage == ProbeStage.DEPTH_ENABLED, false,
                    automaticProbeStage, true);
                return;
            }
            if (resetRequested) { clearParticleStorage(); resetRequested = false; }
            IntBuffer viewport = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, viewport);
            int viewportWidth = Math.max(1, viewport.get(2));
            int viewportHeight = Math.max(1, viewport.get(3));
            CameraInfo cameraInfo = new CameraInfo(camera.pos, viewProjection,
                Math.max(0.01F, Math.abs(camera.projectionMatrix.m11())),
                viewportWidth, viewportHeight);
            collectCompletedStats();
            int statsSlot = acquireStatsSlot();
            int frameStatsBuffer = statsSlot >= 0
                ? statsBuffers[statsSlot] : statsScratchBuffer;
            resetFrameBuffers(stack, frameStatsBuffer);
            long schedulerStarted = System.nanoTime();
            ScheduledFrame scheduled = GpuVfxScheduler.schedule(submissions,
                cameraInfo, adaptiveQuality, frameSequence, deltaSeconds,
                Math.max(0L, deadSlots), WarheadRenderSettings.gpuBudgetScale());
            ClientPerformanceTelemetry.recordGpuSchedulerNanos(
                Math.max(0L, System.nanoTime() - schedulerStarted));
            scheduledLayerCount = scheduled.visibleLayerCount();
            scheduledEmitterCount = scheduled.emitters().size();
            int spawned = uploadEmitters(scheduled.emitters(), deltaSeconds);
            requestedParticles += spawned;

            bindStorageBuffers(frameStatsBuffer);
            prepareDispatch(false);
            runTimedGpuStage(GpuStage.UPDATE, () -> dispatchUpdate(deltaSeconds));
            if (!scheduled.emitters().isEmpty()) runTimedGpuStage(GpuStage.SPAWN,
                () -> dispatchSpawn(scheduled.emitters().size()));
            prepareDispatch(true);
            runTimedGpuStage(GpuStage.CULL, () -> dispatchCull(camera.pos,
                viewProjection, viewportHeight, cameraInfo.projectionScale(), stack));
            runTimedGpuStage(GpuStage.RASTER, () -> draw(camera, viewProjection, state, stack));
            aliveBufferIndex = 1 - aliveBufferIndex;
            if (statsSlot >= 0) {
                statsFences[statsSlot] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
                statsInFlight[statsSlot] = true;
            }
        } catch (RuntimeException exception) {
            WarMod.LOGGER.error("GPU VFX backend failed; returning to packed fallback", exception);
            destroyResources(); backend = Backend.CPU_FALLBACK; readiness = Readiness.FAILED;
        } finally {
            releaseScratchStatsBuffer();
            state.restore();
            ClientPerformanceTelemetry.recordGpuEngineCpuNanos(
                Math.max(0L, System.nanoTime() - cpuStarted));
        }
    }

    private static void applyBackendPreference() {
        destroyResources();
        readiness = Readiness.UNINITIALIZED;
        backendSwitchRequested = false;
        if (backendPreference == BackendPreference.CPU) {
            backend = Backend.CPU_FALLBACK;
            return;
        }
        backend = Backend.UNINITIALIZED;
        initialize();
    }

    private static synchronized FrameSubmissions drainSubmissions() {
        ArrayList<EffectSubmission> effects = new ArrayList<>(PENDING_EFFECTS.size());
        for (PendingEffect pending : PENDING_EFFECTS.values()) {
            EnumMap<VisualLayer, List<EmitterCommand>> layers = new EnumMap<>(VisualLayer.class);
            for (Map.Entry<VisualLayer, ArrayList<EmitterCommand>> entry
                : pending.layers.entrySet()) if (!entry.getValue().isEmpty())
                    layers.put(entry.getKey(), List.copyOf(entry.getValue()));
            if (!layers.isEmpty()) effects.add(new EffectSubmission(pending.descriptor,
                Map.copyOf(layers)));
        }
        List<FireFieldSubmission> fireFields = List.copyOf(PENDING_FIRE_FIELDS.values());
        PENDING_EFFECTS.clear(); PENDING_FIRE_FIELDS.clear();
        return new FrameSubmissions(List.copyOf(effects), fireFields);
    }

    private static void initialize() {
        if (backendPreference == BackendPreference.CPU) {
            backend = Backend.CPU_FALLBACK; readiness = Readiness.UNINITIALIZED;
            return;
        }
        try {
            String backendName = RenderSystem.getDevice().getDeviceInfo().backendName();
            GLCapabilities capabilities = GL.getCapabilities();
            boolean coreCompute = capabilities.OpenGL43;
            boolean extensionCompute = capabilities.GL_ARB_compute_shader
                && capabilities.GL_ARB_shader_storage_buffer_object
                && capabilities.GL_ARB_shader_image_load_store
                && capabilities.GL_ARB_clear_buffer_object;
            boolean indirect = RenderSystem.getDevice().getDeviceInfo().features().drawIndirect()
                && (capabilities.OpenGL40 || capabilities.GL_ARB_draw_indirect);
            int storageBindings = glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
            if (backendName == null || !backendName.toLowerCase().contains("opengl")
                || (!coreCompute && !extensionCompute) || !indirect || storageBindings < 9) {
                backend = Backend.CPU_FALLBACK; readiness = Readiness.FAILED;
                WarMod.LOGGER.info("War Mod GPU VFX unavailable: backend={}, core43={}, "
                    + "extensionCompute={}, indirect={}, ssboBindings={}; using packed fallback",
                    backendName, coreCompute, extensionCompute, indirect, storageBindings);
                return;
            }
            extensionShaderPath = !coreCompute;
            updateProgram = computeProgram("update.comp");
            spawnProgram = computeProgram("spawn.comp");
            cullProgram = computeProgram("cull.comp");
            prepareProgram = computeProgram("prepare_dispatch.comp");
            renderProgram = graphicsProgram("particle.vert", "particle.frag");
            particleBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
                (long) PARTICLE_CAPACITY * PARTICLE_STRIDE, GL_DYNAMIC_DRAW);
            emitterBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
                (long) MAX_EMITTERS_PER_FRAME * EMITTER_STRIDE, GL_STREAM_DRAW);
            visibleBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
                (long) PARTICLE_CAPACITY * TYPE_COUNT * Integer.BYTES, GL_DYNAMIC_DRAW);
            indirectBuffer = createBuffer(GL_DRAW_INDIRECT_BUFFER,
                (long) TYPE_COUNT * INDIRECT_COMMAND_STRIDE, GL_DYNAMIC_DRAW);
            deadListBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
                (long) (PARTICLE_CAPACITY + 1) * Integer.BYTES, GL_DYNAMIC_DRAW);
            aliveBuffers[0] = createBuffer(GL_SHADER_STORAGE_BUFFER,
                (long) (PARTICLE_CAPACITY + 1) * Integer.BYTES, GL_DYNAMIC_DRAW);
            aliveBuffers[1] = createBuffer(GL_SHADER_STORAGE_BUFFER,
                (long) (PARTICLE_CAPACITY + 1) * Integer.BYTES, GL_DYNAMIC_DRAW);
            dispatchBuffer = createBuffer(GL_DISPATCH_INDIRECT_BUFFER, 12L, GL_DYNAMIC_DRAW);
            for (int slot = 0; slot < STATS_RING_SIZE; slot++)
                statsBuffers[slot] = createBuffer(GL_SHADER_STORAGE_BUFFER,
                    (long) STATS_UINTS * Integer.BYTES, GL_STREAM_READ);
            debugParticleBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
                PARTICLE_STRIDE, GL_STREAM_DRAW);
            debugVisibleBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
                Integer.BYTES, GL_STREAM_DRAW);
            vertexArray = glGenVertexArrays();
            for (int stage = 0; stage < GpuStage.values().length; stage++) {
                timeQueries[stage][0] = glGenQueries();
                timeQueries[stage][1] = glGenQueries();
            }
            sampleQueries[0] = glGenQueries(); sampleQueries[1] = glGenQueries();
            clearParticleStorage();
            backend = Backend.GPU_COMPUTE;
            readiness = Readiness.INITIALIZED_UNVERIFIED;
            automaticProbeStage = ProbeStage.DEPTH_DISABLED;
            depthDisabledProbePassed = false;
            depthEnabledProbePassed = false;
            WarMod.LOGGER.info("War Mod effect-aware GPU VFX enabled: {} particles, "
                + "{} semantic emitter slots, extensionPath={}",
                PARTICLE_CAPACITY, MAX_EMITTERS_PER_FRAME, !coreCompute);
        } catch (RuntimeException | IOException exception) {
            WarMod.LOGGER.warn("GPU VFX unavailable; using packed fallback", exception);
            destroyResources(); backend = Backend.CPU_FALLBACK; readiness = Readiness.FAILED;
        }
    }

    private static int createBuffer(final int target, final long bytes, final int usage) {
        int buffer = glGenBuffers(); glBindBuffer(target, buffer);
        glBufferData(target, bytes, usage); glBindBuffer(target, 0); return buffer;
    }

    private static void clearParticleStorage() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer zero = stack.calloc(16);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, particleBuffer);
            glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_RGBA32UI,
                GL_RGBA_INTEGER, GL_UNSIGNED_INT, zero);
            ByteBuffer dead = MemoryUtil.memAlloc((PARTICLE_CAPACITY + 1) * Integer.BYTES);
            try {
                dead.putInt(PARTICLE_CAPACITY);
                for (int id = 0; id < PARTICLE_CAPACITY; id++) dead.putInt(id);
                dead.flip();
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, deadListBuffer);
                glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, dead);
            } finally {
                MemoryUtil.memFree(dead);
            }
            for (int aliveBuffer : aliveBuffers) {
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, aliveBuffer);
                glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, stack.calloc(4));
            }
            for (int statsBuffer : statsBuffers) {
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, statsBuffer);
                glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L,
                    stack.calloc(STATS_UINTS * Integer.BYTES));
            }
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }
        previousFrameNanos = 0L;
        aliveBufferIndex = 0;
        deadSlots = PARTICLE_CAPACITY;
        Arrays.fill(activeByType, 0L); Arrays.fill(visibleByType, 0L);
        Arrays.fill(requestedByType, 0); Arrays.fill(spawnedByType, 0);
        Arrays.fill(rejectedByType, 0);
    }

    private static int uploadEmitters(final List<EmitterCommand> emitters,
        final float deltaSeconds) {
        Arrays.fill(requestedByType, 0);
        if (emitters.isEmpty()) return 0;
        EMITTER_STAGING.clear(); int totalSpawned = 0;
        for (EmitterCommand emitter : emitters) {
            putVec4(EMITTER_STAGING, emitter.position().x, emitter.position().y,
                emitter.position().z, emitter.scale());
            putVec4(EMITTER_STAGING, emitter.velocity().x, emitter.velocity().y,
                emitter.velocity().z, emitter.lifetimeSeconds());
            putVec4(EMITTER_STAGING, emitter.red(), emitter.green(), emitter.blue(),
                emitter.opacity());
            putVec4(EMITTER_STAGING, emitter.size(), emitter.spread(),
                emitter.velocityJitter(), emitter.importance());
            int spawnCount = frameSpawnCount(emitter, deltaSeconds);
            totalSpawned += spawnCount; requestedByType[emitter.type().shaderId] += spawnCount;
            EMITTER_STAGING.putInt(spawnCount).putInt(emitter.seed())
                .putInt(emitter.type().shaderId).putInt(emitter.flags());
        }
        EMITTER_STAGING.flip(); glBindBuffer(GL_SHADER_STORAGE_BUFFER, emitterBuffer);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, EMITTER_STAGING);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0); return totalSpawned;
    }

    private static int frameSpawnCount(final EmitterCommand emitter,
        final float deltaSeconds) {
        double expected = Math.min(64.0, emitter.spawnCount() * deltaSeconds);
        int whole = (int) Math.floor(expected); double fraction = expected - whole;
        long mixed = Integer.toUnsignedLong(emitter.seed()) * 0x9E3779B97F4A7C15L
            ^ frameSequence * 0xD1B54A32D192ED03L;
        double unit = (mix64(mixed) >>> 11) * 0x1.0p-53;
        return whole + (unit < fraction ? 1 : 0);
    }

    private static void putVec4(final ByteBuffer buffer, final double x, final double y,
        final double z, final double w) {
        buffer.putFloat((float) x).putFloat((float) y).putFloat((float) z).putFloat((float) w);
    }

    private static void bindStorageBuffers(final int statsBuffer) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, particleBuffer);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, emitterBuffer);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, visibleBuffer);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, indirectBuffer);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, deadListBuffer);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, aliveBuffers[aliveBufferIndex]);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, aliveBuffers[1 - aliveBufferIndex]);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, statsBuffer);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 8, dispatchBuffer);
    }

    private static void prepareDispatch(final boolean useNextAliveList) {
        glUseProgram(prepareProgram);
        glUniform1i(glGetUniformLocation(prepareProgram, "useNextAliveList"),
            useNextAliveList ? GL_TRUE : GL_FALSE);
        glUniform1ui(glGetUniformLocation(prepareProgram, "particleTypeCount"), TYPE_COUNT);
        glDispatchCompute(1, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
    }

    private static void dispatchUpdate(final float deltaSeconds) {
        glUseProgram(updateProgram);
        glUniform1ui(glGetUniformLocation(updateProgram, "particleCapacity"), PARTICLE_CAPACITY);
        glUniform1ui(glGetUniformLocation(updateProgram, "particleTypeCount"), TYPE_COUNT);
        glUniform1f(glGetUniformLocation(updateProgram, "deltaSeconds"), deltaSeconds);
        glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, dispatchBuffer);
        glDispatchComputeIndirect(0L);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private static void dispatchSpawn(final int emitterCount) {
        glUseProgram(spawnProgram);
        glUniform1ui(glGetUniformLocation(spawnProgram, "particleCapacity"), PARTICLE_CAPACITY);
        glUniform1ui(glGetUniformLocation(spawnProgram, "particleTypeCount"), TYPE_COUNT);
        glUniform1ui(glGetUniformLocation(spawnProgram, "emitterCount"), emitterCount);
        glUniform1ui(glGetUniformLocation(spawnProgram, "spawnEpoch"), (int) frameSequence);
        glDispatchCompute(emitterCount, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private static void resetFrameBuffers(final MemoryStack stack, final int statsBuffer) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, aliveBuffers[1 - aliveBufferIndex]);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, stack.calloc(Integer.BYTES));
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, statsBuffer);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L,
            stack.calloc(STATS_UINTS * Integer.BYTES));
        ByteBuffer commands = stack.malloc(TYPE_COUNT * INDIRECT_COMMAND_STRIDE);
        for (int type = 0; type < TYPE_COUNT; type++)
            commands.putInt(4).putInt(0).putInt(0).putInt(0);
        commands.flip(); glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
        glBufferSubData(GL_DRAW_INDIRECT_BUFFER, 0L, commands);
        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
    }

    private static void dispatchCull(final Vec3 camera, final Matrix4f viewProjection,
        final int viewportHeight, final float projectionScale, final MemoryStack stack) {
        glUseProgram(cullProgram);
        glUniform1ui(glGetUniformLocation(cullProgram, "particleCapacity"), PARTICLE_CAPACITY);
        glUniform1ui(glGetUniformLocation(cullProgram, "particleTypeCount"), TYPE_COUNT);
        glUniform1f(glGetUniformLocation(cullProgram, "viewportHeight"), viewportHeight);
        glUniform1f(glGetUniformLocation(cullProgram, "projectionScale"), projectionScale);
        glUniform3f(glGetUniformLocation(cullProgram, "cameraPosition"),
            (float) camera.x, (float) camera.y, (float) camera.z);
        uniformMatrix(cullProgram, "viewProjection", viewProjection, stack);
        glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, dispatchBuffer);
        glDispatchComputeIndirect(0L);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
    }

    private static void draw(final CameraRenderState camera,
        final Matrix4f viewProjection, final GlState state, final MemoryStack stack) {
        Quaternionf orientation = camera.orientation == null
            ? new Quaternionf() : new Quaternionf(camera.orientation);
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(orientation);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(orientation);
        glUseProgram(renderProgram); uniformMatrix(renderProgram, "viewProjection", viewProjection, stack);
        glUniform3f(glGetUniformLocation(renderProgram, "cameraPosition"),
            (float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z);
        glUniform3f(glGetUniformLocation(renderProgram, "cameraRight"), right.x, right.y, right.z);
        glUniform3f(glGetUniformLocation(renderProgram, "cameraUp"), up.x, up.y, up.z);
        glUniform1i(glGetUniformLocation(renderProgram, "directDebug"), GL_FALSE);
        glBindVertexArray(vertexArray); glEnable(GL_BLEND); glEnable(GL_DEPTH_TEST);
        glDepthFunc(state.depthFunction()); glDepthMask(false); glDisable(GL_CULL_FACE);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
        int visibleBaseUniform = glGetUniformLocation(renderProgram, "visibleBase");
        for (ParticleType type : ParticleType.values()) {
            if (type == ParticleType.FIRE || type == ParticleType.EMBER
                || type == ParticleType.EXPLOSION_FIRE) glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            else glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glUniform1ui(visibleBaseUniform, type.shaderId * PARTICLE_CAPACITY);
            glDrawArraysIndirect(GL_TRIANGLE_STRIP,
                (long) type.shaderId * INDIRECT_COMMAND_STRIDE);
        }
    }

    private static void uniformMatrix(final int program, final String name,
        final Matrix4f matrix, final MemoryStack stack) {
        FloatBuffer values = stack.mallocFloat(16); matrix.get(values);
        glUniformMatrix4fv(glGetUniformLocation(program, name), false, values);
    }

    private static void runTimedGpuStage(final GpuStage stage, final Runnable work) {
        int query = beginGpuTimer(stage);
        try { work.run(); }
        finally { if (query != 0) glEndQuery(GL_TIME_ELAPSED); }
    }

    private static int beginGpuTimer(final GpuStage stage) {
        int stageIndex = stage.ordinal();
        int slot = timeQueryCursor[stageIndex], query = timeQueries[stageIndex][slot];
        if (query == 0) return 0;
        if (timeQueryIssued[stageIndex][slot]) {
            if (glGetQueryObjecti(query, GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) return 0;
            long nanos = Math.max(0L, glGetQueryObjecti64(query, GL_QUERY_RESULT));
            synchronized (GpuParticleEngine.class) {
                ArrayDeque<Long> samples = GPU_TIMES_NANOS[stageIndex];
                if (samples.size() == 120) samples.removeFirst();
                samples.addLast(nanos);
                double millis = nanos / 1_000_000.0;
                gpuStageEwmaMillis[stageIndex] = gpuStageEwmaMillis[stageIndex] == 0.0
                    ? millis : gpuStageEwmaMillis[stageIndex] * 0.90 + millis * 0.10;
                if (stage == GpuStage.RASTER) {
                    gpuTimeEwmaMillis = Arrays.stream(gpuStageEwmaMillis).sum();
                    if (gpuTimeEwmaMillis > 2.35) adaptiveQuality -= 0.018;
                    else if (gpuTimeEwmaMillis < 1.55) adaptiveQuality += 0.009;
                    adaptiveQuality = Mth.clamp(adaptiveQuality, 0.22, 1.35);
                }
            }
            timeQueryIssued[stageIndex][slot] = false;
        }
        glBeginQuery(GL_TIME_ELAPSED, query);
        timeQueryIssued[stageIndex][slot] = true;
        timeQueryCursor[stageIndex] = (slot + 1) & 1;
        return query;
    }

    private static GpuStageTimings gpuTimings() {
        return new GpuStageTimings(gpuTiming(GpuStage.UPDATE), gpuTiming(GpuStage.SPAWN),
            gpuTiming(GpuStage.CULL), gpuTiming(GpuStage.RASTER));
    }

    private static GpuTiming gpuTiming(final GpuStage stage) {
        ArrayDeque<Long> samples = GPU_TIMES_NANOS[stage.ordinal()];
        long[] sorted = new long[samples.size()]; int index = 0;
        for (long value : samples) sorted[index++] = value;
        Arrays.sort(sorted);
        return new GpuTiming(percentileMillis(sorted, 0.50), percentileMillis(sorted, 0.95),
            percentileMillis(sorted, 0.99), sorted.length == 0 ? 0.0
                : sorted[sorted.length - 1] / 1_000_000.0);
    }

    private static int acquireStatsSlot() {
        for (int offset = 0; offset < STATS_RING_SIZE; offset++) {
            int slot = (statsCursor + offset) % STATS_RING_SIZE;
            if (!statsInFlight[slot]) {
                statsCursor = (slot + 1) % STATS_RING_SIZE;
                return slot;
            }
        }
        statsReadbackSkipped++;
        statsScratchBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
            (long) STATS_UINTS * Integer.BYTES, GL_STREAM_DRAW);
        return -1;
    }

    private static void releaseScratchStatsBuffer() {
        if (statsScratchBuffer == 0) return;
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, 0);
        glDeleteBuffers(statsScratchBuffer);
        statsScratchBuffer = 0;
    }

    private static void collectCompletedStats() {
        for (int slot = 0; slot < STATS_RING_SIZE; slot++) {
            if (!statsInFlight[slot] || statsFences[slot] == 0L) continue;
            int result = glClientWaitSync(statsFences[slot], 0, 0L);
            if (result == GL_ALREADY_SIGNALED || result == GL_CONDITION_SATISFIED)
                collectStatsSlot(slot);
        }
    }

    private static void collectStatsSlot(final int slot) {
        ByteBuffer data = MemoryUtil.memAlloc(STATS_UINTS * Integer.BYTES);
        try {
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, statsBuffers[slot]);
            glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, data);
            for (int type = 0; type < TYPE_COUNT; type++) {
                activeByType[type] = Integer.toUnsignedLong(data.getInt(type * Integer.BYTES));
                spawnedByType[type] = data.getInt((TYPE_COUNT + type) * Integer.BYTES);
                rejectedByType[type] = data.getInt((TYPE_COUNT * 2 + type) * Integer.BYTES);
                visibleByType[type] = Integer.toUnsignedLong(
                    data.getInt((TYPE_COUNT * 3 + type) * Integer.BYTES));
                submittedParticles += Integer.toUnsignedLong(spawnedByType[type]);
                rejectedParticles += Integer.toUnsignedLong(rejectedByType[type]);
            }
            deadSlots = Math.min(PARTICLE_CAPACITY, Integer.toUnsignedLong(
                data.getInt((TYPE_COUNT * 4 + 3) * Integer.BYTES)));
            distanceCulled = Integer.toUnsignedLong(data.getInt(TYPE_COUNT * 4 * Integer.BYTES));
            sizeCulled = Integer.toUnsignedLong(data.getInt((TYPE_COUNT * 4 + 1) * Integer.BYTES));
            frustumCulled = Integer.toUnsignedLong(data.getInt((TYPE_COUNT * 4 + 2) * Integer.BYTES));
        } finally {
            MemoryUtil.memFree(data);
            if (statsFences[slot] != 0L) glDeleteSync(statsFences[slot]);
            statsFences[slot] = 0L;
            statsInFlight[slot] = false;
        }
    }

    private static void drawDiagnostic(final CameraRenderState camera,
        final Matrix4f viewProjection, final GlState state, final MemoryStack stack,
        final boolean depthEnabled, final boolean visible, final ProbeStage probeStage,
        final boolean automatic) {
        Quaternionf orientation = camera.orientation == null
            ? new Quaternionf() : new Quaternionf(camera.orientation);
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(orientation);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(orientation);
        Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F).rotate(orientation).normalize();
        double probeDistance = automatic ? 0.5 : 5.0;
        Vec3 position = camera.pos.add(forward.x * probeDistance,
            forward.y * probeDistance, forward.z * probeDistance);
        ByteBuffer particle = stack.malloc(PARTICLE_STRIDE);
        putVec4(particle, position.x, position.y, position.z, 0.5);
        putVec4(particle, 0.0, 0.0, 0.0, 10.0);
        putVec4(particle, 1.0, 0.0, 1.0, 2.0);
        particle.putInt(ParticleType.EXPLOSION_FIRE.shaderId).putInt(0x4D414745)
            .putInt(0).putInt(Float.floatToRawIntBits(1.0F)).flip();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, debugParticleBuffer);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, particle);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, debugVisibleBuffer);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, stack.ints(0));
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, debugParticleBuffer);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, debugVisibleBuffer);
        glUseProgram(renderProgram);
        uniformMatrix(renderProgram, "viewProjection", viewProjection, stack);
        glUniform3f(glGetUniformLocation(renderProgram, "cameraPosition"),
            (float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z);
        glUniform3f(glGetUniformLocation(renderProgram, "cameraRight"), right.x, right.y, right.z);
        glUniform3f(glGetUniformLocation(renderProgram, "cameraUp"), up.x, up.y, up.z);
        glUniform1ui(glGetUniformLocation(renderProgram, "visibleBase"), 0);
        glUniform1i(glGetUniformLocation(renderProgram, "directDebug"), GL_TRUE);
        glUniform1i(glGetUniformLocation(renderProgram, "diagnosticVisible"),
            visible ? GL_TRUE : GL_FALSE);
        glBindVertexArray(vertexArray); glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false); glDisable(GL_CULL_FACE);
        if (!depthEnabled) glDisable(GL_DEPTH_TEST);
        else { glEnable(GL_DEPTH_TEST); glDepthFunc(state.depthFunction()); }
        logDiagnosticState(state);
        boolean query = beginSampleQuery(probeStage, automatic);
        try { glDrawArrays(GL_TRIANGLE_STRIP, 0, 4); }
        finally { if (query) glEndQuery(GL_ANY_SAMPLES_PASSED); }
    }

    private static boolean beginSampleQuery(final ProbeStage stage, final boolean automatic) {
        int slot = sampleQueryCursor, query = sampleQueries[slot];
        if (query == 0) return false;
        if (sampleQueryIssued[slot]) {
            if (glGetQueryObjecti(query, GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) return false;
            debugSamplesPassed = glGetQueryObjecti64(query, GL_QUERY_RESULT);
            acceptProbeResult(sampleProbeStages[slot], sampleAutomatic[slot],
                debugSamplesPassed > 0L);
            sampleQueryIssued[slot] = false;
        }
        glBeginQuery(GL_ANY_SAMPLES_PASSED, query);
        sampleQueryIssued[slot] = true;
        sampleProbeStages[slot] = stage;
        sampleAutomatic[slot] = automatic;
        sampleQueryCursor = (slot + 1) & 1;
        return true;
    }

    private static void acceptProbeResult(final ProbeStage stage, final boolean automatic,
        final boolean passed) {
        if (!automatic || stage == null || readiness == Readiness.FAILED
            || readiness == Readiness.READY) return;
        if (!passed) {
            readiness = Readiness.FAILED;
            WarMod.LOGGER.warn("War Mod GPU readiness probe failed at {}; retaining CPU visuals",
                stage.name().toLowerCase(java.util.Locale.ROOT));
            return;
        }
        if (stage == ProbeStage.DEPTH_DISABLED) {
            depthDisabledProbePassed = true;
            automaticProbeStage = ProbeStage.DEPTH_ENABLED;
        } else {
            depthEnabledProbePassed = true;
        }
        if (depthDisabledProbePassed && depthEnabledProbePassed) {
            readiness = Readiness.READY;
            WarMod.LOGGER.info("War Mod GPU raster readiness probes passed; GPU layers enabled");
        }
    }

    private static void logDiagnosticState(final GlState state) {
        if (diagnosticStateLogged) return;
        diagnosticStateLogged = true;
        GLCapabilities capabilities = GL.getCapabilities();
        int clipOrigin = -1, clipDepthMode = -1;
        if (capabilities.OpenGL45 || capabilities.GL_ARB_clip_control) {
            clipOrigin = glGetInteger(0x935C);
            clipDepthMode = glGetInteger(0x935D);
        }
        WarMod.LOGGER.info("GPU billboard diagnostic mode={} depthFunc={} depthClear={} "
                + "clipOrigin={} clipDepthMode={} framebuffer={} viewport={},{},{},{}",
            diagnosticMode, state.depthFunction(), glGetDouble(GL_DEPTH_CLEAR_VALUE),
            clipOrigin, clipDepthMode, glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
            state.viewport()[0], state.viewport()[1], state.viewport()[2], state.viewport()[3]);
    }

    private static double percentileMillis(final long[] sorted, final double fraction) {
        if (sorted.length == 0) return 0.0;
        int selected = Math.min(sorted.length - 1,
            Math.max(0, (int) Math.ceil(fraction * sorted.length) - 1));
        return sorted[selected] / 1_000_000.0;
    }

    private static int computeProgram(final String resource) throws IOException {
        int shader = compile(GL_COMPUTE_SHADER, load(resource));
        try { return link(shader); } finally { glDeleteShader(shader); }
    }
    private static int graphicsProgram(final String vertexResource,
        final String fragmentResource) throws IOException {
        int vertex = compile(GL_VERTEX_SHADER, load(vertexResource));
        int fragment = compile(GL_FRAGMENT_SHADER, load(fragmentResource));
        try { return link(vertex, fragment); }
        finally { glDeleteShader(vertex); glDeleteShader(fragment); }
    }
    private static String load(final String resource) throws IOException {
        try (InputStream stream = GpuParticleEngine.class.getResourceAsStream(SHADER_ROOT + resource)) {
            if (stream == null) throw new IOException("Missing shader " + resource);
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (!extensionShaderPath) return source;
            String preamble = "#version 330 core\n";
            if (resource.endsWith(".comp")) preamble +=
                "#extension GL_ARB_compute_shader : require\n"
                    + "#extension GL_ARB_shader_storage_buffer_object : require\n";
            else if (resource.endsWith(".vert")) preamble +=
                "#extension GL_ARB_shader_storage_buffer_object : require\n";
            return source.replace("#version 430 core", preamble.stripTrailing());
        }
    }
    private static int compile(final int type, final String source) {
        int shader = glCreateShader(type); glShaderSource(shader, source); glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader); glDeleteShader(shader);
            throw new IllegalStateException("VFX shader compile failed: " + log);
        }
        return shader;
    }
    private static int link(final int... shaders) {
        int program = glCreateProgram();
        for (int shader : shaders) glAttachShader(program, shader);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program); glDeleteProgram(program);
            throw new IllegalStateException("VFX shader link failed: " + log);
        }
        return program;
    }

    private static void destroyResources() {
        if (updateProgram != 0) glDeleteProgram(updateProgram);
        if (spawnProgram != 0) glDeleteProgram(spawnProgram);
        if (cullProgram != 0) glDeleteProgram(cullProgram);
        if (prepareProgram != 0) glDeleteProgram(prepareProgram);
        if (renderProgram != 0) glDeleteProgram(renderProgram);
        if (particleBuffer != 0) glDeleteBuffers(particleBuffer);
        if (emitterBuffer != 0) glDeleteBuffers(emitterBuffer);
        if (visibleBuffer != 0) glDeleteBuffers(visibleBuffer);
        if (indirectBuffer != 0) glDeleteBuffers(indirectBuffer);
        if (deadListBuffer != 0) glDeleteBuffers(deadListBuffer);
        if (dispatchBuffer != 0) glDeleteBuffers(dispatchBuffer);
        if (statsScratchBuffer != 0) glDeleteBuffers(statsScratchBuffer);
        if (debugParticleBuffer != 0) glDeleteBuffers(debugParticleBuffer);
        if (debugVisibleBuffer != 0) glDeleteBuffers(debugVisibleBuffer);
        for (int aliveBuffer : aliveBuffers) if (aliveBuffer != 0) glDeleteBuffers(aliveBuffer);
        for (int slot = 0; slot < STATS_RING_SIZE; slot++) {
            if (statsFences[slot] != 0L) glDeleteSync(statsFences[slot]);
            if (statsBuffers[slot] != 0) glDeleteBuffers(statsBuffers[slot]);
        }
        if (vertexArray != 0) glDeleteVertexArrays(vertexArray);
        for (int stage = 0; stage < GpuStage.values().length; stage++) {
            if (timeQueries[stage][0] != 0) glDeleteQueries(timeQueries[stage][0]);
            if (timeQueries[stage][1] != 0) glDeleteQueries(timeQueries[stage][1]);
            Arrays.fill(timeQueries[stage], 0);
            Arrays.fill(timeQueryIssued[stage], false);
            timeQueryCursor[stage] = 0;
            GPU_TIMES_NANOS[stage].clear();
        }
        if (sampleQueries[0] != 0) glDeleteQueries(sampleQueries[0]);
        if (sampleQueries[1] != 0) glDeleteQueries(sampleQueries[1]);
        updateProgram = spawnProgram = cullProgram = prepareProgram = renderProgram = 0;
        particleBuffer = emitterBuffer = visibleBuffer = indirectBuffer = deadListBuffer = 0;
        dispatchBuffer = debugParticleBuffer = debugVisibleBuffer = statsScratchBuffer = 0;
        Arrays.fill(aliveBuffers, 0); Arrays.fill(statsBuffers, 0);
        Arrays.fill(statsFences, 0L); Arrays.fill(statsInFlight, false);
        Arrays.fill(sampleQueries, 0); Arrays.fill(sampleQueryIssued, false);
        Arrays.fill(sampleProbeStages, null); Arrays.fill(sampleAutomatic, false);
        Arrays.fill(gpuStageEwmaMillis, 0.0);
        vertexArray = sampleQueryCursor = statsCursor = 0;
        gpuTimeEwmaMillis = 0.0;
    }

    private static long mix64(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL; return value ^ value >>> 31;
    }

    public static final class EffectHandle {
        private final EffectDescriptor descriptor;
        private EffectHandle(final EffectDescriptor descriptor) { this.descriptor = descriptor; }
        public EffectHandle submitLayer(final VisualLayer layer, final EmitterCommand command) {
            if (command != null) GpuParticleEngine.submitLayer(descriptor, layer, List.of(command));
            return this;
        }
        public EffectHandle submitLayer(final VisualLayer layer,
            final List<EmitterCommand> commands) {
            GpuParticleEngine.submitLayer(descriptor, layer, commands); return this;
        }
    }

    public record EffectDescriptor(EffectClass effectClass, long id, Vec3 position,
        float boundsRadius, float temporalImportance) {
        public boolean valid() { return effectClass != null && position != null
            && position.isFinite() && Float.isFinite(boundsRadius) && boundsRadius > 0.0F
            && Float.isFinite(temporalImportance) && temporalImportance > 0.0F; }
    }
    public record EffectSubmission(EffectDescriptor descriptor,
        Map<VisualLayer, List<EmitterCommand>> layers) { }
    public record FrameSubmissions(List<EffectSubmission> effects,
        List<FireFieldSubmission> fireFields) { }
    public record FireFieldPatch(long id, Vec3 position, Vec3 wind, float intensity,
        float heat, float coverage, float smoke, long seed) {
        public boolean valid() { return position != null && position.isFinite()
            && wind != null && wind.isFinite() && Float.isFinite(intensity)
            && Float.isFinite(heat) && Float.isFinite(coverage) && Float.isFinite(smoke); }
    }
    public record FireFieldEmber(long id, Vec3 position, Vec3 velocity,
        float intensity, float size, float importance, long seed) {
        public boolean valid() { return position != null && position.isFinite()
            && velocity != null && velocity.isFinite() && Float.isFinite(intensity)
            && intensity > 0.0F && Float.isFinite(size) && size > 0.0F
            && Float.isFinite(importance) && importance > 0.0F; }
    }
    public record FireFieldCluster(long id, Vec3 position, Vec3 wind, float smoke,
        float heat, float radius, int memberCount, long seed) {
        public boolean valid() { return position != null && position.isFinite()
            && wind != null && wind.isFinite() && Float.isFinite(smoke)
            && Float.isFinite(heat) && Float.isFinite(radius) && radius > 0.0F
            && memberCount > 0; }
    }
    public record FireFieldSubmission(long regionId, Vec3 center, float radius,
        List<FireFieldPatch> patches, List<FireFieldEmber> embers,
        List<FireFieldCluster> clusters) {
        public FireFieldSubmission {
            patches = patches == null ? List.of() : patches.stream()
                .filter(FireFieldPatch::valid).toList();
            embers = embers == null ? List.of() : embers.stream()
                .filter(FireFieldEmber::valid).toList();
            clusters = clusters == null ? List.of() : clusters.stream()
                .filter(FireFieldCluster::valid).toList();
        }
        public boolean valid() { return center != null && center.isFinite()
            && Float.isFinite(radius) && radius > 0.0F && (!patches.isEmpty()
                || !embers.isEmpty() || !clusters.isEmpty()); }
        static FireFieldSubmission merge(final FireFieldSubmission left,
            final FireFieldSubmission right) {
            ArrayList<FireFieldPatch> patches = new ArrayList<>(left.patches); patches.addAll(right.patches);
            ArrayList<FireFieldEmber> embers = new ArrayList<>(left.embers); embers.addAll(right.embers);
            ArrayList<FireFieldCluster> clusters = new ArrayList<>(left.clusters); clusters.addAll(right.clusters);
            return new FireFieldSubmission(left.regionId, left.center.lerp(right.center, 0.5),
                Math.max(left.radius, right.radius), List.copyOf(patches),
                List.copyOf(embers), List.copyOf(clusters));
        }
    }

    public record EmitterCommand(Vec3 position, Vec3 velocity, float scale,
        float lifetimeSeconds, float red, float green, float blue, float opacity,
        float size, float spread, float velocityJitter, int spawnCount, int seed,
        ParticleType type, int flags, float importance) {
        public EmitterCommand(final Vec3 position, final Vec3 velocity, final float scale,
            final float lifetimeSeconds, final float red, final float green,
            final float blue, final float size, final float spread,
            final float velocityJitter, final int spawnCount, final int seed,
            final ParticleType type, final int flags) {
            this(position, velocity, scale, lifetimeSeconds, red, green, blue, 1.0F,
                size, spread, velocityJitter, spawnCount, seed, type, flags, 1.0F);
        }
        public boolean valid() { return position != null && position.isFinite()
            && velocity != null && velocity.isFinite() && Float.isFinite(scale) && scale > 0.0F
            && Float.isFinite(lifetimeSeconds) && lifetimeSeconds > 0.0F
            && Float.isFinite(red) && Float.isFinite(green) && Float.isFinite(blue)
            && Float.isFinite(opacity) && opacity > 0.0F && Float.isFinite(size) && size > 0.0F
            && Float.isFinite(spread) && spread >= 0.0F && Float.isFinite(velocityJitter)
            && velocityJitter >= 0.0F && spawnCount > 0 && type != null
            && Float.isFinite(importance) && importance > 0.0F; }
        EmitterCommand withSpawnCount(final int replacement) {
            return new EmitterCommand(position, velocity, scale, lifetimeSeconds,
                red, green, blue, opacity, size, spread, velocityJitter,
                Math.max(1, replacement), seed, type, flags, importance);
        }
    }

    public record DebugSnapshot(Backend backend, BackendPreference preference,
        Readiness readiness, EffectiveBackend effectiveBackend,
        long activeParticles, long visibleParticles, long culledParticles,
        long submittedParticles, long requestedParticles, long rejectedSpawns,
        long deadSlots, int capacity, long vramBytes, GpuStageTimings gpuTime,
        double adaptiveQuality, int scheduledLayers, int scheduledEmitters,
        long distanceCulled, long sizeCulled, long frustumCulled,
        long statsReadbackSkipped,
        DiagnosticMode diagnosticMode, long diagnosticSamplesPassed,
        FireDebugCounters fire) { }
    public record FireDebugCounters(int clientPatches, int fieldSubmissions,
        int fireSpawned, long fireVisible, int smokeSpawned, long smokeVisible,
        int acceptedPackets, int rejectedPackets, int stalePackets,
        int receivedPatchEntries, int storedPatches) { }
    public record GpuStageTimings(GpuTiming update, GpuTiming spawn,
        GpuTiming cull, GpuTiming raster) { }
    public record GpuTiming(double p50Millis, double p95Millis,
        double p99Millis, double maximumMillis) { }
    private enum ProbeStage { DEPTH_DISABLED, DEPTH_ENABLED }
    private enum GpuStage { UPDATE, SPAWN, CULL, RASTER }
    private record EffectKey(EffectClass effectClass, long id) { }
    private static final class PendingEffect {
        private EffectDescriptor descriptor;
        private final EnumMap<VisualLayer, ArrayList<EmitterCommand>> layers =
            new EnumMap<>(VisualLayer.class);
        private PendingEffect(final EffectDescriptor descriptor) { this.descriptor = descriptor; }
    }

    private record GlState(int program, int vertexArray, int indirectBuffer,
        int dispatchIndirectBuffer, int storageBuffer,
        int[] storageBindings, boolean blend, boolean depthTest, boolean cull,
        boolean depthWrite, int blendSourceRgb, int blendDestinationRgb,
        int blendSourceAlpha, int blendDestinationAlpha, int depthFunction,
        int[] viewport) {
        private static GlState capture() {
            int[] bindings = new int[9];
            for (int index = 0; index < bindings.length; index++)
                bindings[index] = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, index);
            int[] viewport = new int[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            return new GlState(glGetInteger(GL_CURRENT_PROGRAM),
                glGetInteger(GL_VERTEX_ARRAY_BINDING), glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING),
                glGetInteger(GL_DISPATCH_INDIRECT_BUFFER_BINDING),
                glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING),
                bindings, glIsEnabled(GL_BLEND), glIsEnabled(GL_DEPTH_TEST),
                glIsEnabled(GL_CULL_FACE), glGetBoolean(GL_DEPTH_WRITEMASK),
                glGetInteger(GL_BLEND_SRC_RGB), glGetInteger(GL_BLEND_DST_RGB),
                glGetInteger(GL_BLEND_SRC_ALPHA), glGetInteger(GL_BLEND_DST_ALPHA),
                glGetInteger(GL_DEPTH_FUNC), viewport);
        }
        private void restore() {
            glUseProgram(program); glBindVertexArray(vertexArray);
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
            glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, dispatchIndirectBuffer);
            for (int index = 0; index < storageBindings.length; index++)
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, storageBindings[index]);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, storageBuffer);
            setEnabled(GL_BLEND, blend); setEnabled(GL_DEPTH_TEST, depthTest);
            setEnabled(GL_CULL_FACE, cull); glDepthMask(depthWrite); glDepthFunc(depthFunction);
            glBlendFuncSeparate(blendSourceRgb, blendDestinationRgb,
                blendSourceAlpha, blendDestinationAlpha);
        }
        private static void setEnabled(final int capability, final boolean enabled) {
            if (enabled) glEnable(capability); else glDisable(capability);
        }
    }
}
