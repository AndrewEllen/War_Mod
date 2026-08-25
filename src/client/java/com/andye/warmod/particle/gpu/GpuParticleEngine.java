package com.andye.warmod.particle.gpu;

import static org.lwjgl.opengl.GL43C.*;

import com.andye.warmod.WarMod;
import com.andye.warmod.diagnostics.client.ClientPerformanceTelemetry;
import com.andye.warmod.particle.gpu.GpuVfxScheduler.CameraInfo;
import com.andye.warmod.particle.gpu.GpuVfxScheduler.ScheduledFrame;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final String SHADER_ROOT = "/assets/war_mod/shaders/gpu_particles/";
    private static final LinkedHashMap<EffectKey, PendingEffect> PENDING_EFFECTS =
        new LinkedHashMap<>();
    private static final LinkedHashMap<Long, FireFieldSubmission> PENDING_FIRE_FIELDS =
        new LinkedHashMap<>();
    private static final ByteBuffer EMITTER_STAGING = MemoryUtil.memAlloc(
        MAX_EMITTERS_PER_FRAME * EMITTER_STRIDE);

    private static volatile Backend backend = Backend.UNINITIALIZED;
    private static boolean registered, resetRequested, extensionShaderPath;
    private static int particleBuffer, emitterBuffer, visibleBuffer, indirectBuffer;
    private static int allocationBuffer, vertexArray;
    private static int updateProgram, spawnProgram, cullProgram, renderProgram;
    private static final int[] timeQueries = new int[2];
    private static final boolean[] timeQueryIssued = new boolean[2];
    private static final int[][] visibilityQueries = new int[TYPE_COUNT][2];
    private static final boolean[][] visibilityQueryIssued = new boolean[TYPE_COUNT][2];
    private static final int[] visibilityQueryCursor = new int[TYPE_COUNT];
    private static final long[] visibleByType = new long[TYPE_COUNT];
    private static final long[] activeByType = new long[TYPE_COUNT];
    private static final int[] spawnedByType = new int[TYPE_COUNT];
    private static final ArrayDeque<Long> GPU_TIMES_NANOS = new ArrayDeque<>(120);
    private static int timeQueryCursor;
    private static long previousFrameNanos, submittedParticles, frameSequence;
    private static double adaptiveQuality = 1.0, gpuTimeEwmaMillis;
    private static int scheduledLayerCount, scheduledEmitterCount;
    private static int clientFirePatches, fireFieldSubmissions;
    private static int acceptedFirePackets, rejectedFirePackets, staleFirePackets;

    private GpuParticleEngine() { }

    public static synchronized void register() {
        if (registered) return;
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(GpuParticleEngine::render);
        registered = true;
    }

    public static Backend backend() { return backend; }
    public static boolean isGpuActive() { return backend == Backend.GPU_COMPUTE; }
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
        if (field == null || !field.valid()) return;
        PENDING_FIRE_FIELDS.merge(field.regionId(), field, FireFieldSubmission::merge);
        fireFieldSubmissions++;
    }

    public static synchronized void recordClientFirePatches(final int count) {
        clientFirePatches = Math.max(0, count);
        fireFieldSubmissions = 0;
    }

    public static synchronized void recordFirePacket(final boolean accepted,
        final boolean stale) {
        if (accepted) acceptedFirePackets++;
        else { rejectedFirePackets++; if (stale) staleFirePackets++; }
    }

    public static synchronized void clearLevel() {
        PENDING_EFFECTS.clear(); PENDING_FIRE_FIELDS.clear();
        GpuVfxScheduler.clear(); resetRequested = true;
        Arrays.fill(activeByType, 0L); Arrays.fill(visibleByType, 0L);
        Arrays.fill(spawnedByType, 0); clientFirePatches = fireFieldSubmissions = 0;
        acceptedFirePackets = rejectedFirePackets = staleFirePackets = 0;
    }

    public static synchronized DebugSnapshot debugSnapshot() {
        long active = Arrays.stream(activeByType).sum();
        long visible = Math.min(active, Arrays.stream(visibleByType).sum());
        long vram = isGpuActive() ? (long) PARTICLE_CAPACITY * PARTICLE_STRIDE
            + (long) PARTICLE_CAPACITY * TYPE_COUNT * Integer.BYTES
            + (long) MAX_EMITTERS_PER_FRAME * EMITTER_STRIDE
            + (long) TYPE_COUNT * INDIRECT_COMMAND_STRIDE + 4L : 0L;
        FireDebugCounters fire = new FireDebugCounters(clientFirePatches,
            fireFieldSubmissions, spawnedByType[ParticleType.FIRE.shaderId],
            visibleByType[ParticleType.FIRE.shaderId],
            spawnedByType[ParticleType.SMOKE.shaderId],
            visibleByType[ParticleType.SMOKE.shaderId], acceptedFirePackets,
            rejectedFirePackets, staleFirePackets);
        return new DebugSnapshot(backend, active, visible, Math.max(0L, active - visible),
            submittedParticles, PARTICLE_CAPACITY, vram, gpuTiming(), adaptiveQuality,
            scheduledLayerCount, scheduledEmitterCount, fire);
    }

    private static synchronized void submitLayer(final EffectDescriptor descriptor,
        final VisualLayer layer, final List<EmitterCommand> commands) {
        if (descriptor == null || !descriptor.valid() || layer == null
            || commands == null || commands.isEmpty()) return;
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
        if (backend == Backend.UNINITIALIZED) initialize();
        FrameSubmissions submissions = drainSubmissions();
        if (backend != Backend.GPU_COMPUTE) return;
        if (resetRequested) { clearParticleStorage(); resetRequested = false; }
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (camera == null || camera.pos == null || camera.projectionMatrix == null
            || camera.viewRotationMatrix == null) return;
        long now = System.nanoTime();
        float deltaSeconds = previousFrameNanos == 0L ? 1.0F / 60.0F
            : Math.min(0.10F, Math.max(0.001F,
                (now - previousFrameNanos) / 1_000_000_000.0F));
        previousFrameNanos = now; frameSequence++;

        GlState state = GlState.capture();
        boolean gpuTimerActive = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Matrix4f viewProjection = new Matrix4f(camera.projectionMatrix)
                .mul(camera.viewRotationMatrix);
            IntBuffer viewport = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, viewport);
            int viewportWidth = Math.max(1, viewport.get(2));
            int viewportHeight = Math.max(1, viewport.get(3));
            CameraInfo cameraInfo = new CameraInfo(camera.pos, viewProjection,
                Math.max(0.01F, Math.abs(camera.projectionMatrix.m11())),
                viewportWidth, viewportHeight);
            ScheduledFrame scheduled = GpuVfxScheduler.schedule(submissions,
                cameraInfo, adaptiveQuality, frameSequence);
            scheduledLayerCount = scheduled.visibleLayerCount();
            scheduledEmitterCount = scheduled.emitters().size();
            int spawned = uploadEmitters(scheduled.emitters(), deltaSeconds);
            submittedParticles += spawned; updateActiveEstimates(deltaSeconds);

            int query = beginGpuTimer(); gpuTimerActive = query != 0;
            bindStorageBuffers(); dispatchUpdate(deltaSeconds);
            if (!scheduled.emitters().isEmpty()) dispatchSpawn(scheduled.emitters().size());
            resetIndirectCommands(stack);
            dispatchCull(camera.pos, viewProjection, viewportHeight,
                cameraInfo.projectionScale(), stack);
            draw(camera, viewProjection, stack);
            if (gpuTimerActive) { glEndQuery(GL_TIME_ELAPSED); gpuTimerActive = false; }
        } catch (RuntimeException exception) {
            if (gpuTimerActive) glEndQuery(GL_TIME_ELAPSED);
            WarMod.LOGGER.error("GPU VFX backend failed; returning to packed fallback", exception);
            destroyResources(); backend = Backend.CPU_FALLBACK;
        } finally {
            state.restore();
            ClientPerformanceTelemetry.recordGpuEngineCpuNanos(
                Math.max(0L, System.nanoTime() - cpuStarted));
        }
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
            if (backendName == null || !backendName.toLowerCase().contains("opengl")
                || (!coreCompute && !extensionCompute) || !indirect) {
                backend = Backend.CPU_FALLBACK;
                WarMod.LOGGER.info("War Mod GPU VFX unavailable: backend={}, core43={}, "
                    + "extensionCompute={}, indirect={}; using packed fallback",
                    backendName, coreCompute, extensionCompute, indirect);
                return;
            }
            extensionShaderPath = !coreCompute;
            updateProgram = computeProgram("update.comp");
            spawnProgram = computeProgram("spawn.comp");
            cullProgram = computeProgram("cull.comp");
            renderProgram = graphicsProgram("particle.vert", "particle.frag");
            particleBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
                (long) PARTICLE_CAPACITY * PARTICLE_STRIDE, GL_DYNAMIC_DRAW);
            emitterBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
                (long) MAX_EMITTERS_PER_FRAME * EMITTER_STRIDE, GL_STREAM_DRAW);
            visibleBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
                (long) PARTICLE_CAPACITY * TYPE_COUNT * Integer.BYTES, GL_DYNAMIC_DRAW);
            indirectBuffer = createBuffer(GL_DRAW_INDIRECT_BUFFER,
                (long) TYPE_COUNT * INDIRECT_COMMAND_STRIDE, GL_DYNAMIC_DRAW);
            allocationBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER, 4L, GL_DYNAMIC_DRAW);
            vertexArray = glGenVertexArrays();
            timeQueries[0] = glGenQueries(); timeQueries[1] = glGenQueries();
            for (int type = 0; type < TYPE_COUNT; type++) {
                visibilityQueries[type][0] = glGenQueries();
                visibilityQueries[type][1] = glGenQueries();
            }
            clearParticleStorage(); backend = Backend.GPU_COMPUTE;
            WarMod.LOGGER.info("War Mod effect-aware GPU VFX enabled: {} particles, "
                + "{} semantic emitter slots, extensionPath={}",
                PARTICLE_CAPACITY, MAX_EMITTERS_PER_FRAME, !coreCompute);
        } catch (RuntimeException | IOException exception) {
            WarMod.LOGGER.warn("GPU VFX unavailable; using packed fallback", exception);
            destroyResources(); backend = Backend.CPU_FALLBACK;
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
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, allocationBuffer);
            glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, stack.calloc(4));
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }
        previousFrameNanos = 0L;
        Arrays.fill(activeByType, 0L); Arrays.fill(visibleByType, 0L);
    }

    private static int uploadEmitters(final List<EmitterCommand> emitters,
        final float deltaSeconds) {
        Arrays.fill(spawnedByType, 0);
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
            totalSpawned += spawnCount; spawnedByType[emitter.type().shaderId] += spawnCount;
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

    private static void updateActiveEstimates(final float deltaSeconds) {
        float[] typicalLifetime = {1.15F, 5.0F, 0.65F, 2.6F, 8.0F, 3.2F, 5.8F};
        for (int type = 0; type < TYPE_COUNT; type++) {
            long expired = Math.max(activeByType[type] > 0L ? 1L : 0L,
                (long) (activeByType[type] * deltaSeconds
                    / Math.max(0.1F, typicalLifetime[type])));
            activeByType[type] = Math.min(PARTICLE_CAPACITY,
                Math.max(0L, activeByType[type] - expired) + spawnedByType[type]);
        }
        long total = Arrays.stream(activeByType).sum();
        if (total <= PARTICLE_CAPACITY) return;
        double scale = PARTICLE_CAPACITY / (double) total;
        for (int type = 0; type < TYPE_COUNT; type++)
            activeByType[type] = Math.round(activeByType[type] * scale);
    }

    private static void putVec4(final ByteBuffer buffer, final double x, final double y,
        final double z, final double w) {
        buffer.putFloat((float) x).putFloat((float) y).putFloat((float) z).putFloat((float) w);
    }

    private static void bindStorageBuffers() {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, particleBuffer);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, emitterBuffer);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, visibleBuffer);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, indirectBuffer);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, allocationBuffer);
    }

    private static void dispatchUpdate(final float deltaSeconds) {
        glUseProgram(updateProgram);
        glUniform1ui(glGetUniformLocation(updateProgram, "particleCapacity"), PARTICLE_CAPACITY);
        glUniform1f(glGetUniformLocation(updateProgram, "deltaSeconds"), deltaSeconds);
        glDispatchCompute((PARTICLE_CAPACITY + 255) / 256, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private static void dispatchSpawn(final int emitterCount) {
        glUseProgram(spawnProgram);
        glUniform1ui(glGetUniformLocation(spawnProgram, "particleCapacity"), PARTICLE_CAPACITY);
        glUniform1ui(glGetUniformLocation(spawnProgram, "emitterCount"), emitterCount);
        glDispatchCompute(emitterCount, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private static void resetIndirectCommands(final MemoryStack stack) {
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
        glUniform1ui(glGetUniformLocation(cullProgram, "frameSequence"), (int) frameSequence);
        glUniform1f(glGetUniformLocation(cullProgram, "viewportHeight"), viewportHeight);
        glUniform1f(glGetUniformLocation(cullProgram, "projectionScale"), projectionScale);
        glUniform3f(glGetUniformLocation(cullProgram, "cameraPosition"),
            (float) camera.x, (float) camera.y, (float) camera.z);
        uniformMatrix(cullProgram, "viewProjection", viewProjection, stack);
        glDispatchCompute((PARTICLE_CAPACITY + 255) / 256, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
    }

    private static void draw(final CameraRenderState camera,
        final Matrix4f viewProjection, final MemoryStack stack) {
        Quaternionf orientation = camera.orientation == null
            ? new Quaternionf() : new Quaternionf(camera.orientation);
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(orientation);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(orientation);
        glUseProgram(renderProgram); uniformMatrix(renderProgram, "viewProjection", viewProjection, stack);
        glUniform3f(glGetUniformLocation(renderProgram, "cameraPosition"),
            (float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z);
        glUniform3f(glGetUniformLocation(renderProgram, "cameraRight"), right.x, right.y, right.z);
        glUniform3f(glGetUniformLocation(renderProgram, "cameraUp"), up.x, up.y, up.z);
        glBindVertexArray(vertexArray); glEnable(GL_BLEND); glEnable(GL_DEPTH_TEST);
        glDepthMask(false); glDisable(GL_CULL_FACE); glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
        int visibleBaseUniform = glGetUniformLocation(renderProgram, "visibleBase");
        for (ParticleType type : ParticleType.values()) {
            if (type == ParticleType.FIRE || type == ParticleType.EMBER
                || type == ParticleType.EXPLOSION_FIRE) glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            else glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glUniform1ui(visibleBaseUniform, type.shaderId * PARTICLE_CAPACITY);
            boolean visibilityQuery = beginVisibilityQuery(type.shaderId);
            try { glDrawArraysIndirect(GL_TRIANGLE_STRIP,
                (long) type.shaderId * INDIRECT_COMMAND_STRIDE); }
            finally { if (visibilityQuery) glEndQuery(GL_PRIMITIVES_GENERATED); }
        }
    }

    private static void uniformMatrix(final int program, final String name,
        final Matrix4f matrix, final MemoryStack stack) {
        FloatBuffer values = stack.mallocFloat(16); matrix.get(values);
        glUniformMatrix4fv(glGetUniformLocation(program, name), false, values);
    }

    private static int beginGpuTimer() {
        int slot = timeQueryCursor, query = timeQueries[slot];
        if (query == 0) return 0;
        if (timeQueryIssued[slot]) {
            if (glGetQueryObjecti(query, GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) return 0;
            long nanos = glGetQueryObjecti64(query, GL_QUERY_RESULT);
            synchronized (GpuParticleEngine.class) {
                if (GPU_TIMES_NANOS.size() == 120) GPU_TIMES_NANOS.removeFirst();
                GPU_TIMES_NANOS.addLast(Math.max(0L, nanos));
                double millis = Math.max(0.0, nanos / 1_000_000.0);
                gpuTimeEwmaMillis = gpuTimeEwmaMillis == 0.0 ? millis
                    : gpuTimeEwmaMillis * 0.90 + millis * 0.10;
                if (gpuTimeEwmaMillis > 2.35) adaptiveQuality -= 0.018;
                else if (gpuTimeEwmaMillis < 1.55) adaptiveQuality += 0.009;
                adaptiveQuality = Mth.clamp(adaptiveQuality, 0.22, 1.35);
            }
            timeQueryIssued[slot] = false;
        }
        glBeginQuery(GL_TIME_ELAPSED, query); timeQueryIssued[slot] = true;
        timeQueryCursor = (slot + 1) & 1; return query;
    }

    private static boolean beginVisibilityQuery(final int type) {
        int slot = visibilityQueryCursor[type], query = visibilityQueries[type][slot];
        if (query == 0) return false;
        if (visibilityQueryIssued[type][slot]) {
            if (glGetQueryObjecti(query, GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) return false;
            long primitives = glGetQueryObjecti64(query, GL_QUERY_RESULT);
            visibleByType[type] = Math.min(PARTICLE_CAPACITY, Math.max(0L, primitives / 2L));
            visibilityQueryIssued[type][slot] = false;
        }
        glBeginQuery(GL_PRIMITIVES_GENERATED, query);
        visibilityQueryIssued[type][slot] = true;
        visibilityQueryCursor[type] = (slot + 1) & 1; return true;
    }

    private static GpuTiming gpuTiming() {
        long[] sorted = new long[GPU_TIMES_NANOS.size()]; int index = 0;
        for (long value : GPU_TIMES_NANOS) sorted[index++] = value;
        Arrays.sort(sorted);
        return new GpuTiming(percentileMillis(sorted, 0.50), percentileMillis(sorted, 0.95),
            percentileMillis(sorted, 0.99), sorted.length == 0 ? 0.0
                : sorted[sorted.length - 1] / 1_000_000.0);
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
        if (renderProgram != 0) glDeleteProgram(renderProgram);
        if (particleBuffer != 0) glDeleteBuffers(particleBuffer);
        if (emitterBuffer != 0) glDeleteBuffers(emitterBuffer);
        if (visibleBuffer != 0) glDeleteBuffers(visibleBuffer);
        if (indirectBuffer != 0) glDeleteBuffers(indirectBuffer);
        if (allocationBuffer != 0) glDeleteBuffers(allocationBuffer);
        if (vertexArray != 0) glDeleteVertexArrays(vertexArray);
        if (timeQueries[0] != 0) glDeleteQueries(timeQueries[0]);
        if (timeQueries[1] != 0) glDeleteQueries(timeQueries[1]);
        for (int type = 0; type < TYPE_COUNT; type++) {
            if (visibilityQueries[type][0] != 0) glDeleteQueries(visibilityQueries[type][0]);
            if (visibilityQueries[type][1] != 0) glDeleteQueries(visibilityQueries[type][1]);
            Arrays.fill(visibilityQueries[type], 0);
            Arrays.fill(visibilityQueryIssued[type], false); visibilityQueryCursor[type] = 0;
        }
        updateProgram = spawnProgram = cullProgram = renderProgram = 0;
        particleBuffer = emitterBuffer = visibleBuffer = indirectBuffer = allocationBuffer = 0;
        vertexArray = 0; Arrays.fill(timeQueries, 0); Arrays.fill(timeQueryIssued, false);
        GPU_TIMES_NANOS.clear();
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

    public record DebugSnapshot(Backend backend, long activeParticles,
        long visibleParticles, long culledParticles, long submittedParticles,
        int capacity, long vramBytes, GpuTiming gpuTime, double adaptiveQuality,
        int scheduledLayers, int scheduledEmitters, FireDebugCounters fire) { }
    public record FireDebugCounters(int clientPatches, int fieldSubmissions,
        int fireSpawned, long fireVisible, int smokeSpawned, long smokeVisible,
        int acceptedPackets, int rejectedPackets, int stalePackets) { }
    public record GpuTiming(double p50Millis, double p95Millis,
        double p99Millis, double maximumMillis) { }
    private record EffectKey(EffectClass effectClass, long id) { }
    private static final class PendingEffect {
        private EffectDescriptor descriptor;
        private final EnumMap<VisualLayer, ArrayList<EmitterCommand>> layers =
            new EnumMap<>(VisualLayer.class);
        private PendingEffect(final EffectDescriptor descriptor) { this.descriptor = descriptor; }
    }

    private record GlState(int program, int vertexArray, int indirectBuffer,
        int[] storageBindings, boolean blend, boolean depthTest, boolean cull,
        boolean depthWrite, int blendSourceRgb, int blendDestinationRgb,
        int blendSourceAlpha, int blendDestinationAlpha) {
        private static GlState capture() {
            int[] bindings = new int[5];
            for (int index = 0; index < bindings.length; index++)
                bindings[index] = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, index);
            return new GlState(glGetInteger(GL_CURRENT_PROGRAM),
                glGetInteger(GL_VERTEX_ARRAY_BINDING), glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING),
                bindings, glIsEnabled(GL_BLEND), glIsEnabled(GL_DEPTH_TEST),
                glIsEnabled(GL_CULL_FACE), glGetBoolean(GL_DEPTH_WRITEMASK),
                glGetInteger(GL_BLEND_SRC_RGB), glGetInteger(GL_BLEND_DST_RGB),
                glGetInteger(GL_BLEND_SRC_ALPHA), glGetInteger(GL_BLEND_DST_ALPHA));
        }
        private void restore() {
            glUseProgram(program); glBindVertexArray(vertexArray);
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
            for (int index = 0; index < storageBindings.length; index++)
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, storageBindings[index]);
            setEnabled(GL_BLEND, blend); setEnabled(GL_DEPTH_TEST, depthTest);
            setEnabled(GL_CULL_FACE, cull); glDepthMask(depthWrite);
            glBlendFuncSeparate(blendSourceRgb, blendDestinationRgb,
                blendSourceAlpha, blendDestinationAlpha);
        }
        private static void setEnabled(final int capability, final boolean enabled) {
            if (enabled) glEnable(capability); else glDisable(capability);
        }
    }
}
