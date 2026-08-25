package com.andye.warmod.warhead.client.render;

import com.andye.warmod.acoustics.ModSoundEvents;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadEffectProfile;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.WarheadYieldScaling;
import com.andye.warmod.warhead.client.ClientDebrisBatchManager;
import com.andye.warmod.warhead.client.ClientWarheadVisualManager;
import com.andye.warmod.warhead.client.ImpactVisualState;
import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.andye.warmod.warhead.client.WarheadVisualState;
import com.andye.warmod.particle.gpu.GpuParticleEngine;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectClass;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectHandle;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EmitterCommand;
import com.andye.warmod.particle.gpu.GpuParticleEngine.ParticleType;
import com.andye.warmod.particle.gpu.GpuParticleEngine.VisualLayer;
import com.andye.warmod.diagnostics.client.ClientPerformanceTelemetry;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Active War Mod world render submission path. */
public final class WarheadWorldRenderer {
    private static final double NEAR_DISTANCE = 192.0;
    private static final double MEDIUM_DISTANCE = 640.0;
    private static final double MAX_DISTANCE = WarheadConstants.VISUAL_RANGE_BLOCKS;
    private static final Set<UUID> RETURN_WAVE_SOUND_PLAYED = new HashSet<>();
    private static final Map<UUID, Double> RETURN_WAVE_PREVIOUS_RADIUS = new HashMap<>();
    private static volatile RenderFrame currentFrame = RenderFrame.EMPTY;
    private static boolean registered;
    private static long lastDebugTick = Long.MIN_VALUE;

    private WarheadWorldRenderer() { }

    public static void register() {
        if (registered) return;
        LevelExtractionEvents.END_EXTRACTION.register(WarheadWorldRenderer::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(WarheadWorldRenderer::collectSubmits);
        registered = true;
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            com.andye.warmod.WarMod.LOGGER.info("Warhead renderer loaded; backend={}",
                debugSnapshot().activeRenderBackend());
        }
    }

