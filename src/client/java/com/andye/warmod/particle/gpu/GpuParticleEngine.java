package com.andye.warmod.particle.gpu;

import static org.lwjgl.opengl.GL43C.*;

import com.andye.warmod.WarMod;
import com.andye.warmod.diagnostics.client.ClientPerformanceTelemetry;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Shared semantic-emitter particle backend. On OpenGL 4.3 it keeps particle
 * state in SSBOs, updates/spawns/culls with compute shaders, compacts visible
 * IDs and submits one indirect instanced billboard draw. Other backends retain
 * the existing CPU renderers as a compatibility fallback.
 */
public final class GpuParticleEngine {
    public enum Backend { UNINITIALIZED, GPU_COMPUTE, CPU_FALLBACK }
    public enum ParticleType {
        FIRE(0), SMOKE(1), EMBER(2), EXPLOSION_FIRE(3), EXPLOSION_SMOKE(4),
        GROUND_DUST(5), CURTAIN(6);
        private final int shaderId;
        ParticleType(final int shaderId) { this.shaderId = shaderId; }
    }

    private static final int PARTICLE_CAPACITY = 262_144;
    private static final int MAX_EMITTERS_PER_FRAME = 4_096;
    private static final int PARTICLE_STRIDE = 64;
    private static final int EMITTER_STRIDE = 80;
    private static final String SHADER_ROOT =
        "/assets/war_mod/shaders/gpu_particles/";
    private static final ArrayDeque<EmitterCommand> PENDING_EMITTERS = new ArrayDeque<>();
    private static final ByteBuffer EMITTER_STAGING = MemoryUtil.memAlloc(
        MAX_EMITTERS_PER_FRAME * EMITTER_STRIDE);

    private static volatile Backend backend = Backend.UNINITIALIZED;
    private static boolean registered;
    private static boolean resetRequested;
    private static int particleBuffer;
    private static int emitterBuffer;
    private static int visibleBuffer;
    private static int indirectBuffer;
    private static int allocationBuffer;
    private static int vertexArray;
    private static int updateProgram;
    private static int spawnProgram;
    private static int cullProgram;
    private static int renderProgram;
    private static final int[] timeQueries = new int[2];
    private static final boolean[] timeQueryIssued = new boolean[2];
    private static final int[] visibilityQueries = new int[2];
    private static final boolean[] visibilityQueryIssued = new boolean[2];
    private static final ArrayDeque<Long> GPU_TIMES_NANOS = new ArrayDeque<>(120);
    private static int timeQueryCursor;
    private static int visibilityQueryCursor;
    private static long previousFrameNanos;
    private static long submittedParticles;
    private static long activeEstimate;
    private static long culledEstimate;
    private static int framesWithoutEmitters;
    private static long frameSequence;

    private GpuParticleEngine() { }

    public static synchronized void register() {
        if (registered) return;
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(GpuParticleEngine::render);
        registered = true;
    }

    public static Backend backend() { return backend; }
    public static boolean isGpuActive() { return backend == Backend.GPU_COMPUTE; }

    public static synchronized void submit(final EmitterCommand command) {
        if (command == null || !command.valid()) return;
        if (PENDING_EMITTERS.size() >= MAX_EMITTERS_PER_FRAME) return;
        PENDING_EMITTERS.addLast(command);
    }

    /** Defers GL destruction to the render thread and drops transient commands now. */
    public static synchronized void clearLevel() {
        PENDING_EMITTERS.clear();
        resetRequested = true;
        activeEstimate = 0L;
        culledEstimate = 0L;
        framesWithoutEmitters = 0;
    }

    public static synchronized DebugSnapshot debugSnapshot() {
        return new DebugSnapshot(backend, activeEstimate,
            Math.max(0L, activeEstimate - culledEstimate), culledEstimate,
            submittedParticles, PARTICLE_CAPACITY,
            isGpuActive() ? (long) PARTICLE_CAPACITY * PARTICLE_STRIDE
                + (long) PARTICLE_CAPACITY * Integer.BYTES
                + (long) MAX_EMITTERS_PER_FRAME * EMITTER_STRIDE + 20L : 0L,
            gpuTiming());
    }

