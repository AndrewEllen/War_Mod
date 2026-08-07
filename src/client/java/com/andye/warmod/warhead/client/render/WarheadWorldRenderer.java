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
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
    private static final double MAX_DISTANCE = 1536.0;
    private static final Set<UUID> RETURN_WAVE_SOUND_PLAYED = new HashSet<>();
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
        ClientLevel level = context.level();
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (level == null || camera == null || camera.pos == null) {
            currentFrame = RenderFrame.EMPTY;
            RETURN_WAVE_SOUND_PLAYED.clear();
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
        for (ImpactVisualState state : snapshot.impacts()) {
            double age = state.ageTicks(gameTime, partialTick);
            if (state.isExpired(gameTime, partialTick)) continue;
            if (state.payloadType() == WarheadPayloadType.NUCLEAR) {
                activeReturnWaveIds.add(state.warheadId());
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
            int dustLimit = Math.round((impactLod == WarheadMesh.Lod.NEAR ? 3_400
                : impactLod == WarheadMesh.Lod.MEDIUM ? 1_700 : 650) * budgetScale);
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
                state.terrainShockfrontField().snapshotSpokes(), dustNodes, gameTime,
                rawGroundDistance, groundDistance));
        }
        RETURN_WAVE_SOUND_PLAYED.retainAll(activeReturnWaveIds);

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
                "Stage8 particles={} spawned/tick={} culled={} debris={} backend={}",
                debug.activeParticles(), debug.spawnedParticlesPerTick(),
                debug.culledParticles(), debug.activeDebrisFragments(),
                debug.activeRenderBackend());
        }
    }

    private static void collectSubmits(final LevelRenderContext context) {
        RenderFrame frame = currentFrame;
        if (frame == RenderFrame.EMPTY || (frame.warheads().isEmpty()
            && frame.impacts().isEmpty() && frame.debris().isEmpty())) return;
        PoseStack poseStack = context.poseStack();
        if (poseStack == null) return;
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
        }
        poseStack.popPose();

        CloudCluster cloud = impact.payloadType() == WarheadPayloadType.NUCLEAR
            ? cloudCluster(frame.impacts(), impact) : null;
        boolean renderCloud = impact.renderVolumetrics()
            && (cloud == null || cloud.leaderId().equals(impact.id()));

        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);
        if (groundEffects(impact.effectProfile())) {
            context.submitNodeCollector().submitCustomGeometry(poseStack,
                WarheadRenderPipelines.GROUND_DUST,
                (pose, buffer) -> GroundDustFrontRenderer.render(pose, buffer,
                    impact.dustNodes(), impact.position(), impact.gameTime(), impact.lod(),
                    (float) impact.profile().shockwaveParticleDensityScale()
                        * yieldThicknessScale,
                    frame.cameraOrientation()));
            if (impact.payloadType() != WarheadPayloadType.NUCLEAR) {
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.EXPLOSION_PUFF,
                    (pose, buffer) -> GroundDustFrontRenderer.renderExplosionFlecks(
                        pose, buffer, impact.dustNodes(), impact.position(), impact.gameTime(),
                        impact.lod(), (float) impact.profile().shockwaveParticleDensityScale()
                            * yieldThicknessScale,
                        frame.cameraOrientation()));
            }
        }

        if (renderCloud) {
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
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.FIREBALL_HOT,
                    (pose, buffer) -> NuclearCentralColumnRenderer.renderFire(pose, buffer,
                        cloud.ageTicks(), cloud.visualScale(), cloud.visualSeed(), impact.lod(),
                        true, frame.cameraOrientation()));
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.FIREBALL_HOT,
                    (pose, buffer) -> NuclearParticleCloudRenderer.renderFire(pose, buffer,
                        cloud.ageTicks(), cloud.visualScale(), cloud.profile(), cloud.visualSeed(),
                        impact.lod(), true, cloud.sources(), frame.cameraOrientation()));
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.FIREBALL_COOL,
                    (pose, buffer) -> NuclearCentralColumnRenderer.renderFire(pose, buffer,
                        cloud.ageTicks(), cloud.visualScale(), cloud.visualSeed(), impact.lod(),
                        false, frame.cameraOrientation()));
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.FIREBALL_COOL,
                    (pose, buffer) -> NuclearParticleCloudRenderer.renderFire(pose, buffer,
                        cloud.ageTicks(), cloud.visualScale(), cloud.profile(), cloud.visualSeed(),
                        impact.lod(), false, cloud.sources(), frame.cameraOrientation()));
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.NUCLEAR_SMOKE,
                    (pose, buffer) -> NuclearParticleCloudRenderer.renderSmoke(pose, buffer,
                        cloud.ageTicks(), cloud.visualScale(), cloud.profile(), cloud.visualSeed(),
                        impact.lod(), cloud.sources(), frame.cameraOrientation()));
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.NUCLEAR_SMOKE,
                    (pose, buffer) -> NuclearCentralColumnRenderer.renderSmoke(pose, buffer,
                        cloud.ageTicks(), cloud.visualScale(), cloud.visualSeed(), impact.lod(),
                        frame.cameraOrientation()));
            }
            double returnRadius = WarheadVisualMath.nuclearReturnWaveRadius(
                impact.ageTicks(), yieldRadiusScale);
            if (returnRadius > 0.0) {
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.GROUND_DUST,
                    (pose, buffer) -> ConventionalBlastParticleRenderer
                        .renderNuclearReturnFront(pose, buffer, impact.ageTicks(),
                            returnRadius, yieldRadiusScale, impact.visualSeed(), impact.lod(),
                            frame.cameraOrientation()));
            }
            if (renderCloud || cloud.sources().size() == 1) {
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    WarheadRenderPipelines.NUCLEAR_FLASH,
                    (pose, buffer) -> NuclearFlashRenderer.render(pose, buffer,
                        impact.ageTicks(), frame.cameraOrientation()));
            }
        } else if (renderCloud) {
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
        poseStack.popPose();
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
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(cameraOrientation);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(cameraOrientation);
        Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F).rotate(cameraOrientation);
        for (DebrisFrame debris : debrisFrames) {
            ClientDebrisBatchManager.RenderSample sample = debris.sample();
            if (sample.partIndex() != 0 || sample.trailPositions().size() < 2) continue;
            List<Vec3> trail = sample.trailPositions();
            int red = debris.trailColour() >>> 16 & 255;
            int green = debris.trailColour() >>> 8 & 255;
            int blue = debris.trailColour() & 255;
            for (int point = 1; point < trail.size(); point++) {
                Vec3 start = trail.get(point - 1);
                Vec3 end = trail.get(point);
                double segmentLength = start.distanceTo(end);
                int subdivisions = Math.max(1,
                    Math.min(6, (int) Math.ceil(segmentLength / 0.32)));
                for (int subdivision = 0; subdivision < subdivisions; subdivision++) {
                    double t = (subdivision + 0.5) / subdivisions;
                    Vec3 center = start.lerp(end, t).subtract(cameraPosition);
                    float head = (point - 1 + (float) t)
                        / Math.max(1.0F, trail.size() - 1.0F);
                    float radius = (0.12F + 0.29F * head)
                        * Math.max(0.78F, sample.scale());
                    float alpha = (0.13F + 0.50F * head)
                        * (sample.onGround() ? 0.42F : 1.0F);
                    addDebrisBillboard(pose, buffer, center, radius,
                        red, green, blue, alpha, right, up, normal);
                }
            }
        }
    }

    private static void addDebrisBillboard(final PoseStack.Pose pose,
        final com.mojang.blaze3d.vertex.VertexConsumer buffer,
        final Vec3 center, final float radius, final int red, final int green,
        final int blue, final float alpha, final Vector3f right,
        final Vector3f up, final Vector3f normal) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        debrisVertex(pose, buffer, center, -radius, -radius, 0.0F, 1.0F,
            red, green, blue, a, right, up, normal);
        debrisVertex(pose, buffer, center, -radius, radius, 0.0F, 0.0F,
            red, green, blue, a, right, up, normal);
        debrisVertex(pose, buffer, center, radius, radius, 1.0F, 0.0F,
            red, green, blue, a, right, up, normal);
        debrisVertex(pose, buffer, center, radius, -radius, 1.0F, 1.0F,
            red, green, blue, a, right, up, normal);
    }

    private static void debrisVertex(final PoseStack.Pose pose,
        final com.mojang.blaze3d.vertex.VertexConsumer buffer,
        final Vec3 center, final float x, final float y, final float u,
        final float v, final int red, final int green, final int blue,
        final int alpha, final Vector3f right, final Vector3f up,
        final Vector3f normal) {
        float ox = right.x * x + up.x * y;
        float oy = right.y * x + up.y * y;
        float oz = right.z * x + up.z * y;
        buffer.addVertex(pose, (float) center.x + ox, (float) center.y + oy,
                (float) center.z + oz)
            .setColor(red, green, blue, alpha).setUv(u, v).setOverlay(0)
            .setLight(0xB000B0).setNormal(pose, normal.x, normal.y, normal.z);
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
            leader.visualSeed(), leader.profile(), List.copyOf(sources));
    }

    public static DebugSnapshot debugSnapshot() {
        ConventionalBlastVisualV5.DebugSnapshot conventional =
            ConventionalBlastVisualV5.debugSnapshot();
        ConventionalBlastParticleRenderer.DebugSnapshot returnFront =
            ConventionalBlastParticleRenderer.debugSnapshot();
        NuclearParticleCloudRenderer.DebugSnapshot nuclear =
            NuclearParticleCloudRenderer.debugSnapshot();
        return new DebugSnapshot(
            conventional.activeParticles() + returnFront.activeParticles()
                + nuclear.activeParticles(),
            conventional.spawnedParticlesPerTick() + returnFront.spawnedParticlesPerTick()
                + nuclear.spawnedParticlesPerTick(),
            conventional.culledParticles() + returnFront.culledParticles()
                + nuclear.culledParticles(),
            ClientDebrisBatchManager.INSTANCE.activeFragmentCount(),
            WarheadRenderPipelines.compatibilityRendererActive()
                ? "fabric_entity_pipeline_external_renderer"
                : "war_mod_custom_pipeline");
    }

    private static void playReturnWaveSound(final ClientLevel level,
        final ImpactVisualState state, final double age, final Vec3 listener) {
        if (RETURN_WAVE_SOUND_PLAYED.contains(state.warheadId())) return;
        float radiusScale = WarheadYieldScaling.radiusScale(
            state.payloadType(), state.visualScale());
        double previous = WarheadVisualMath.nuclearReturnWaveRadius(
            Math.max(0.0, age - 1.0), radiusScale);
        double current = WarheadVisualMath.nuclearReturnWaveRadius(age, radiusScale);
        double distance = listener.distanceTo(state.impactPosition());
        if (previous >= 0.0 && current >= 0.0
            && previous >= distance && current < distance) {
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
        double angularAllowance = Math.min(0.82, radius / Math.max(1.0, distance));
        return facing + angularAllowance > -0.08;
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

    public record DebugSnapshot(int activeParticles, int spawnedParticlesPerTick,
        int culledParticles, int activeDebrisFragments, String activeRenderBackend) { }

    private record WarheadFrame(Vec3 position, Vec3 velocity, float progress,
        float elapsedTicks, float remainingTicks, int flightTicks, long visualSeed,
        WarheadMesh.Lod lod, int packedLight) { }

    private record ImpactFrame(UUID id, Vec3 position, double ageTicks,
        float visualScale, long visualSeed, WarheadPayloadType payloadType,
        WarheadEffectProfile effectProfile, boolean renderVolumetrics,
        WarheadClientVisualProfile profile, WarheadMesh.Lod lod,
        List<TerrainShockfrontSpoke> shockfrontSpokes,
        List<TerrainShockfrontNode> dustNodes, long gameTime,
        double rawGroundDistance, double groundDistance) { }

    private record CloudCluster(UUID leaderId, double ageTicks, float visualScale,
        long visualSeed, WarheadClientVisualProfile profile,
        List<NuclearCloudSource> sources) { }

    private record DebrisFrame(ClientDebrisBatchManager.RenderSample sample,
        MovingBlockRenderState movingBlock, int trailColour) { }

    private record RenderFrame(Vec3 cameraPosition, Quaternionf cameraOrientation,
        List<WarheadFrame> warheads, List<ImpactFrame> impacts,
        List<DebrisFrame> debris) {
        private static final RenderFrame EMPTY = new RenderFrame(Vec3.ZERO,
            new Quaternionf(), List.of(), List.of(), List.of());
    }
}
