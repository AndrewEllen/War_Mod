package com.andye.warmod.compat.distanthorizons;

import com.andye.warmod.WarMod;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderCleanupEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL45C;

/**
 * Reprojects Distant Horizons' LOD depth into Minecraft's main depth target.
 *
 * <p>Voxy performs an equivalent final depth resolve itself. DH keeps its LOD
 * depth separate, so without this bridge normal Minecraft render types cannot
 * be occluded by distant LOD terrain.</p>
 */
public final class DistantHorizonsDepthBridge {
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final StagedVertexBuffer STAGED_BUFFER = new StagedVertexBuffer(
        () -> "War Mod Distant Horizons depth bridge", RenderType.SMALL_BUFFER_SIZE);
    private static final RenderPipeline DEPTH_BRIDGE = createPipeline(false);
    private static final RenderPipeline DEPTH_BRIDGE_ZERO_TO_ONE = createPipeline(true);

    private static boolean registered;
    private static boolean disabled;
    private static boolean warnedDepthUnavailable;
    private static boolean loggedFirstResolve;
    private static boolean loggedRawResolve;
    private static int wrappedRawTextureId = -1;
    private static int wrappedRawTextureWidth = -1;
    private static int wrappedRawTextureHeight = -1;
    private static GpuTextureView wrappedRawDepthView;

    private DistantHorizonsDepthBridge() { }

    public static void register() {
        if (registered) return;


        DhApiResult<Void> cleanupResult = DhApiEventRegister.on(
            DhApiBeforeRenderCleanupEvent.class, new BeforeCleanupHandler());
        if (!cleanupResult.success) {
            disabled = true;
            WarMod.LOGGER.warn(
                "Distant Horizons depth compatibility could not bind the before-cleanup render event: {}",
                cleanupResult.message);
            return;
        }

        registered = true;
        WarMod.LOGGER.info("Distant Horizons depth compatibility enabled.");
    }

    public static void close() {
        STAGED_BUFFER.close();
        // The API-7.0 view is a non-owning facade over DH's OpenGL texture.
        // Closing it would eventually delete a texture owned by DH.
        wrappedRawDepthView = null;
        wrappedRawTextureId = -1;
    }