    private static void render(final LevelRenderContext context) {
        long cpuStarted = System.nanoTime();
        RenderSystem.assertOnRenderThread();
        if (backend == Backend.UNINITIALIZED) initialize();
        List<EmitterCommand> emitters = drainEmitters();
        if (backend != Backend.GPU_COMPUTE) return;
        if (resetRequested) {
            clearParticleStorage();
            resetRequested = false;
        }
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (camera == null || camera.pos == null || camera.projectionMatrix == null
            || camera.viewRotationMatrix == null) return;
        long now = System.nanoTime();
        float deltaSeconds = previousFrameNanos == 0L ? 1.0F / 60.0F
            : Math.min(0.10F, Math.max(0.001F,
                (now - previousFrameNanos) / 1_000_000_000.0F));
        previousFrameNanos = now;
        int spawned = uploadEmitters(emitters, deltaSeconds);
        frameSequence++;
        submittedParticles += spawned;
        if (spawned == 0) framesWithoutEmitters++; else framesWithoutEmitters = 0;
        activeEstimate = Math.min(PARTICLE_CAPACITY,
            Math.max(0L, activeEstimate - Math.max(1L,
                (long) (activeEstimate * deltaSeconds / 3.8))) + spawned);
        if (framesWithoutEmitters > 600) activeEstimate = 0L;

        GlState state = GlState.capture();
        boolean gpuTimerActive = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int query = beginGpuTimer();
            gpuTimerActive = query != 0;
            bindStorageBuffers();
            dispatchUpdate(deltaSeconds);
            if (!emitters.isEmpty()) dispatchSpawn(emitters.size());
            resetIndirectCommand(stack);
            Matrix4f viewProjection = new Matrix4f(camera.projectionMatrix)
                .mul(camera.viewRotationMatrix);
            dispatchCull(camera.pos, viewProjection, stack);
            draw(camera, viewProjection, stack);
            if (gpuTimerActive) {
                glEndQuery(GL_TIME_ELAPSED);
                gpuTimerActive = false;
            }
        } catch (RuntimeException exception) {
            if (gpuTimerActive) glEndQuery(GL_TIME_ELAPSED);
            WarMod.LOGGER.error("GPU particle backend failed; returning to CPU fallback",
                exception);
            destroyResources();
            backend = Backend.CPU_FALLBACK;
        } finally {
            state.restore();
            ClientPerformanceTelemetry.recordGpuEngineCpuNanos(
                Math.max(0L, System.nanoTime() - cpuStarted));
        }
    }

    private static synchronized List<EmitterCommand> drainEmitters() {
        if (PENDING_EMITTERS.isEmpty()) return List.of();
        ArrayList<EmitterCommand> result = new ArrayList<>(PENDING_EMITTERS.size());
        while (!PENDING_EMITTERS.isEmpty()) result.add(PENDING_EMITTERS.removeFirst());
        return result;
    }

    private static void initialize() {
        try {
            String backendName = RenderSystem.getDevice().getDeviceInfo().backendName();
            if (backendName == null || !backendName.toLowerCase().contains("opengl")
                || !GL.getCapabilities().OpenGL43
                || !RenderSystem.getDevice().getDeviceInfo().features().drawIndirect()) {
                backend = Backend.CPU_FALLBACK;
                return;
            }
            updateProgram = computeProgram("update.comp");
            spawnProgram = computeProgram("spawn.comp");
            cullProgram = computeProgram("cull.comp");
            renderProgram = graphicsProgram("particle.vert", "particle.frag");
            particleBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
                (long) PARTICLE_CAPACITY * PARTICLE_STRIDE, GL_DYNAMIC_DRAW);
            emitterBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
                (long) MAX_EMITTERS_PER_FRAME * EMITTER_STRIDE, GL_STREAM_DRAW);
            visibleBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER,
                (long) PARTICLE_CAPACITY * Integer.BYTES, GL_DYNAMIC_DRAW);
            indirectBuffer = createBuffer(GL_DRAW_INDIRECT_BUFFER, 16L, GL_DYNAMIC_DRAW);
            allocationBuffer = createBuffer(GL_SHADER_STORAGE_BUFFER, 4L, GL_DYNAMIC_DRAW);
            vertexArray = glGenVertexArrays();
            timeQueries[0] = glGenQueries();
            timeQueries[1] = glGenQueries();
            visibilityQueries[0] = glGenQueries();
            visibilityQueries[1] = glGenQueries();
            clearParticleStorage();
            backend = Backend.GPU_COMPUTE;
            WarMod.LOGGER.info("War Mod GPU particle backend enabled: {} particles",
                PARTICLE_CAPACITY);
        } catch (RuntimeException | IOException exception) {
            WarMod.LOGGER.warn("GPU compute particles unavailable; using CPU fallback",
                exception);
            destroyResources();
            backend = Backend.CPU_FALLBACK;
        }
    }

    private static int createBuffer(final int target, final long bytes, final int usage) {
        int buffer = glGenBuffers();
        glBindBuffer(target, buffer);
        glBufferData(target, bytes, usage);
        glBindBuffer(target, 0);
        return buffer;
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
    }

    private static int uploadEmitters(final List<EmitterCommand> emitters,
        final float deltaSeconds) {
        if (emitters.isEmpty()) return 0;
        EMITTER_STAGING.clear();
        int totalSpawned = 0;
        for (EmitterCommand emitter : emitters) {
            putVec4(EMITTER_STAGING, emitter.position.x, emitter.position.y,
                emitter.position.z, emitter.scale);
            putVec4(EMITTER_STAGING, emitter.velocity.x, emitter.velocity.y,
                emitter.velocity.z, emitter.lifetimeSeconds);
            putVec4(EMITTER_STAGING, emitter.red, emitter.green, emitter.blue, 1.0F);
            putVec4(EMITTER_STAGING, emitter.size, emitter.spread,
                emitter.velocityJitter, emitter.type.shaderId);
            int spawnCount = frameSpawnCount(emitter, deltaSeconds);
            totalSpawned += spawnCount;
            EMITTER_STAGING.putInt(spawnCount).putInt(emitter.seed)
                .putInt(emitter.type.shaderId).putInt(emitter.flags);
        }
        EMITTER_STAGING.flip();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, emitterBuffer);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, EMITTER_STAGING);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        return totalSpawned;
    }

    private static int frameSpawnCount(final EmitterCommand emitter,
        final float deltaSeconds) {
        double expected = Math.min(64.0, emitter.spawnCount * deltaSeconds);
        int whole = (int) Math.floor(expected);
        double fraction = expected - whole;
        long mixed = emitter.seed * 0x9E3779B97F4A7C15L
            ^ frameSequence * 0xD1B54A32D192ED03L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        double unit = (mixed >>> 11) * 0x1.0p-53;
        return whole + (unit < fraction ? 1 : 0);
    }

    private static void putVec4(final ByteBuffer buffer, final double x, final double y,
        final double z, final double w) {
        buffer.putFloat((float) x).putFloat((float) y).putFloat((float) z)
            .putFloat((float) w);
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
        glUniform1ui(glGetUniformLocation(updateProgram, "particleCapacity"),
            PARTICLE_CAPACITY);
        glUniform1f(glGetUniformLocation(updateProgram, "deltaSeconds"), deltaSeconds);
        glDispatchCompute((PARTICLE_CAPACITY + 255) / 256, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private static void dispatchSpawn(final int emitterCount) {
        glUseProgram(spawnProgram);
        glUniform1ui(glGetUniformLocation(spawnProgram, "particleCapacity"),
            PARTICLE_CAPACITY);
        glUniform1ui(glGetUniformLocation(spawnProgram, "emitterCount"), emitterCount);
        glDispatchCompute(emitterCount, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private static void resetIndirectCommand(final MemoryStack stack) {
        ByteBuffer command = stack.malloc(16);
        command.putInt(4).putInt(0).putInt(0).putInt(0).flip();
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
        glBufferSubData(GL_DRAW_INDIRECT_BUFFER, 0L, command);
    }

    private static void dispatchCull(final Vec3 camera, final Matrix4f viewProjection,
        final MemoryStack stack) {
        glUseProgram(cullProgram);
        glUniform1ui(glGetUniformLocation(cullProgram, "particleCapacity"),
            PARTICLE_CAPACITY);
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
        glUseProgram(renderProgram);
        uniformMatrix(renderProgram, "viewProjection", viewProjection, stack);
        glUniform3f(glGetUniformLocation(renderProgram, "cameraPosition"),
            (float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z);
        glUniform3f(glGetUniformLocation(renderProgram, "cameraRight"),
            right.x, right.y, right.z);
        glUniform3f(glGetUniformLocation(renderProgram, "cameraUp"), up.x, up.y, up.z);
        glBindVertexArray(vertexArray);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
        boolean visibilityTimer = beginVisibilityQuery();
        try {
            glDrawArraysIndirect(GL_TRIANGLE_STRIP, 0L);
        } finally {
            if (visibilityTimer) glEndQuery(GL_PRIMITIVES_GENERATED);
        }
    }

    private static void uniformMatrix(final int program, final String name,
        final Matrix4f matrix, final MemoryStack stack) {
        FloatBuffer values = stack.mallocFloat(16);
        matrix.get(values);
        glUniformMatrix4fv(glGetUniformLocation(program, name), false, values);
    }

    private static int beginGpuTimer() {
        int slot = timeQueryCursor;
        int query = timeQueries[slot];
        if (query == 0) return 0;
        if (timeQueryIssued[slot]) {
            if (glGetQueryObjecti(query, GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) return 0;
            long nanos = glGetQueryObjecti64(query, GL_QUERY_RESULT);
            synchronized (GpuParticleEngine.class) {
                if (GPU_TIMES_NANOS.size() == 120) GPU_TIMES_NANOS.removeFirst();
                GPU_TIMES_NANOS.addLast(Math.max(0L, nanos));
            }
            timeQueryIssued[slot] = false;
        }
        glBeginQuery(GL_TIME_ELAPSED, query);
        timeQueryIssued[slot] = true;
        timeQueryCursor = (slot + 1) & 1;
        return query;
    }

    private static GpuTiming gpuTiming() {
        long[] sorted = new long[GPU_TIMES_NANOS.size()];
        int index = 0;
        for (long value : GPU_TIMES_NANOS) sorted[index++] = value;
        Arrays.sort(sorted);
        return new GpuTiming(percentileMillis(sorted, 0.50),
            percentileMillis(sorted, 0.95), percentileMillis(sorted, 0.99),
            sorted.length == 0 ? 0.0 : sorted[sorted.length - 1] / 1_000_000.0);
    }

    private static boolean beginVisibilityQuery() {
        int slot = visibilityQueryCursor;
        int query = visibilityQueries[slot];
        if (query == 0) return false;
        if (visibilityQueryIssued[slot]) {
            if (glGetQueryObjecti(query, GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) return false;
            long primitives = glGetQueryObjecti64(query, GL_QUERY_RESULT);
            long visible = Math.min(PARTICLE_CAPACITY, Math.max(0L, primitives / 2L));
            culledEstimate = Math.max(0L, activeEstimate - visible);
            visibilityQueryIssued[slot] = false;
        }
        glBeginQuery(GL_PRIMITIVES_GENERATED, query);
        visibilityQueryIssued[slot] = true;
        visibilityQueryCursor = (slot + 1) & 1;
        return true;
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
        try (InputStream stream = GpuParticleEngine.class.getResourceAsStream(
            SHADER_ROOT + resource)) {
            if (stream == null) throw new IOException("Missing shader " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int compile(final int type, final String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Particle shader compile failed: " + log);
        }
        return shader;
    }

    private static int link(final int... shaders) {
        int program = glCreateProgram();
        for (int shader : shaders) glAttachShader(program, shader);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Particle shader link failed: " + log);
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
        if (visibilityQueries[0] != 0) glDeleteQueries(visibilityQueries[0]);
        if (visibilityQueries[1] != 0) glDeleteQueries(visibilityQueries[1]);
        updateProgram = spawnProgram = cullProgram = renderProgram = 0;
        particleBuffer = emitterBuffer = visibleBuffer = indirectBuffer = allocationBuffer = 0;
        vertexArray = 0;
        timeQueries[0] = timeQueries[1] = 0;
        timeQueryIssued[0] = timeQueryIssued[1] = false;
        visibilityQueries[0] = visibilityQueries[1] = 0;
        visibilityQueryIssued[0] = visibilityQueryIssued[1] = false;
        GPU_TIMES_NANOS.clear();
    }

    public record EmitterCommand(Vec3 position, Vec3 velocity, float scale,
        float lifetimeSeconds, float red, float green, float blue, float size,
        float spread, float velocityJitter, int spawnCount, int seed,
        ParticleType type, int flags) {
        public boolean valid() {
            return position != null && position.isFinite() && velocity != null
                && velocity.isFinite() && Float.isFinite(scale) && scale > 0.0F
                && Float.isFinite(lifetimeSeconds) && lifetimeSeconds > 0.0F
                && Float.isFinite(size) && size > 0.0F && Float.isFinite(spread)
                && spread >= 0.0F && spawnCount > 0 && type != null;
        }
    }

    public record DebugSnapshot(Backend backend, long activeParticles,
        long visibleParticles, long culledParticles, long submittedParticles,
        int capacity, long vramBytes, GpuTiming gpuTime) { }
    public record GpuTiming(double p50Millis, double p95Millis,
        double p99Millis, double maximumMillis) { }

    private record GlState(int program, int vertexArray, int indirectBuffer,
        int[] storageBindings, boolean blend, boolean depthTest, boolean cull,
        boolean depthWrite, int blendSourceRgb, int blendDestinationRgb,
        int blendSourceAlpha, int blendDestinationAlpha) {
        private static GlState capture() {
            int[] bindings = new int[5];
            for (int index = 0; index < bindings.length; index++) {
                bindings[index] = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, index);
            }
            return new GlState(glGetInteger(GL_CURRENT_PROGRAM),
                glGetInteger(GL_VERTEX_ARRAY_BINDING),
                glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING), bindings,
                glIsEnabled(GL_BLEND), glIsEnabled(GL_DEPTH_TEST),
                glIsEnabled(GL_CULL_FACE), glGetBoolean(GL_DEPTH_WRITEMASK),
                glGetInteger(GL_BLEND_SRC_RGB), glGetInteger(GL_BLEND_DST_RGB),
                glGetInteger(GL_BLEND_SRC_ALPHA), glGetInteger(GL_BLEND_DST_ALPHA));
        }

        private void restore() {
            glUseProgram(program);
            glBindVertexArray(vertexArray);
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
            for (int index = 0; index < storageBindings.length; index++) {
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, storageBindings[index]);
            }
            setEnabled(GL_BLEND, blend);
            setEnabled(GL_DEPTH_TEST, depthTest);
            setEnabled(GL_CULL_FACE, cull);
            glDepthMask(depthWrite);
            glBlendFuncSeparate(blendSourceRgb, blendDestinationRgb,
                blendSourceAlpha, blendDestinationAlpha);
        }

        private static void setEnabled(final int capability, final boolean enabled) {
            if (enabled) glEnable(capability); else glDisable(capability);
        }
    }
}