    private static void extract(final LevelExtractionContext context) {
        long extractionStarted = System.nanoTime();
        ClientLevel level = context.level();
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (level == null || camera == null || camera.pos == null) {
            currentFrame = RenderFrame.EMPTY;
            RETURN_WAVE_SOUND_PLAYED.clear();
            RETURN_WAVE_PREVIOUS_RADIUS.clear();
            NuclearParticleCloudRenderer.retainFields(Set.of());
            ConventionalBlastParticleRenderer.clearLevel();
            ClientPerformanceTelemetry.recordExplosionNanos(
                Math.max(0L, System.nanoTime() - extractionStarted));
            return;
        }
        Vec3 cameraPosition = camera.pos;
        Quaternionf cameraOrientation = camera.orientation == null
            ? new Quaternionf() : new Quaternionf(camera.orientation);
        long gameTime = level.getGameTime();
        double partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(true);
        ClientWarheadVisualManager.Snapshot snapshot =
            ClientWarheadVisualManager.INSTANCE.snapshot(level);

        List<WarheadFrame> warheads = new ArrayList<>();
        for (WarheadVisualState state : snapshot.warheads()) {
            if (state.isExpired(gameTime, partialTick)) continue;
            Vec3 position = state.positionAt(gameTime, partialTick);
            Vec3 velocity = state.velocityAt(gameTime, partialTick);
            double distance = cameraPosition.distanceTo(position);
            if (!position.isFinite() || !velocity.isFinite() || !Double.isFinite(distance)
                || distance > MAX_DISTANCE || !visibleSphere(cameraPosition, cameraOrientation,
                    position, 12.0)) continue;
            double elapsed = state.elapsedTicks(gameTime, partialTick);
            warheads.add(new WarheadFrame(position, velocity,
                (float) state.progressAt(gameTime, partialTick), (float) elapsed,
                (float) Math.max(0.0, state.flightTicks() - elapsed), state.flightTicks(),
                state.visualSeed(), lod(distance), sampledLight(level, position)));
        }

        List<ImpactFrame> impacts = new ArrayList<>();
        Set<UUID> activeReturnWaveIds = new HashSet<>();
        Set<Long> activeNuclearCloudSeeds = new HashSet<>();
        Set<Long> activeConventionalFieldSeeds = new HashSet<>();
        for (ImpactVisualState state : snapshot.impacts()) {
            double age = state.ageTicks(gameTime, partialTick);
            if (state.isExpired(gameTime, partialTick)) continue;
            activeConventionalFieldSeeds.add(state.visualSeed());
            if (state.payloadType() == WarheadPayloadType.NUCLEAR) {
                activeReturnWaveIds.add(state.warheadId());
                activeNuclearCloudSeeds.add(state.visualSeed());
                playReturnWaveSound(level, state, age, cameraPosition);
            }
            double distance = cameraPosition.distanceTo(state.impactPosition());
            if (!Double.isFinite(distance) || distance > MAX_DISTANCE) continue;
            float yieldRadiusScale = WarheadYieldScaling.radiusScale(
                state.payloadType(), state.visualScale());
            double rawGroundDistance = WarheadVisualMath.groundShockwaveDistance(age,
                yieldRadiusScale);
            double groundDistance = visualGroundDistance(state.payloadType(),
                state.visualScale(), rawGroundDistance);
            double cloudRadius = state.payloadType() == WarheadPayloadType.NUCLEAR
                ? 82.0 + state.visualScale() * 45.0 + Math.sqrt(Math.max(0.0, age)) * 3.2
                : 20.0 + state.visualScale() * 22.0;
            double visibleRadius = Math.max(cloudRadius,
                groundDistance > 0.0 && age < WarheadVisualMath.airShockwaveDurationTicks(yieldRadiusScale)
                    ? groundDistance : 0.0);
            if (!visibleSphere(cameraPosition, cameraOrientation,
                state.impactPosition(), visibleRadius)) continue;

            WarheadMesh.Lod impactLod = lod(distance);
            float budgetScale = Mth.clamp(
                (float) Math.sqrt(WarheadRenderSettings.particleBudgetMultiplier() / 6.0F),
                0.45F, 4.0F);
            boolean nuclear = state.payloadType() == WarheadPayloadType.NUCLEAR;
            if (nuclear) budgetScale = Math.max(1.0F, budgetScale);
            int dustLimit = Math.round((nuclear
                ? impactLod == WarheadMesh.Lod.NEAR ? 14_000
                    : impactLod == WarheadMesh.Lod.MEDIUM ? 9_000 : 5_000
                : impactLod == WarheadMesh.Lod.NEAR ? 8_000
                    : impactLod == WarheadMesh.Lod.MEDIUM ? 5_000 : 2_400) * budgetScale);
            List<TerrainShockfrontNode> dustNodes = groundEffects(state.effectProfile())
                ? state.terrainShockfrontField().activeDustNodes(groundDistance,
                    frontierSpokeCount(impactLod), dustLimit, gameTime)
                : List.of();
            for (TerrainShockfrontNode node : dustNodes) {
                if (node.state() == TerrainShockfrontNode.State.READY) {
                    state.terrainShockfrontField().markEmitted(node, gameTime);
                }
            }
            impacts.add(new ImpactFrame(state.warheadId(), state.impactPosition(), age,
                state.visualScale(), state.visualSeed(), state.payloadType(),
                state.effectProfile(), ClientWarheadVisualManager.INSTANCE
                    .shouldRenderVolumetrics(state.warheadId()), state.profile(), impactLod,
                state.terrainShockfrontField().snapshotSpokes(), dustNodes,
                state.ambientWind(), gameTime,
                rawGroundDistance, groundDistance));
            submitGpuImpact(state.warheadId(), state.impactPosition(), age, state.visualScale(),
                state.visualSeed(), state.payloadType(), state.effectProfile(), state.profile(),
                state.fireballLobes(), state.blastCloudLobes(), dustNodes,
                state.ambientWind());
        }
        RETURN_WAVE_SOUND_PLAYED.retainAll(activeReturnWaveIds);
        RETURN_WAVE_PREVIOUS_RADIUS.keySet().retainAll(activeReturnWaveIds);
        /* Authoritative CPU fields remain warm even while verified GPU layers
           are selected, so a backend failure or runtime switch can recover on
           the next extraction frame without requiring another detonation. */
        NuclearParticleCloudRenderer.retainFields(activeNuclearCloudSeeds);
        ConventionalBlastParticleRenderer.retainFields(activeConventionalFieldSeeds);

        List<DebrisFrame> debris = new ArrayList<>();
        for (ClientDebrisBatchManager.RenderSample sample
            : ClientDebrisBatchManager.INSTANCE.snapshot(level, gameTime, partialTick,
                cameraPosition, cameraOrientation, MAX_DISTANCE)) {
            if (!visibleSphere(cameraPosition, cameraOrientation, sample.position(),
                Math.max(4.0, sample.scale() * 4.0))) continue;
            BlockPos blockPosition = BlockPos.containing(sample.position());
            MovingBlockRenderState movingBlock = new MovingBlockRenderState();
            movingBlock.randomSeedPos = blockPosition;
            movingBlock.blockPos = blockPosition;
            movingBlock.blockState = sample.state();
            movingBlock.biome = level.hasChunkAt(blockPosition)
                ? level.getBiome(blockPosition) : null;
            movingBlock.cardinalLighting = CardinalLighting.DEFAULT;
            movingBlock.lightEngine = level.getLightEngine();
            debris.add(new DebrisFrame(sample, movingBlock, debrisTrailColour(sample)));
        }
        currentFrame = new RenderFrame(cameraPosition, cameraOrientation,
            List.copyOf(warheads), List.copyOf(impacts), List.copyOf(debris));

        if (SharedConstants.IS_RUNNING_IN_IDE && gameTime != lastDebugTick
            && gameTime % 100L == 0L) {
            lastDebugTick = gameTime;
            DebugSnapshot debug = debugSnapshot();
            com.andye.warmod.WarMod.LOGGER.info(
                "Stage8 simulated={} represented={} spawned/tick={} culled={} debris={} backend={}",
                debug.activeParticles(), debug.representedParticles(), debug.spawnedParticlesPerTick(),
                debug.culledParticles(), debug.activeDebrisFragments(),
                debug.activeRenderBackend());
        }
        ClientPerformanceTelemetry.recordExplosionNanos(
            Math.max(0L, System.nanoTime() - extractionStarted));
    }

    private static void collectSubmits(final LevelRenderContext context) {
        RenderFrame frame = currentFrame;
        if (frame == RenderFrame.EMPTY || (frame.warheads().isEmpty()
            && frame.impacts().isEmpty() && frame.debris().isEmpty())) return;
        PoseStack poseStack = context.poseStack();
        if (poseStack == null) return;
        if (!frame.impacts().isEmpty()) {
            GroundDustFrontRenderer.beginFrame();
        }
        for (WarheadFrame warhead : frame.warheads()) {
            renderWarhead(context, poseStack, frame.cameraPosition(), warhead);
        }
        for (ImpactFrame impact : frame.impacts()) {
            renderImpact(context, poseStack, frame, impact);
        }
        renderDebris(context, poseStack, frame);
    }

