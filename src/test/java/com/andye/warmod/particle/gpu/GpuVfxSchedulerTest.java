package com.andye.warmod.particle.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectClass;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectDescriptor;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectSubmission;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EmitterCommand;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FrameSubmissions;
import com.andye.warmod.particle.gpu.GpuParticleEngine.ParticleType;
import com.andye.warmod.particle.gpu.GpuParticleEngine.VisualLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class GpuVfxSchedulerTest {
    private static final GpuVfxScheduler.CameraInfo CAMERA =
        new GpuVfxScheduler.CameraInfo(Vec3.ZERO, new Matrix4f(),
            1.0F, 1_920, 1_080);

    @AfterEach
    void clearLodHistory() {
        GpuVfxScheduler.clear();
    }

    @Test
    void identicalFreshInputsProduceIdenticalSchedules() {
        FrameSubmissions input = frame(effect(7L, 0.0, VisualLayer.FIREBALL,
            ringCommands(0.0, 96, 160, 0.45F, ParticleType.EXPLOSION_FIRE)));

        GpuVfxScheduler.ScheduledFrame first = schedule(input, 1.0, 41L);
        GpuVfxScheduler.clear();
        GpuVfxScheduler.ScheduledFrame second = schedule(input, 1.0, 41L);

        assertEquals(first, second);
    }

    @Test
    void criticalAllocationKeepsSimultaneousExplosionsRepresented() {
        FrameSubmissions input = frame(
            effect(1L, -0.28, VisualLayer.FIREBALL,
                ringCommands(-0.28, 48, 120, 0.38F, ParticleType.EXPLOSION_FIRE)),
            effect(2L, 0.28, VisualLayer.FIREBALL,
                ringCommands(0.28, 48, 120, 0.38F, ParticleType.EXPLOSION_FIRE)));

        List<EmitterCommand> scheduled = schedule(input, 0.002, 12L).emitters();

        assertTrue(scheduled.stream().anyMatch(command -> command.position().x < 0.0),
            "The left-hand explosion lost its critical representation");
        assertTrue(scheduled.stream().anyMatch(command -> command.position().x > 0.0),
            "The right-hand explosion lost its critical representation");
    }

    @Test
    void lowerBudgetReducesScheduledParticleRate() {
        FrameSubmissions input = frame(effect(3L, 0.0, VisualLayer.FLAMES,
            ringCommands(0.0, 256, 4_000, 0.42F, ParticleType.FIRE)));

        long lowRate = totalSpawnRate(schedule(input, 0.10, 20L));
        GpuVfxScheduler.clear();
        long highRate = totalSpawnRate(schedule(input, 1.0, 20L));

        assertTrue(lowRate > 0L);
        assertTrue(lowRate < highRate,
            () -> "Expected budget to reduce GPU allocation, low=" + lowRate
                + ", high=" + highRate);
    }

    @Test
    void aggregationCannotCreateOversizedFireSplodges() {
        FrameSubmissions input = frame(effect(4L, 0.0, VisualLayer.FLAMES,
            ringCommands(0.0, 320, 800, 0.50F, ParticleType.FIRE)));

        GpuVfxScheduler.ScheduledFrame scheduled = schedule(input, 0.08, 30L);

        assertTrue(scheduled.emitters().stream()
            .allMatch(command -> command.size() <= 0.651F),
            "Flame aggregation exceeded the 1.30x card-size ceiling");
    }

    @Test
    void offscreenEffectsAreRejectedBeforeAllocation() {
        EffectDescriptor descriptor = new EffectDescriptor(EffectClass.CONVENTIONAL,
            5L, new Vec3(4.0, 0.0, 0.5), 0.20F, 1.0F);
        EffectSubmission effect = new EffectSubmission(descriptor, Map.of(
            VisualLayer.FIREBALL,
            ringCommands(4.0, 32, 80, 0.35F, ParticleType.EXPLOSION_FIRE)));

        GpuVfxScheduler.ScheduledFrame scheduled = schedule(frame(effect), 1.0, 40L);

        assertTrue(scheduled.emitters().isEmpty());
        assertEquals(0, scheduled.visibleLayerCount());
    }

    @Test
    void emitterCountNeverExceedsGpuBufferCapacity() {
        FrameSubmissions input = frame(effect(6L, 0.0, VisualLayer.FLAMES,
            ringCommands(0.0, 5_000, 2_000, 0.30F, ParticleType.FIRE)));

        GpuVfxScheduler.ScheduledFrame scheduled = GpuVfxScheduler.schedule(input,
            CAMERA, 1.0, 50L, 0.05F, 262_144L, 1_000.0);

        assertTrue(scheduled.emitters().size()
            <= GpuVfxScheduler.MAX_SCHEDULED_EMITTERS);
    }

    @Test
    void reportedGpuBudgetMatchesSchedulerScalingAndHardEmitterCap() {
        GpuVfxScheduler.BudgetLimits base =
            GpuVfxScheduler.budgetLimits(1.0, 1.0);
        GpuVfxScheduler.BudgetLimits doubled =
            GpuVfxScheduler.budgetLimits(1.0, 2.0);

        assertEquals(base.spawnRatePerSecond() * 2.0,
            doubled.spawnRatePerSecond(), 0.001);
        assertEquals(base.fragmentCostPerSecond() * 2.0,
            doubled.fragmentCostPerSecond(), 0.001);
        assertEquals(GpuVfxScheduler.MAX_SCHEDULED_EMITTERS,
            doubled.emitterCapacity());
    }

    @Test
    void semanticLayerDiagnosticsReconcileRequestedAndAcceptedWork() {
        FrameSubmissions input = frame(effect(8L, 0.0, VisualLayer.FIREBALL,
            ringCommands(0.0, 64, 900, 0.40F, ParticleType.EXPLOSION_FIRE)));

        GpuVfxScheduler.LayerSchedule layer = schedule(input, 0.04, 60L)
            .layerSchedules().get(VisualLayer.FIREBALL);

        assertEquals(64, layer.commandsSubmitted());
        assertEquals(64, layer.emittersRequested());
        assertTrue(layer.emittersScheduled() > 0);
        assertEquals(layer.particlesRequested() - layer.particlesAccepted(),
            layer.particlesRejected());
        assertTrue(layer.particlesAccepted() <= layer.particlesRequested());
    }

    private static GpuVfxScheduler.ScheduledFrame schedule(
        final FrameSubmissions input, final double budget, final long frame) {
        return GpuVfxScheduler.schedule(input, CAMERA, 1.0, frame,
            0.05F, 262_144L, budget);
    }

    private static long totalSpawnRate(final GpuVfxScheduler.ScheduledFrame frame) {
        return frame.emitters().stream().mapToLong(EmitterCommand::spawnCount).sum();
    }

    private static FrameSubmissions frame(final EffectSubmission... effects) {
        return new FrameSubmissions(List.of(effects), List.of());
    }

    private static EffectSubmission effect(final long id, final double centerX,
        final VisualLayer layer, final List<EmitterCommand> commands) {
        EffectDescriptor descriptor = new EffectDescriptor(EffectClass.CONVENTIONAL,
            id, new Vec3(centerX, 0.0, 0.5), 0.24F, 1.0F);
        return new EffectSubmission(descriptor, Map.of(layer, commands));
    }

    private static List<EmitterCommand> ringCommands(final double centerX,
        final int count, final int spawnCount, final float size,
        final ParticleType type) {
        ArrayList<EmitterCommand> commands = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0 * index / Math.max(1, count);
            Vec3 position = new Vec3(centerX + Math.cos(angle) * 0.08,
                Math.sin(angle * 3.0) * 0.04, 0.5 + Math.sin(angle) * 0.08);
            commands.add(new EmitterCommand(position, new Vec3(0.0, 0.4, 0.0),
                1.0F, 2.0F, 1.0F, 0.45F, 0.08F, 0.9F,
                size, 0.12F, 0.08F, spawnCount, 100 + index,
                type, 0, 1.0F));
        }
        return List.copyOf(commands);
    }
}