    /**
     * Resolve while DH still owns a completed frame, after its apply pass but
     * immediately before it tears down render state. Doing this in AfterRender
     * is too late on the 26.2 Blaze3D path: the LOD depth target may already be
     * detached/reset by the time later Minecraft feature renders execute.
     */
    private static void bridgeFrame(final DhApiRenderParam param) {
        // Resolve into Minecraft's shared depth target before any renderer
        // selection. Iris-submitted quads, custom meshes, and vanilla render
        // types all test against this same target later in the frame.
        if (param == null || disabled || param.renderPass == EDhApiRenderPass.TRANSPARENT) {
            return;
        }

        try {
            if (DhApi.Delayed.renderProxy == null) return;
            Object renderProxy = DhApi.Delayed.renderProxy;
            Matrix4f mcProjection = toJoml(param.mcProjectionMatrix);
            Matrix4f mcModelView = toJoml(param.mcModelViewMatrix);
            Matrix4f dhInverseMvp = toJoml(param.dhInverseMvmProjectionMatrix);
            Matrix4f reprojection = mcProjection.mul(mcModelView).mul(dhInverseMvp);

            if (!resolveBlazeDepth(renderProxy, reprojection)) {
                resolveRawOpenGlDepth(renderProxy, reprojection);
            }
            warnedDepthUnavailable = false;
            if (!loggedFirstResolve) {
                loggedFirstResolve = true;
                WarMod.LOGGER.info(
                    "Distant Horizons LOD depth resolved into Minecraft depth before DH cleanup.");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disabled = true;
            WarMod.LOGGER.warn(
                "Distant Horizons depth compatibility failed and will be disabled for this session",
                exception);
        }
    }

    /** DH API 7.1+ exposes an owned Blaze3D view which is the preferred path. */
    private static boolean supportsBlazeDepthWrappers() {
        int major = DhApi.getApiMajorVersion();
        int minor = DhApi.getApiMinorVersion();
        return major > 7 || (major == 7 && minor >= 1);
    }

    private static boolean resolveBlazeDepth(final Object renderProxy,
        final Matrix4f reprojection) throws ReflectiveOperationException {
        if (!supportsBlazeDepthWrappers()) return false;
        Object renderingEngine = invoke(renderProxy, "getRenderingEngine");
        if (!"BLAZE_3D".equals(String.valueOf(renderingEngine))) return false;

        DhApiResult<?> result = (DhApiResult<?>) invoke(renderProxy,
            "getDhDepthTextureBlazeWrapper");
        if (!result.success || result.payload == null) {
            warnDepthUnavailable(result.message);
            return true;
        }
        Object viewObject = invoke(result.payload, "getTextureView");
        Object samplerObject = invoke(result.payload, "getTextureSampler");
        if (!(viewObject instanceof GpuTextureView depthView)
            || !(samplerObject instanceof GpuSampler depthSampler)) {
            warnDepthUnavailable("DH returned an uninitialized Blaze3D depth texture");
            return true;
        }
        drawDepth(reprojection, depthView, depthSampler);
        return true;
    }

    /**
     * DH 3.2.0-b/API 7.0.1 is forced by Iris onto DH's raw OpenGL renderer.
     * Its compatibility API exposes only getDhDepthTextureId(), so wrap that
     * texture in a non-owning Blaze3D facade and run the same reprojection pass.
     */
    private static void resolveRawOpenGlDepth(final Object renderProxy,
        final Matrix4f reprojection) throws ReflectiveOperationException {
        DhApiResult<?> result = (DhApiResult<?>) invoke(renderProxy, "getDhDepthTextureId");
        if (!result.success || !(result.payload instanceof Number textureNumber)
            || textureNumber.intValue() <= 0) {
            warnDepthUnavailable(result.message);
            return;
        }
        int textureId = textureNumber.intValue();
        GpuTextureView depthView = rawDepthView(textureId);
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        drawDepth(reprojection, depthView, sampler);
        if (!loggedRawResolve) {
            loggedRawResolve = true;
            WarMod.LOGGER.info(
                "Distant Horizons API 7.0 raw OpenGL depth texture is being reprojected into Minecraft depth.");
        }
    }

    private static GpuTextureView rawDepthView(final int textureId)
        throws ReflectiveOperationException {
        RenderSystem.assertOnRenderThread();
        if (!GL11C.glIsTexture(textureId)) {
            throw new IllegalStateException("DH returned a non-texture OpenGL name: " + textureId);
        }
        int width = GL45C.glGetTextureLevelParameteri(textureId, 0, GL11C.GL_TEXTURE_WIDTH);
        int height = GL45C.glGetTextureLevelParameteri(textureId, 0, GL11C.GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("DH returned an incomplete depth texture: " + textureId);
        }
        if (wrappedRawDepthView != null && wrappedRawTextureId == textureId
            && wrappedRawTextureWidth == width && wrappedRawTextureHeight == height) {
            return wrappedRawDepthView;
        }

        Object device = RenderSystem.getDevice();
        Field backendField = device.getClass().getDeclaredField("backend");
        backendField.setAccessible(true);
        Object backend = backendField.get(device);
        Field cacheField = backend.getClass().getDeclaredField("frameBufferCache");
        cacheField.setAccessible(true);
        Object frameBufferCache = cacheField.get(backend);

        Class<?> glTextureClass = Class.forName("com.mojang.blaze3d.opengl.GlTexture");
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : glTextureClass.getDeclaredConstructors()) {
            if (candidate.getParameterCount() == 9) {
                constructor = candidate;
                break;
            }
        }
        if (constructor == null) {
            throw new NoSuchMethodException("GlTexture external facade constructor");
        }
        constructor.setAccessible(true);
        GpuTexture texture = (GpuTexture) constructor.newInstance(textureId,
            "War Mod borrowed DH API 7.0 depth", GpuFormat.D32_FLOAT,
            width, height, 1, 1, GpuTexture.USAGE_TEXTURE_BINDING, frameBufferCache);
        wrappedRawDepthView = RenderSystem.getDevice().createTextureView(texture);
        wrappedRawTextureId = textureId;
        wrappedRawTextureWidth = width;
        wrappedRawTextureHeight = height;
        return wrappedRawDepthView;
    }