    private static void renderWarhead(final LevelRenderContext context,
        final PoseStack poseStack, final Vec3 camera, final WarheadFrame warhead) {
        poseStack.pushPose();
        Vec3 relative = warhead.position().subtract(camera);
        poseStack.translate(relative.x, relative.y, relative.z);
        poseStack.mulPose(rotationToVelocity(warhead.velocity()));
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.PROJECTILE,
            (pose, buffer) -> WarheadMesh.render(pose, buffer, warhead.lod(),
                warhead.packedLight()));
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.CONE,
            (pose, buffer) -> ShockConeMesh.render(pose, buffer, warhead.lod(),
                warhead.progress(), warhead.elapsedTicks(), warhead.remainingTicks(),
                warhead.velocity(), warhead.visualSeed(), warhead.flightTicks()));
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.VAPOR_BAND,
            (pose, buffer) -> VaporBandRenderer.render(pose, buffer, warhead.lod(),
                warhead.elapsedTicks(), warhead.visualSeed(), warhead.progress(),
                (float) coneActivation(warhead),
                (float) WarheadVisualMath.coneFade(warhead.remainingTicks())));
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.REENTRY_PLASMA,
            (pose, buffer) -> ReentryHeatingRenderer.renderBowShock(pose, buffer,
                warhead.lod(), warhead.progress(), warhead.elapsedTicks(),
                warhead.remainingTicks(), warhead.velocity(), warhead.visualSeed()));
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.REENTRY_PLASMA,
            (pose, buffer) -> ReentryHeatingRenderer.renderGlow(pose, buffer,
                warhead.lod(), warhead.progress(), warhead.elapsedTicks(),
                warhead.remainingTicks(), warhead.velocity(), warhead.visualSeed()));
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.REENTRY_PLASMA,
            (pose, buffer) -> ReentryHeatingRenderer.renderFilaments(pose, buffer,
                warhead.lod(), warhead.progress(), warhead.elapsedTicks(),
                warhead.remainingTicks(), warhead.velocity(), warhead.visualSeed()));
        poseStack.popPose();
    }

    private static void renderImpact(final LevelRenderContext context,
        final PoseStack poseStack, final RenderFrame frame, final ImpactFrame impact) {
        Vec3 relative = impact.position().subtract(frame.cameraPosition());
        float yieldRadiusScale = WarheadYieldScaling.radiusScale(
            impact.payloadType(), impact.visualScale());
        float yieldThicknessScale = (float) Math.sqrt(yieldRadiusScale);
        float thicknessScale = (float) impact.profile().shockwaveThicknessScale()
            * yieldThicknessScale;
        float alphaScale = (float) impact.profile().shockwaveAlphaScale()
            * Mth.clamp(yieldThicknessScale, 0.72F, 1.28F);
        float rangeFade = visualRangeFade(impact.payloadType(), impact.visualScale(),
            impact.rawGroundDistance());
        boolean gpuFireball = gpuImpactLayerReady(impact.payloadType(), VisualLayer.FIREBALL);
        boolean gpuCloudSmoke = gpuImpactLayerReady(impact.payloadType(),
            VisualLayer.MUSHROOM_CLOUD);
        boolean gpuStemSmoke = gpuImpactLayerReady(impact.payloadType(), VisualLayer.STEM);
        boolean gpuGroundDust = gpuImpactLayerReady(impact.payloadType(),
            VisualLayer.GROUND_DUST);

        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);
        double rawAirRadius = WarheadVisualMath.airShockwaveRadius(
            impact.ageTicks(), yieldRadiusScale);
        double visualAirRadius = visualGroundDistance(impact.payloadType(),
            impact.visualScale(), rawAirRadius);
        if (rangeFade > 0.001F) {
            context.submitNodeCollector().submitCustomGeometry(poseStack,
                WarheadRenderPipelines.PRESSURE_SHELL,
                (pose, buffer) -> PressureWaveSphereRenderer.render(pose, buffer,
                    visualAirRadius, impact.ageTicks(), thicknessScale,
                    alphaScale * rangeFade, yieldRadiusScale, impact.lod()));
        }
        if (impact.payloadType() == WarheadPayloadType.NUCLEAR) {
            double returnRadius = WarheadVisualMath.nuclearReturnWaveRadius(
                impact.ageTicks(), yieldRadiusScale);
            if (returnRadius > 0.0) {
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.PRESSURE_SHELL,
                    (pose, buffer) -> PressureWaveSphereRenderer.renderReturn(pose, buffer,
                        returnRadius, impact.ageTicks(), yieldRadiusScale, impact.lod()));
            }
        }
        if (groundEffects(impact.effectProfile()) && rangeFade > 0.001F) {
            context.submitNodeCollector().submitCustomGeometry(poseStack,
                WarheadRenderPipelines.SHOCKWAVE,
                (pose, buffer) -> TerrainShockwaveRenderer.renderFrontier(pose, buffer,
                    impact.shockfrontSpokes(), impact.position(), impact.groundDistance(),
                    frontierSpokeCount(impact.lod()),
                    groundFrontierWidth(impact.ageTicks(), thicknessScale, yieldRadiusScale),
                    groundFrontierAlpha(impact.ageTicks(), alphaScale, yieldRadiusScale)
                        * rangeFade,
                    208, 226, 244));
            if (!gpuGroundDust) {
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.GROUND_DUST,
                    (pose, buffer) -> ConventionalBlastParticleRenderer.renderSurfaceFront(
                        pose, buffer, impact.ageTicks(), impact.groundDistance(),
                        impact.visualScale(), impact.visualSeed(), impact.position(), impact.lod(),
                        frame.cameraOrientation()));
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.EXPLOSION_PUFF,
                    (pose, buffer) -> ConventionalBlastParticleRenderer.renderSurfaceExplosionPuffs(
                        pose, buffer, impact.ageTicks(), impact.groundDistance(),
                        impact.visualScale(), impact.visualSeed(), impact.position(), impact.lod(),
                        frame.cameraOrientation()));
            }
        }
        if (impact.payloadType() == WarheadPayloadType.NUCLEAR) {
            double returnRadius = WarheadVisualMath.nuclearReturnWaveRadius(
                impact.ageTicks(), yieldRadiusScale);
            if (returnRadius > 0.0) {
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.GROUND_DUST,
                    (pose, buffer) -> ConventionalBlastParticleRenderer.renderNuclearReturnFront(
                        pose, buffer, impact.ageTicks(), returnRadius, yieldRadiusScale,
                        impact.visualSeed(), impact.position(), impact.lod(),
                        frame.cameraOrientation()));
            }
        }
        poseStack.popPose();

        CloudCluster cloud = impact.payloadType() == WarheadPayloadType.NUCLEAR
            ? cloudCluster(frame.impacts(), impact) : null;
        boolean renderCloud = impact.renderVolumetrics()
            && (cloud == null || cloud.leaderId().equals(impact.id()));

        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);
        if (groundEffects(impact.effectProfile()) && !gpuGroundDust) {
            context.submitNodeCollector().submitCustomGeometry(poseStack,
                WarheadRenderPipelines.GROUND_DUST,
                (pose, buffer) -> GroundDustFrontRenderer.render(pose, buffer,
                    impact.dustNodes(), impact.position(), impact.gameTime(), impact.lod(),
                    (float) impact.profile().shockwaveParticleDensityScale()
                        * yieldThicknessScale,
                    impact.payloadType() == WarheadPayloadType.NUCLEAR,
                    frame.cameraOrientation()));
            context.submitNodeCollector().submitCustomGeometry(poseStack,
                WarheadRenderPipelines.EXPLOSION_PUFF,
                (pose, buffer) -> GroundDustFrontRenderer.renderExplosionFlecks(
                    pose, buffer, impact.dustNodes(), impact.position(), impact.gameTime(),
                    impact.lod(), (float) impact.profile().shockwaveParticleDensityScale()
                        * yieldThicknessScale,
                    impact.payloadType() == WarheadPayloadType.NUCLEAR,
                    frame.cameraOrientation()));
        }

        if (renderCloud || impact.payloadType() == WarheadPayloadType.NUCLEAR) {
            context.submitNodeCollector().submitCustomGeometry(poseStack,
                impact.payloadType() == WarheadPayloadType.NUCLEAR
                    ? WarheadRenderPipelines.NUCLEAR_SMOKE
                    : WarheadRenderPipelines.HEAVY_SMOKE,
                (pose, buffer) -> TerrainSettledSmokeRenderer.render(pose, buffer,
                    impact.shockfrontSpokes(), impact.position(), impact.ageTicks(),
                    impact.visualScale(), impact.visualSeed(), impact.lod(),
                    impact.payloadType() == WarheadPayloadType.NUCLEAR,
                    frame.cameraOrientation()));
        }

        if (impact.payloadType() == WarheadPayloadType.NUCLEAR) {
            if (renderCloud) {
                if (!gpuCloudSmoke || !gpuStemSmoke) {
                    context.submitNodeCollector().submitCustomGeometry(poseStack,
                        WarheadRenderPipelines.NUCLEAR_SMOKE,
                        (pose, buffer) -> {
                            if (!gpuCloudSmoke) NuclearParticleCloudRenderer.renderSmoke(
                                pose, buffer, cloud.ageTicks(), cloud.visualScale(),
                                cloud.profile(), cloud.visualSeed(), impact.lod(),
                                cloud.sources(), frame.cameraOrientation(),
                                cloud.ambientWind());
                            if (!gpuStemSmoke) NuclearCentralColumnRenderer.renderSmoke(
                                pose, buffer, cloud.ageTicks(), cloud.visualScale(),
                                cloud.visualSeed(), impact.lod(), frame.cameraOrientation());
                        });
                }

                /* The analytical central-column fire has no proven GPU
                   equivalent and therefore remains independently CPU-owned. */
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.NUCLEAR_FIRE,
                    (pose, buffer) -> {
                        NuclearCentralColumnRenderer.renderFire(pose, buffer,
                            cloud.ageTicks(), cloud.visualScale(), cloud.visualSeed(),
                            impact.lod(), true, frame.cameraOrientation());
                        if (!gpuFireball) NuclearParticleCloudRenderer.renderFire(
                            pose, buffer, cloud.ageTicks(), cloud.visualScale(),
                            cloud.profile(), cloud.visualSeed(), impact.lod(), true,
                            cloud.sources(), frame.cameraOrientation(),
                            cloud.ambientWind());
                        NuclearCentralColumnRenderer.renderFire(pose, buffer,
                            cloud.ageTicks(), cloud.visualScale(), cloud.visualSeed(),
                            impact.lod(), false, frame.cameraOrientation());
                        if (!gpuFireball) NuclearParticleCloudRenderer.renderFire(
                            pose, buffer, cloud.ageTicks(), cloud.visualScale(),
                            cloud.profile(), cloud.visualSeed(), impact.lod(), false,
                            cloud.sources(), frame.cameraOrientation(),
                            cloud.ambientWind());
                    });
            }
            if (renderCloud || cloud.sources().size() == 1) {
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.NUCLEAR_FLASH,
                    (pose, buffer) -> NuclearFlashRenderer.render(pose, buffer,
                        impact.ageTicks(), frame.cameraOrientation()));
            }
        } else if (renderCloud) {
            if (!gpuFireball) {
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.FIREBALL_CORE,
                    (pose, buffer) -> ConventionalBlastVisualV5.renderFireCore(
                        pose, buffer, impact.ageTicks(), impact.visualScale(), impact.profile(),
                        impact.visualSeed(), impact.lod(), frame.cameraOrientation()));
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.FIREBALL_HOT,
                    (pose, buffer) -> ConventionalBlastVisualV5.renderHot(
                        pose, buffer, impact.ageTicks(), impact.visualScale(), impact.profile(),
                        impact.visualSeed(), impact.lod(), frame.cameraOrientation()));
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.FIREBALL_COOL,
                    (pose, buffer) -> ConventionalBlastVisualV5.renderCooling(
                        pose, buffer, impact.ageTicks(), impact.visualScale(), impact.profile(),
                        impact.visualSeed(), impact.lod(), frame.cameraOrientation()));
            }
            if (!gpuCloudSmoke) {
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.HEAVY_SMOKE_CORE,
                    (pose, buffer) -> ConventionalBlastVisualV5.renderSmokeCore(
                        pose, buffer, impact.ageTicks(), impact.visualScale(), impact.profile(),
                        impact.visualSeed(), impact.lod(), frame.cameraOrientation()));
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.HEAVY_SMOKE,
                    (pose, buffer) -> ConventionalBlastVisualV5.renderSmoke(
                        pose, buffer, impact.ageTicks(), impact.visualScale(), impact.profile(),
                        impact.visualSeed(), impact.lod(), frame.cameraOrientation()));
            }
        }
        poseStack.popPose();
    }

    /**
     * Semantic selection is stricter than shader readiness. The current GPU stem
     * emitter has no side-by-side parity with the analytical nuclear column, so
     * that one layer deliberately remains CPU-owned while GPU cap smoke is used.
     */
    private static boolean gpuImpactLayerReady(final WarheadPayloadType payloadType,
        final VisualLayer layer) {
        if (!GpuParticleEngine.canRender(layer)) return false;
        return payloadType != WarheadPayloadType.NUCLEAR || layer != VisualLayer.STEM;
    }

    private static void submitGpuImpact(final UUID warheadId, final Vec3 position,
        final double ageTicks,
        final float visualScale, final long seed, final WarheadPayloadType payloadType,
        final WarheadEffectProfile effect, final WarheadClientVisualProfile profile,
        final List<FireballLobe> fireballLobes, final List<BlastCloudLobe> cloudLobes,
        final List<TerrainShockfrontNode> dustNodes, final Vec3 ambientWind) {
        if (!GpuParticleEngine.isGpuReady()) return;
        boolean nuclear = payloadType == WarheadPayloadType.NUCLEAR;
        float scale = Math.max(0.15F, visualScale);
        int folded = (int) (seed ^ seed >>> 32);
        float bounds = nuclear ? 180.0F + scale * 96.0F : 38.0F + scale * 28.0F;
        float temporalImportance = ageTicks < (nuclear ? 80.0 : 28.0) ? 1.35F : 1.0F;
        EffectHandle vfx = GpuParticleEngine.beginEffect(
            nuclear ? EffectClass.NUCLEAR : EffectClass.CONVENTIONAL,
            GpuParticleEngine.stableId(warheadId), position, bounds, temporalImportance);
        if (gpuImpactLayerReady(payloadType, VisualLayer.FIREBALL)
            && ageTicks < (nuclear ? 48.0 : 22.0)) {
            float fade = (float) Math.max(0.0,
                1.0 - ageTicks / (nuclear ? 48.0 : 22.0));
            vfx.submitLayer(VisualLayer.FIREBALL, new EmitterCommand(position,
                new Vec3(0.0, nuclear ? 3.8 : 2.2, 0.0), scale,
                nuclear ? 2.8F : 1.25F, 1.0F, nuclear ? 0.58F : 0.34F, 0.06F,
                nuclear ? 3.4F : 0.85F,
                (nuclear ? 5.0F : 1.8F) * scale,
                (nuclear ? 6.2F : 3.5F) * scale,
                Math.max(12, Math.round((nuclear ? 1_800.0F : 700.0F) * fade)),
                folded, ParticleType.EXPLOSION_FIRE, 0));
        }
        if (gpuImpactLayerReady(payloadType, VisualLayer.FIREBALL)
            && profile != null && fireballLobes != null
            && ageTicks >= profile.fireballGrowthStartTick()
            && ageTicks < profile.fireballCoolingEndTick()) {
            int limit = Math.min(nuclear ? 128 : 48, fireballLobes.size());
            double rawGrowth = Mth.clamp((ageTicks - profile.fireballGrowthStartTick())
                / Math.max(1.0, profile.fireballGrowthEndTick()
                    - profile.fireballGrowthStartTick()), 0.0, 1.0);
            double growth = rawGrowth * rawGrowth * (3.0 - 2.0 * rawGrowth);
            float geometryScale = nuclear ? 1.0F : Mth.clamp(scale, 0.45F, 1.5F);
            List<EmitterCommand> structure = new ArrayList<>(limit);
            for (int visible = 0; visible < limit; visible++) {
                FireballLobe lobe = fireballLobes.get(
                    (int) ((long) visible * fireballLobes.size() / limit));
                if (ageTicks < lobe.spawnDelayTicks()) continue;
                Vec3 center = position.add(lobe.baseOffset().scale(growth * geometryScale));
                float radius = (float) Math.max(0.35, lobe.baseRadius()
                    * lobe.expansionMultiplier() * (0.18 + growth * 0.82) * geometryScale);
                float particleSize = nuclear ? Math.min(4.2F, radius * 0.28F)
                    : Math.min(1.8F, radius * 0.32F);
                structure.add(new EmitterCommand(center,
                    new Vec3(0.0, 1.4 + lobe.riseSpeed() * 0.45, 0.0),
                    scale, nuclear ? 2.8F : 1.4F, 1.0F, 0.52F, 0.05F,
                    particleSize, radius * 0.45F, radius * 0.20F,
                    nuclear ? 72 : 38, folded ^ visible * 0x632BE5AB,
                    ParticleType.EXPLOSION_FIRE, 0));
            }
            vfx.submitLayer(VisualLayer.FIREBALL, structure);
        }
        if (profile != null && cloudLobes != null && !cloudLobes.isEmpty()
            && ageTicks >= profile.smokeStartTick()
            && ageTicks < profile.cloudDissipationEndTick()) {
            int limit = Math.min(nuclear ? 192 : 72, cloudLobes.size());
            List<EmitterCommand> cap = new ArrayList<>();
            float geometryScale = nuclear ? 1.0F : Mth.clamp(scale, 0.55F, 1.45F);
            for (int visible = 0; visible < limit; visible++) {
                BlastCloudLobe lobe = cloudLobes.get(
                    (int) ((long) visible * cloudLobes.size() / limit));
                Vec3 local = BlastCloudRenderer.center(lobe, profile, ageTicks,
                    geometryScale, ambientWind);
                if (local == null || !local.isFinite()) continue;
                Vec3 center = position.add(local);
                boolean stemLayer = lobe.flowRole() == BlastCloudFlowRole.STEM;
                if (nuclear && stemLayer) continue;
                float radius = (float) Math.max(0.6, lobe.baseRadius() * geometryScale);
                Vec3 radial = new Vec3(local.x, 0.0, local.z);
                Vec3 windVelocity = ambientWind == null || !ambientWind.isFinite()
                    ? Vec3.ZERO : new Vec3(ambientWind.x, 0.0, ambientWind.z)
                        .scale(nuclear && !stemLayer ? 0.42 : 0.12);
                Vec3 velocity = (stemLayer ? new Vec3(0.0, 0.72, 0.0)
                    : (radial.lengthSqr() < 1.0E-6 ? Vec3.ZERO : radial.normalize())
                        .scale(nuclear ? 0.20 : 0.12).add(0.0,
                            nuclear ? 0.34 : 0.22, 0.0)).add(windVelocity);
                int colourRed = Mth.clamp(lobe.red(), 0, 255);
                int colourGreen = Mth.clamp(lobe.green(), 0, 255);
                int colourBlue = Mth.clamp(lobe.blue(), 0, 255);
                float fade = (float) Math.max(0.0, 1.0
                    - BlastCloudRenderer.dissipationProgress(profile, ageTicks));
                if (fade <= 0.015F) continue;
                float cardSize = nuclear ? Math.min(4.6F, radius * 0.30F)
                    : Math.min(2.2F, radius * 0.34F);
                EmitterCommand command = new EmitterCommand(center, velocity, scale,
                    nuclear ? 7.5F : 8.0F, colourRed / 255.0F,
                    colourGreen / 255.0F, colourBlue / 255.0F,
                    Math.max(0.05F, lobe.opacity() * fade), cardSize,
                    radius * (stemLayer ? 0.54F : 0.72F), radius * 0.16F,
                    Math.max(4, Math.round((nuclear ? 38 : 28) * fade)),
                    folded ^ visible * 0x9E3779B9,
                    ParticleType.EXPLOSION_SMOKE, 0, stemLayer ? 1.2F : 1.4F);
                cap.add(command);
            }
            if (gpuImpactLayerReady(payloadType, VisualLayer.MUSHROOM_CLOUD))
                vfx.submitLayer(VisualLayer.MUSHROOM_CLOUD, cap);
        }
        if (!gpuImpactLayerReady(payloadType, VisualLayer.GROUND_DUST)
            || !groundEffects(effect) || dustNodes.isEmpty()) return;
        int limit = Math.min(384, dustNodes.size());
        List<EmitterCommand> dust = new ArrayList<>(limit);
        for (int visible = 0; visible < limit; visible++) {
            TerrainShockfrontNode node = dustNodes.get(
                (int) ((long) visible * dustNodes.size() / limit));
            int tint = node.tintColor();
            float red = ((tint >> 16) & 255) / 255.0F;
            float green = ((tint >> 8) & 255) / 255.0F;
            float blue = (tint & 255) / 255.0F;
            dust.add(new EmitterCommand(node.position(),
                new Vec3(0.0, 0.65 + scale * 0.25, 0.0), scale, 3.2F,
                red, green, blue, 0.55F + scale * 0.30F,
                0.65F + scale * 0.35F, 1.35F,
                nuclear ? 24 : 12, folded ^ visible * 0x45D9F3B,
                ParticleType.GROUND_DUST, 1));
        }
        vfx.submitLayer(VisualLayer.GROUND_DUST, dust);
    }

    private static void renderDebris(final LevelRenderContext context,
        final PoseStack poseStack, final RenderFrame frame) {
        if (frame.debris().isEmpty()) return;
        poseStack.pushPose();
        context.submitNodeCollector().submitCustomGeometry(poseStack,
            WarheadRenderPipelines.HEAVY_SMOKE,
            (pose, buffer) -> renderDebrisTrails(pose, buffer, frame.debris(),
                frame.cameraPosition(), frame.cameraOrientation()));
        poseStack.popPose();
        for (DebrisFrame debris : frame.debris()) {
            ClientDebrisBatchManager.RenderSample sample = debris.sample();
            double distanceSquared = frame.cameraPosition().distanceToSqr(sample.position());
            if (sample.scale() < 0.55F && distanceSquared > 64.0 * 64.0) continue;
            Vec3 relative = sample.position().subtract(frame.cameraPosition());
            poseStack.pushPose();
            poseStack.translate(relative.x, relative.y, relative.z);
            poseStack.scale(sample.scale(), sample.scale(), sample.scale());
            poseStack.mulPose(new Quaternionf().rotationXYZ(
                (float) sample.spin().x * sample.age(),
                (float) sample.spin().y * sample.age(),
                (float) sample.spin().z * sample.age()));
            poseStack.translate(-0.5, -0.5, -0.5);
            context.submitNodeCollector().submitMovingBlock(poseStack,
                debris.movingBlock(), 0);
            poseStack.popPose();
        }
    }

    private static void renderDebrisTrails(final PoseStack.Pose pose,
        final com.mojang.blaze3d.vertex.VertexConsumer buffer,
        final List<DebrisFrame> debrisFrames, final Vec3 cameraPosition,
        final Quaternionf cameraOrientation) {
        for (DebrisFrame debris : debrisFrames) {
            DebrisTrailRendererV7.render(pose, buffer, debris.sample(),
                debris.trailColour(), cameraPosition, cameraOrientation);
        }
    }

    private static CloudCluster cloudCluster(final List<ImpactFrame> impacts,
        final ImpactFrame anchor) {
        float anchorRadiusScale = WarheadYieldScaling.radiusScale(
            anchor.payloadType(), anchor.visualScale());
        double mergeRadius = 82.0 * Math.max(0.55, anchorRadiusScale);
        ArrayList<ImpactFrame> members = new ArrayList<>();
        for (ImpactFrame candidate : impacts) {
            if (candidate.payloadType() != WarheadPayloadType.NUCLEAR
                || candidate.effectProfile() != anchor.effectProfile()) continue;
            float candidateScale = WarheadYieldScaling.radiusScale(
                candidate.payloadType(), candidate.visualScale());
            double candidateMerge = Math.max(mergeRadius, 82.0 * candidateScale);
            if (Math.abs(candidate.ageTicks() - anchor.ageTicks()) > 180.0
                || candidate.position().distanceToSqr(anchor.position())
                    > candidateMerge * candidateMerge) continue;
            members.add(candidate);
        }
        if (members.isEmpty()) members.add(anchor);
        members.sort(Comparator.comparing(ImpactFrame::id));

        ImpactFrame leader = members.stream().filter(ImpactFrame::renderVolumetrics)
            .findFirst().orElse(members.getFirst());
        double volume = 0.0;
        double maximumScale = 0.01;
        ArrayList<NuclearCloudSource> sources = new ArrayList<>(members.size());
        sources.add(new NuclearCloudSource.Basic(Vec3.ZERO, leader.ageTicks(),
            leader.visualScale(), leader.visualSeed()));
        for (ImpactFrame member : members) {
            double memberScale = Math.max(0.05, member.visualScale());
            volume += memberScale * memberScale * memberScale;
            maximumScale = Math.max(maximumScale, memberScale);
            if (member.id().equals(leader.id())) continue;
            sources.add(new NuclearCloudSource.Basic(
                member.position().subtract(leader.position()), member.ageTicks(),
                member.visualScale(), member.visualSeed()));
        }
        float mergedScale = (float) Math.min(maximumScale * 1.90, Math.cbrt(volume));
        return new CloudCluster(leader.id(), leader.ageTicks(), mergedScale,
            leader.visualSeed(), leader.profile(), leader.ambientWind(),
            List.copyOf(sources));
    }

    public static DebugSnapshot debugSnapshot() {
        GpuParticleEngine.DebugSnapshot gpu = GpuParticleEngine.debugSnapshot();
        if (GpuParticleEngine.effectiveBackend()
            == GpuParticleEngine.EffectiveBackend.GPU) {
            return new DebugSnapshot((int) Math.min(Integer.MAX_VALUE, gpu.activeParticles()),
                (int) Math.min(Integer.MAX_VALUE, gpu.visibleParticles()), 0,
                (int) Math.min(Integer.MAX_VALUE, gpu.culledParticles()),
                ClientDebrisBatchManager.INSTANCE.activeFragmentCount(),
                "gpu_compute_ssbo_indirect");
        }
        ConventionalBlastVisualV5.DebugSnapshot conventional =
            ConventionalBlastVisualV5.debugSnapshot();
        ConventionalBlastParticleRenderer.DebugSnapshot returnFront =
            ConventionalBlastParticleRenderer.debugSnapshot();
        NuclearParticleCloudRenderer.DebugSnapshot nuclear =
            NuclearParticleCloudRenderer.debugSnapshot();
        return new DebugSnapshot(
            conventional.activeParticles() + returnFront.activeParticles()
                + nuclear.simulatedParticles(),
            conventional.activeParticles() + returnFront.activeParticles()
                + nuclear.representedParticles(),
            conventional.spawnedParticlesPerTick() + returnFront.spawnedParticlesPerTick()
                + nuclear.spawnedSimulatedParticlesPerTick(),
            conventional.culledParticles() + returnFront.culledParticles()
                + nuclear.culledSimulatedParticles(),
            ClientDebrisBatchManager.INSTANCE.activeFragmentCount(),
            WarheadRenderPipelines.compatibilityRendererActive()
                ? "cpu_assembled_fabric_gpu_raster_pipeline"
                : "cpu_assembled_custom_gpu_raster_pipeline");
    }

    private static void playReturnWaveSound(final ClientLevel level,
        final ImpactVisualState state, final double age, final Vec3 listener) {
        if (RETURN_WAVE_SOUND_PLAYED.contains(state.warheadId())) return;
        float radiusScale = WarheadYieldScaling.radiusScale(
            state.payloadType(), state.visualScale());
        double returnStart = WarheadVisualMath.nuclearReturnWaveStartTicks(radiusScale);
        double current = WarheadVisualMath.nuclearReturnWaveRadius(age, radiusScale);
        if (current < 0.0) {
            if (age < returnStart) {
                RETURN_WAVE_PREVIOUS_RADIUS.remove(state.warheadId());
                return;
            }
            /* A very slow frame may step across the final collapse entirely. */
            current = 0.0;
        }
        Double sampledPrevious = RETURN_WAVE_PREVIOUS_RADIUS.put(state.warheadId(), current);
        double previous = sampledPrevious == null
            ? WarheadVisualMath.nuclearReturnWaveMaximumRadius(radiusScale)
            : sampledPrevious;
        double distance = listener.distanceTo(state.impactPosition());
        /* Compare actual rendered-frame radii, not age-1. Large yields can drop
           several simulation ticks between frames; the old one-tick sample could
           skip the crossing and play late or not at all. */
        if (previous >= distance && current < distance) {
            level.playLocalSound(listener.x, listener.y, listener.z,
                ModSoundEvents.PROTOTYPE_EXPLOSION_EXTREME, SoundSource.BLOCKS,
                3.2F, 0.54F, false);
            RETURN_WAVE_SOUND_PLAYED.add(state.warheadId());
        }
    }

    private static int debrisTrailColour(
        final ClientDebrisBatchManager.RenderSample sample) {
        int hash = sample.batchId().hashCode() * 31 + sample.pieceIndex() * 0x45D9F3B;
        int selector = Math.floorMod(hash, 100);
        if (selector < 14) {
            int dark = 70 + Math.floorMod(hash >>> 8, 55);
            return dark << 16 | Math.min(132, dark + 3) << 8 | Math.min(140, dark + 9);
        }
        int tone = 202 + Math.floorMod(hash, 50);
        int blue = Math.min(255, tone + 7);
        return tone << 16 | Math.min(255, tone + 3) << 8 | blue;
    }

    private static double conventionalVisualRange(final float visualScale) {
        if (visualScale < 0.49F) return 128.0;
        if (visualScale < 0.82F) return 256.0;
        if (visualScale < 1.19F) return 384.0;
        return 512.0;
    }

    private static double visualGroundDistance(final WarheadPayloadType payloadType,
        final float visualScale, final double rawDistance) {
        if (payloadType == WarheadPayloadType.NUCLEAR) return rawDistance;
        return Math.min(rawDistance, conventionalVisualRange(visualScale));
    }

    private static float visualRangeFade(final WarheadPayloadType payloadType,
        final float visualScale, final double rawDistance) {
        if (payloadType == WarheadPayloadType.NUCLEAR) return 1.0F;
        double limit = conventionalVisualRange(visualScale);
        if (rawDistance <= limit - 24.0) return 1.0F;
        return Mth.clamp((float) ((limit + 12.0 - rawDistance) / 36.0), 0.0F, 1.0F);
    }

    private static boolean visibleSphere(final Vec3 cameraPosition,
        final Quaternionf cameraOrientation, final Vec3 center, final double radius) {
        Vec3 delta = center.subtract(cameraPosition);
        double distance = delta.length();
        if (!Double.isFinite(distance)) return false;
        if (distance <= radius * 1.25 + 16.0) return true;
        Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F)
            .rotate(cameraOrientation);
        if (forward.lengthSquared() < 1.0E-6F) return true;
        forward.normalize();
        double facing = (delta.x * forward.x + delta.y * forward.y
            + delta.z * forward.z) / Math.max(1.0E-6, distance);
        double angularAllowance = Math.min(0.74,
            radius * 1.35 / Math.max(1.0, distance));
        return facing + angularAllowance > 0.04;
    }

    private static boolean groundEffects(final WarheadEffectProfile effect) {
        return effect != WarheadEffectProfile.ANTI_AIR_INTERCEPTION
            && effect != WarheadEffectProfile.ANTI_AIR_SAFE_SELF_DESTRUCT;
    }

    private static double coneActivation(final WarheadFrame warhead) {
        double speed = WarheadVisualMath.normalizedSpeed(warhead.velocity(),
            WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK * 1.65);
        return WarheadVisualMath.coneActivation(speed)
            * WarheadVisualMath.coneAttack(
                warhead.elapsedTicks() - warhead.flightTicks() * 0.20);
    }

    private static int frontierSpokeCount(final WarheadMesh.Lod lod) {
        return lod == WarheadMesh.Lod.NEAR ? 256
            : lod == WarheadMesh.Lod.MEDIUM ? 160 : 96;
    }

    private static WarheadMesh.Lod lod(final double distance) {
        return distance < NEAR_DISTANCE ? WarheadMesh.Lod.NEAR
            : distance < MEDIUM_DISTANCE ? WarheadMesh.Lod.MEDIUM
            : WarheadMesh.Lod.FAR;
    }

    private static int sampledLight(final ClientLevel level, final Vec3 position) {
        BlockPos block = BlockPos.containing(position);
        if (!level.hasChunkAt(block)) return LightCoordsUtil.pack(5, 5);
        int packed = LightCoordsUtil.getLightCoords(level, block);
        return LightCoordsUtil.pack(
            Math.max(5, LightCoordsUtil.block(packed)),
            Math.max(5, LightCoordsUtil.sky(packed)));
    }

    private static Quaternionf rotationToVelocity(final Vec3 velocity) {
        Vector3f direction = new Vector3f((float) velocity.x,
            (float) velocity.y, (float) velocity.z);
        if (!Float.isFinite(direction.x()) || !Float.isFinite(direction.y())
            || !Float.isFinite(direction.z())
            || direction.lengthSquared() < 1.0E-8F) return new Quaternionf();
        return new Quaternionf().rotationTo(
            new Vector3f(0.0F, 1.0F, 0.0F), direction.normalize());
    }

    private static float groundFrontierWidth(final double age,
        final float scale, final float radiusScale) {
        return (float) WarheadVisualMath.airShockwaveThickness(age, scale, radiusScale);
    }

    private static float groundFrontierAlpha(final double age,
        final float scale, final float radiusScale) {
        return Mth.clamp((float) (WarheadVisualMath.groundShockwaveAlpha(age, radiusScale)
            * 0.74 * scale), 0.0F, 1.0F);
    }

    public record DebugSnapshot(int activeParticles, int representedParticles,
        int spawnedParticlesPerTick,
        int culledParticles, int activeDebrisFragments, String activeRenderBackend) { }

    private record WarheadFrame(Vec3 position, Vec3 velocity, float progress,
        float elapsedTicks, float remainingTicks, int flightTicks, long visualSeed,
        WarheadMesh.Lod lod, int packedLight) { }

    private record ImpactFrame(UUID id, Vec3 position, double ageTicks,
        float visualScale, long visualSeed, WarheadPayloadType payloadType,
        WarheadEffectProfile effectProfile, boolean renderVolumetrics,
        WarheadClientVisualProfile profile, WarheadMesh.Lod lod,
        List<TerrainShockfrontSpoke> shockfrontSpokes,
        List<TerrainShockfrontNode> dustNodes, Vec3 ambientWind, long gameTime,
        double rawGroundDistance, double groundDistance) { }

    private record CloudCluster(UUID leaderId, double ageTicks, float visualScale,
        long visualSeed, WarheadClientVisualProfile profile,
        Vec3 ambientWind, List<NuclearCloudSource> sources) { }

    private record DebrisFrame(ClientDebrisBatchManager.RenderSample sample,
        MovingBlockRenderState movingBlock, int trailColour) { }

    private record RenderFrame(Vec3 cameraPosition, Quaternionf cameraOrientation,
        List<WarheadFrame> warheads, List<ImpactFrame> impacts,
        List<DebrisFrame> debris) {
        private static final RenderFrame EMPTY = new RenderFrame(Vec3.ZERO,
            new Quaternionf(), List.of(), List.of(), List.of());
    }
}