    private static Object invoke(final Object target, final String method)
        throws ReflectiveOperationException {
        Method reflectedMethod = target.getClass().getMethod(method);
        return reflectedMethod.invoke(target);
    }

    private static void drawDepth(final Matrix4f reprojection, final GpuTextureView depthView,
        final GpuSampler depthSampler) {
        RenderPipeline pipeline = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()
            ? DEPTH_BRIDGE_ZERO_TO_ONE : DEPTH_BRIDGE;
        VertexFormat formatBinding = pipeline.getVertexFormatBinding(0);
        if (formatBinding == null) return;

        PrimitiveTopology primitive = pipeline.getPrimitiveTopology();
        StagedVertexBuffer.Draw draw = STAGED_BUFFER.appendDraw(formatBinding, primitive,
            primitive == PrimitiveTopology.QUADS
                ? RenderSystem.getProjectionType().vertexSorting() : null);
        var builder = STAGED_BUFFER.getVertexBuilder(draw);
        builder.addVertex(-1.0F, -1.0F, 0.0F).setColor(0xFFFFFFFF);
        builder.addVertex(1.0F, -1.0F, 0.0F).setColor(0xFFFFFFFF);
        builder.addVertex(1.0F, 1.0F, 0.0F).setColor(0xFFFFFFFF);
        builder.addVertex(-1.0F, 1.0F, 0.0F).setColor(0xFFFFFFFF);

        try {
            STAGED_BUFFER.upload();
            StagedVertexBuffer.ExecuteInfo info = STAGED_BUFFER.getExecuteInfo(draw);
            if (info == null) return;

            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                reprojection, COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
            Minecraft client = Minecraft.getInstance();
            RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
            GpuTextureView colorTexture = mainTarget.getColorTextureView();
            GpuTextureView mainDepth = mainTarget.getDepthTextureView();
            if (colorTexture == null || mainDepth == null) return;

            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "War Mod Distant Horizons depth resolve",
                    colorTexture, Optional.empty(), mainDepth, OptionalDouble.empty())) {
                renderPass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                renderPass.bindTexture("Sampler0", depthView, depthSampler);
                renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
                renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());
                renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(),
                    info.baseVertex(), 0);
            }
        } finally {
            STAGED_BUFFER.endFrame();
        }
    }

    private static RenderPipeline createPipeline(final boolean zeroToOne) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(WarMod.MOD_ID,
                zeroToOne ? "pipeline/dh_depth_bridge_zero_to_one" : "pipeline/dh_depth_bridge"))
            .withVertexShader(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "core/dh_depth_bridge"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "core/dh_depth_bridge"))
            .withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler0").build())
            .withColorTargetState(new ColorTargetState(Optional.empty(),
                ColorTargetState.DEFAULT.format(), ColorTargetState.WRITE_NONE))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false);
        if (zeroToOne) builder.withShaderDefine("ZERO_ONE_NDC");
        return RenderPipelines.register(builder.build());
    }

    private static Matrix4f toJoml(final DhApiMat4f matrix) {
        return new Matrix4f().set(matrix.getValuesAsArray()).transpose();
    }

    private static void warnDepthUnavailable(final String reason) {
        if (warnedDepthUnavailable) return;
        warnedDepthUnavailable = true;
        WarMod.LOGGER.warn("Distant Horizons depth texture is not available yet: {}", reason);
    }

    private static final class BeforeCleanupHandler extends DhApiBeforeRenderCleanupEvent {
        @Override
        public void beforeCleanup(final DhApiEventParam<DhApiRenderParam> event) {
            if (event == null || event.value == null) return;
            bridgeFrame(event.value);
        }
    }
}
