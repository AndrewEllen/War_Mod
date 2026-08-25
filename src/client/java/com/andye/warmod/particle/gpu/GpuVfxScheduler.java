package com.andye.warmod.particle.gpu;

import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectClass;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectDescriptor;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectSubmission;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EmitterCommand;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldCluster;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldEmber;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldPatch;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldSubmission;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FrameSubmissions;
import com.andye.warmod.particle.gpu.GpuParticleEngine.ParticleType;
import com.andye.warmod.particle.gpu.GpuParticleEngine.VisualLayer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * CPU-side admission and representation planner for the GPU VFX backend.
 * It schedules semantic effect layers instead of accepting a first-come FIFO.
 * Particle state and per-particle culling remain GPU-owned.
 */
final class GpuVfxScheduler {
    static final int MAX_SCHEDULED_EMITTERS = 4_096;
    private static final double BASE_COST_BUDGET = 115_000.0;
    private static final int STALE_LOD_FRAMES = 600;
    private static final Map<LayerKey, LodState> LOD_STATES = new HashMap<>();

    private GpuVfxScheduler() { }

    static synchronized ScheduledFrame schedule(final FrameSubmissions submissions,
        final CameraInfo camera, final double adaptiveQuality, final long frameSequence) {
        ArrayList<EffectSubmission> effects = new ArrayList<>(submissions.effects());
        for (FireFieldSubmission field : submissions.fireFields()) {
            EffectSubmission expanded = expandFireField(field, camera, frameSequence);
            if (expanded != null) effects.add(expanded);
        }

        ArrayList<LayerDemand> demands = new ArrayList<>();
        for (EffectSubmission effect : effects) {
            EffectDescriptor descriptor = effect.descriptor();
            if (descriptor == null || !descriptor.valid()
                || !camera.visible(descriptor.position(), descriptor.boundsRadius())) continue;
            double projectedDiameter = camera.projectedDiameter(
                descriptor.position(), descriptor.boundsRadius());
            double screenImportance = screenImportance(projectedDiameter, camera.viewportHeight());
            for (Map.Entry<VisualLayer, List<EmitterCommand>> entry
                : effect.layers().entrySet()) {
                List<EmitterCommand> commands = entry.getValue();
                if (commands == null || commands.isEmpty()) continue;
                VisualLayer layer = entry.getKey();
                LodState lod = lodState(new LayerKey(descriptor.effectClass(), descriptor.id(),
                    layer), projectedDiameter, frameSequence);
                double requested = commands.stream().mapToDouble(EmitterCommand::spawnCount).sum()
                    * lod.density;
                if (requested <= 0.0) continue;
                double particleCost = averageParticleCost(commands, camera);
                double weight = Math.max(0.01, layer.priority() * descriptor.temporalImportance()
                    * (0.18 + screenImportance * 0.82));
                demands.add(new LayerDemand(descriptor, layer, commands, lod,
                    requested, particleCost, weight));
            }
        }

        double remainingCost = BASE_COST_BUDGET * Mth.clamp(adaptiveQuality, 0.22, 1.35);
        remainingCost = allocatePhase(demands, remainingCost, AllocationPhase.CRITICAL);
        remainingCost = allocatePhase(demands, remainingCost, AllocationPhase.QUALITY);
        remainingCost = allocatePhase(demands, remainingCost, AllocationPhase.TARGET);
        allocatePhase(demands, remainingCost, AllocationPhase.MAXIMUM);
        allocateEmitterSlots(demands);

        ArrayList<EmitterCommand> scheduled = new ArrayList<>(MAX_SCHEDULED_EMITTERS);
        EnumMap<VisualLayer, Integer> scheduledByLayer = new EnumMap<>(VisualLayer.class);
        for (LayerDemand demand : demands) {
            if (demand.allocatedRate <= 0.0 || demand.allocatedSlots <= 0) continue;
            List<EmitterCommand> represented = represent(demand);
            scheduled.addAll(represented);
            scheduledByLayer.merge(demand.layer, represented.size(), Integer::sum);
        }
        if (scheduled.size() > MAX_SCHEDULED_EMITTERS) {
            throw new IllegalStateException("VFX scheduler exceeded emitter capacity: "
                + scheduled.size());
        }
        LOD_STATES.entrySet().removeIf(entry ->
            frameSequence - entry.getValue().lastSeenFrame > STALE_LOD_FRAMES);
        return new ScheduledFrame(List.copyOf(scheduled), Map.copyOf(scheduledByLayer),
            demands.size(), BASE_COST_BUDGET * adaptiveQuality - remainingCost);
    }

    static synchronized void clear() { LOD_STATES.clear(); }

    private static EffectSubmission expandFireField(final FireFieldSubmission field,
        final CameraInfo camera, final long frameSequence) {
        if (field == null || !field.valid() || !camera.visible(field.center(), field.radius()))
            return null;
        double projectedDiameter = camera.projectedDiameter(field.center(), field.radius());
        LayerKey flameKey = new LayerKey(EffectClass.FIRE_FIELD, field.regionId(),
            VisualLayer.FLAMES);
        LodState lod = lodState(flameKey, projectedDiameter, frameSequence);
        int cellSize = fireCellSize(lod.level);
        ArrayList<EmitterCommand> flames = new ArrayList<>();
        ArrayList<EmitterCommand> smoke = new ArrayList<>(field.clusters().size());
        float currentWeight = (float) lod.transition;
        appendAggregatedFire(field, cellSize, Math.max(0.01F, currentWeight),
            lod.density, flames, smoke);
        if (lod.previousLevel != lod.level && lod.transition < 0.999) {
            appendAggregatedFire(field, fireCellSize(lod.previousLevel),
                Math.max(0.01F, 1.0F - currentWeight), lod.density, flames, smoke);
        }
        for (FireFieldCluster cluster : field.clusters()) {
            int rate = Math.max(4, (int) Math.round(
                (55.0F + cluster.memberCount() * 2.5F) * lod.density));
            smoke.add(new EmitterCommand(cluster.position(),
                cluster.wind().scale(0.18).add(0.0, 0.82, 0.0),
                Math.max(1.0F, cluster.radius()), 6.2F,
                0.14F, 0.15F, 0.14F, 0.44F,
                Math.max(1.8F, cluster.radius() * 0.30F),
                Math.max(1.0F, cluster.radius() * 0.42F), 0.34F,
                rate, mix32(cluster.seed()), ParticleType.SMOKE, 0,
                0.9F + cluster.smoke()));
        }

        ArrayList<FireFieldEmber> rankedEmbers = new ArrayList<>(field.embers());
        rankedEmbers.sort(Comparator.comparingDouble(FireFieldEmber::importance).reversed()
            .thenComparingLong(FireFieldEmber::id));
        double emberDensity = Math.pow(lod.density, 1.45);
        ArrayList<EmitterCommand> embers = new ArrayList<>();
        for (FireFieldEmber ember : rankedEmbers) {
            double stable = unit(mix64(ember.id() ^ field.regionId()));
            double biasedDensity = Math.min(1.0, emberDensity
                * (0.55 + Math.min(2.4, ember.importance()) * 0.45));
            if (stable > biasedDensity) continue;
            embers.add(new EmitterCommand(ember.position(), ember.velocity(), 1.0F,
                0.62F, 1.0F, 0.46F, 0.07F, 0.98F,
                Math.max(0.075F, ember.size()), 0.06F, 0.24F,
                Math.max(3, Math.round(18.0F * ember.intensity())), mix32(ember.seed()),
                ParticleType.EMBER, 0, Math.max(0.1F, ember.importance())));
        }

        EffectDescriptor descriptor = new EffectDescriptor(EffectClass.FIRE_FIELD,
            field.regionId(), field.center(), field.radius(), 1.0F);
        EnumMap<VisualLayer, List<EmitterCommand>> layers = new EnumMap<>(VisualLayer.class);
        if (!flames.isEmpty()) layers.put(VisualLayer.FLAMES, List.copyOf(flames));
        if (!smoke.isEmpty()) layers.put(VisualLayer.SMOKE, List.copyOf(smoke));
        if (!embers.isEmpty()) layers.put(VisualLayer.EMBERS, List.copyOf(embers));
        return layers.isEmpty() ? null : new EffectSubmission(descriptor, Map.copyOf(layers));
    }

    private static int fireCellSize(final int lod) {
        return switch (lod) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 5;
            default -> 12;
        };
    }

    private static void appendAggregatedFire(final FireFieldSubmission field,
        final int cellSize, final float representationWeight, final double density,
        final List<EmitterCommand> flames, final List<EmitterCommand> smoke) {
        Map<Long, FireAccumulator> cells = new LinkedHashMap<>();
        for (FireFieldPatch patch : field.patches()) {
            int cellX = Math.floorDiv(Mth.floor(patch.position().x), cellSize);
            int cellY = Math.floorDiv(Mth.floor(patch.position().y),
                Math.max(1, cellSize / 2));
            int cellZ = Math.floorDiv(Mth.floor(patch.position().z), cellSize);
            cells.computeIfAbsent(cellKey(cellX, cellY, cellZ), ignored ->
                new FireAccumulator()).add(patch);
        }
        for (Map.Entry<Long, FireAccumulator> entry : cells.entrySet()) {
            FireAggregate aggregate = entry.getValue().finish(cellSize);
            int flameRate = Math.max(1, (int) Math.round(
                aggregate.flameRate() * density * representationWeight));
            flames.add(new EmitterCommand(aggregate.position(), aggregate.velocity(),
                aggregate.scale(), 0.90F + aggregate.heat() * 0.48F,
                1.0F, 0.20F + aggregate.heat() * 0.38F, 0.025F,
                0.96F * representationWeight, aggregate.flameSize(),
                aggregate.spread(), 0.52F, flameRate,
                mix32(entry.getKey() ^ field.regionId() ^ cellSize),
                ParticleType.FIRE, 0, 1.0F + aggregate.heat()));
            if (aggregate.smoke() <= 0.018F) continue;
            int smokeRate = Math.max(1, (int) Math.round(
                aggregate.smokeRate() * density * representationWeight));
            smoke.add(new EmitterCommand(aggregate.position().add(0.0,
                Math.max(0.25, aggregate.flameSize() * 0.45), 0.0),
                aggregate.velocity().scale(0.58).add(0.0, 0.62, 0.0),
                aggregate.scale(), 3.6F + aggregate.smoke() * 2.8F,
                0.15F, 0.16F, 0.15F, 0.48F * representationWeight,
                aggregate.smokeSize(), aggregate.spread() * 1.25F, 0.30F,
                smokeRate, mix32(entry.getKey() ^ field.regionId()
                    ^ 0x534D4F4BL ^ cellSize), ParticleType.SMOKE, 0,
                0.72F + aggregate.smoke()));
        }
    }

    private static double allocatePhase(final List<LayerDemand> demands,
        double remainingCost, final AllocationPhase phase) {
        if (remainingCost <= 0.0) return 0.0;
        if (phase == AllocationPhase.CRITICAL) {
            double requestedCost = 0.0;
            double totalWeight = 0.0;
            for (LayerDemand demand : demands) {
                if (demand.layer.canDisappear()) continue;
                double goal = Math.min(demand.requestedRate, Math.max(1.0,
                    phase.goal(demand.layer)));
                double initial = Math.min(goal, 12.0);
                requestedCost += initial * demand.particleCost;
                totalWeight += demand.weight;
            }
            double available = Math.min(remainingCost, requestedCost);
            for (LayerDemand demand : demands) {
                if (demand.layer.canDisappear()) continue;
                double goal = Math.min(demand.requestedRate, Math.max(1.0,
                    phase.goal(demand.layer)));
                double desired = Math.min(goal, 12.0);
                double grant = requestedCost <= available + 0.01 ? desired
                    : Math.min(desired, available * demand.weight
                        / Math.max(0.01, totalWeight) / demand.particleCost);
                demand.allocatedRate += grant;
                remainingCost -= grant * demand.particleCost;
            }
        }
        for (int pass = 0; pass < 5 && remainingCost > 0.5; pass++) {
            double totalWeight = 0.0;
            for (LayerDemand demand : demands) {
                if (demand.remaining(phase) > 0.01) totalWeight += demand.weight;
            }
            if (totalWeight <= 0.0) break;
            double before = remainingCost;
            for (LayerDemand demand : demands) {
                double need = demand.remaining(phase);
                if (need <= 0.01) continue;
                double share = before * demand.weight / totalWeight;
                double granted = Math.min(need, share / demand.particleCost);
                demand.allocatedRate += granted;
                remainingCost -= granted * demand.particleCost;
            }
            if (before - remainingCost < 0.5) break;
        }
        return Math.max(0.0, remainingCost);
    }

    private static void allocateEmitterSlots(final List<LayerDemand> demands) {
        int remaining = MAX_SCHEDULED_EMITTERS;
        for (LayerDemand demand : demands) {
            if (demand.allocatedRate <= 0.0 || demand.commands.isEmpty()) continue;
            demand.allocatedSlots = 1;
            remaining--;
        }
        while (remaining > 0) {
            double totalWeight = 0.0;
            for (LayerDemand demand : demands) {
                if (demand.allocatedSlots < demand.desiredSlots()) totalWeight += demand.weight;
            }
            if (totalWeight <= 0.0) break;
            int before = remaining;
            for (LayerDemand demand : demands) {
                int need = demand.desiredSlots() - demand.allocatedSlots;
                if (need <= 0 || remaining <= 0) continue;
                int share = Math.max(1, (int) Math.floor(before * demand.weight / totalWeight));
                int granted = Math.min(need, Math.min(share, remaining));
                demand.allocatedSlots += granted;
                remaining -= granted;
            }
            if (remaining == before) break;
        }
    }

    private static List<EmitterCommand> represent(final LayerDemand demand) {
        int slots = Math.min(demand.allocatedSlots, demand.commands.size());
        if (slots <= 0) return List.of();
        ArrayList<EmitterCommand> ordered = new ArrayList<>(demand.commands);
        if (demand.layer == VisualLayer.EMBERS) {
            ordered.sort(Comparator.comparingDouble(EmitterCommand::importance).reversed()
                .thenComparingInt(EmitterCommand::seed));
            ordered = new ArrayList<>(ordered.subList(0, slots));
        } else {
            ordered.sort(Comparator.comparingLong(command ->
                mix64(Integer.toUnsignedLong(command.seed()) ^ demand.descriptor.id())));
        }
        double ratePerSlot = demand.allocatedRate / slots;
        ArrayList<EmitterCommand> result = new ArrayList<>(slots);
        if (ordered.size() <= slots) {
            double requested = ordered.stream().mapToDouble(EmitterCommand::spawnCount).sum();
            double scale = requested <= 0.0 ? 0.0 : demand.allocatedRate / requested;
            for (EmitterCommand command : ordered) {
                int rate = Math.max(1, (int) Math.round(command.spawnCount() * scale));
                result.add(command.withSpawnCount(rate));
            }
            return result;
        }
        for (int slot = 0; slot < slots; slot++) {
            int start = (int) ((long) slot * ordered.size() / slots);
            int end = (int) ((long) (slot + 1) * ordered.size() / slots);
            result.add(aggregate(ordered.subList(start, Math.max(start + 1, end)),
                Math.max(1, (int) Math.round(ratePerSlot)), demand.layer,
                demand.descriptor.id() ^ ((long) demand.layer.ordinal() << 48) ^ slot));
        }
        return result;
    }

    private static EmitterCommand aggregate(final List<EmitterCommand> commands,
        final int rate, final VisualLayer layer, final long salt) {
        double weight = 0.0, x = 0.0, y = 0.0, z = 0.0;
        double vx = 0.0, vy = 0.0, vz = 0.0;
        double red = 0.0, green = 0.0, blue = 0.0, opacity = 0.0;
        double lifetime = 0.0, scale = 0.0, sizeArea = 0.0, spreadSq = 0.0;
        double jitter = 0.0, importance = 0.0;
        EmitterCommand first = commands.getFirst();
        for (EmitterCommand command : commands) {
            double sampleWeight = Math.max(1.0, command.spawnCount());
            weight += sampleWeight;
            x += command.position().x * sampleWeight;
            y += command.position().y * sampleWeight;
            z += command.position().z * sampleWeight;
            vx += command.velocity().x * sampleWeight;
            vy += command.velocity().y * sampleWeight;
            vz += command.velocity().z * sampleWeight;
            red += command.red() * sampleWeight;
            green += command.green() * sampleWeight;
            blue += command.blue() * sampleWeight;
            opacity += command.opacity() * sampleWeight;
            lifetime += command.lifetimeSeconds() * sampleWeight;
            scale += command.scale() * sampleWeight;
            sizeArea += command.size() * command.size() * sampleWeight;
            spreadSq += command.spread() * command.spread() * sampleWeight;
            jitter += command.velocityJitter() * sampleWeight;
            importance = Math.max(importance, command.importance());
        }
        double safe = Math.max(1.0, weight);
        Vec3 center = new Vec3(x / safe, y / safe, z / safe);
        double variance = 0.0;
        for (EmitterCommand command : commands)
            variance += center.distanceToSqr(command.position())
                * Math.max(1.0, command.spawnCount());
        variance /= safe;
        double representationRatio = commands.size();
        float combinedSize = (float) Math.min(maximumSize(commands) * 2.35,
            Math.sqrt(sizeArea / safe) * Math.pow(representationRatio, 0.22));
        float combinedOpacity = (float) Math.min(1.0,
            opacity / safe * Math.pow(representationRatio, 0.12));
        float spread = (float) Math.sqrt(Math.max(0.0, spreadSq / safe + variance));
        int seed = mix32(Integer.toUnsignedLong(first.seed()) ^ salt ^ layer.ordinal());
        return new EmitterCommand(center, new Vec3(vx / safe, vy / safe, vz / safe),
            (float) (scale / safe), (float) (lifetime / safe),
            (float) (red / safe), (float) (green / safe), (float) (blue / safe),
            combinedOpacity, Math.max(0.02F, combinedSize), spread,
            (float) (jitter / safe), rate, seed, first.type(), first.flags(),
            (float) importance);
    }

    private static float maximumSize(final List<EmitterCommand> commands) {
        float maximum = 0.0F;
        for (EmitterCommand command : commands) maximum = Math.max(maximum, command.size());
        return Math.max(0.02F, maximum);
    }

    private static double averageParticleCost(final List<EmitterCommand> commands,
        final CameraInfo camera) {
        double weight = 0.0, cost = 0.0;
        for (EmitterCommand command : commands) {
            double sampleWeight = Math.max(1.0, command.spawnCount());
            double diameter = camera.projectedDiameter(command.position(),
                Math.max(command.size(), command.spread() * 0.35F));
            double area = Math.PI * 0.25 * diameter * diameter;
            double fragmentCost = 1.0 + Math.min(48.0, area / 192.0);
            cost += fragmentCost * sampleWeight;
            weight += sampleWeight;
        }
        return cost / Math.max(1.0, weight);
    }

    private static double screenImportance(final double diameter,
        final int viewportHeight) {
        double fraction = diameter / Math.max(1.0, viewportHeight);
        return Mth.clamp(Math.sqrt(Math.max(0.0, fraction * fraction * Math.PI * 0.25))
            * 3.2, 0.02, 2.5);
    }

    private static LodState lodState(final LayerKey key, final double projectedDiameter,
        final long frameSequence) {
        LodState state = LOD_STATES.computeIfAbsent(key, ignored -> new LodState());
        int desired = desiredLod(projectedDiameter);
        if (!state.initialized) {
            state.level = desired;
            state.previousLevel = desired;
            state.density = densityFor(desired);
            state.transition = 1.0;
            state.initialized = true;
        } else if (desired > state.level) {
            double threshold = thresholdFor(desired) * 0.84;
            if (projectedDiameter < threshold) state.changeLevel(desired);
        } else if (desired < state.level) {
            double threshold = thresholdFor(state.level) * 1.18;
            if (projectedDiameter > threshold) state.changeLevel(desired);
        }
        if (state.lastSeenFrame != frameSequence && state.transition < 1.0)
            state.transition = Math.min(1.0, state.transition + 0.12);
        double targetDensity = densityFor(state.level);
        state.density += (targetDensity - state.density) * 0.14;
        state.lastSeenFrame = frameSequence;
        return state;
    }

    private static double densityFor(final int level) {
        return switch (level) {
            case 0 -> 1.0;
            case 1 -> 0.62;
            case 2 -> 0.29;
            default -> 0.10;
        };
    }

    private static int desiredLod(final double projectedDiameter) {
        if (projectedDiameter >= 220.0) return 0;
        if (projectedDiameter >= 72.0) return 1;
        if (projectedDiameter >= 18.0) return 2;
        return 3;
    }

    private static double thresholdFor(final int level) {
        return switch (level) {
            case 0 -> Double.POSITIVE_INFINITY;
            case 1 -> 220.0;
            case 2 -> 72.0;
            default -> 18.0;
        };
    }

    private static long cellKey(final int x, final int y, final int z) {
        return mix64(((long) x * 0x9E3779B97F4A7C15L)
            ^ ((long) y * 0xC2B2AE3D27D4EB4FL)
            ^ ((long) z * 0x165667B19E3779F9L));
    }

    private static long mix64(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static int mix32(final long value) {
        long mixed = mix64(value);
        return (int) (mixed ^ mixed >>> 32);
    }

    private static double unit(final long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    record CameraInfo(Vec3 position, Matrix4f viewProjection, float projectionScale,
        int viewportWidth, int viewportHeight) {
        boolean visible(final Vec3 center, final float radius) {
            Vec3 relative = center.subtract(position);
            Vector4f clip = new Vector4f((float) relative.x, (float) relative.y,
                (float) relative.z, 1.0F).mul(viewProjection);
            if (clip.w <= 0.0F) return false;
            float allowance = Math.max(0.012F,
                radius * projectionScale / Math.max(0.01F, clip.w));
            return Math.abs(clip.x) <= clip.w * (1.0F + allowance)
                && Math.abs(clip.y) <= clip.w * (1.0F + allowance)
                && clip.z >= -clip.w && clip.z <= clip.w;
        }

        double projectedDiameter(final Vec3 center, final float radius) {
            double distance = Math.max(0.25, center.distanceTo(position));
            return Math.max(0.0, radius * 2.0 * projectionScale
                * viewportHeight * 0.5 / distance);
        }
    }

    record ScheduledFrame(List<EmitterCommand> emitters,
        Map<VisualLayer, Integer> emittersByLayer, int visibleLayerCount,
        double allocatedCost) { }

    private enum AllocationPhase {
        CRITICAL, QUALITY, TARGET, MAXIMUM;
        private double goal(final VisualLayer layer) {
            return switch (this) {
                case CRITICAL -> layer.criticalMinimum();
                case QUALITY -> layer.qualityMinimum();
                case TARGET -> layer.target();
                case MAXIMUM -> layer.maximum();
            };
        }
    }

    private record LayerKey(EffectClass effectClass, long effectId, VisualLayer layer) { }

    private static final class LodState {
        private boolean initialized;
        private int level;
        private int previousLevel;
        private double density = 1.0;
        private double transition = 1.0;
        private long lastSeenFrame;

        private void changeLevel(final int replacement) {
            if (replacement == level) return;
            previousLevel = level;
            level = replacement;
            transition = 0.0;
        }
    }

    private static final class LayerDemand {
        private final EffectDescriptor descriptor;
        private final VisualLayer layer;
        private final List<EmitterCommand> commands;
        private final LodState lod;
        private final double requestedRate;
        private final double particleCost;
        private final double weight;
        private double allocatedRate;
        private int allocatedSlots;

        private LayerDemand(final EffectDescriptor descriptor, final VisualLayer layer,
            final List<EmitterCommand> commands, final LodState lod,
            final double requestedRate, final double particleCost, final double weight) {
            this.descriptor = descriptor;
            this.layer = layer;
            this.commands = commands;
            this.lod = lod;
            this.requestedRate = requestedRate;
            this.particleCost = Math.max(0.25, particleCost);
            this.weight = weight;
        }

        private double remaining(final AllocationPhase phase) {
            double goal = Math.min(requestedRate, phase.goal(layer));
            return Math.max(0.0, goal - allocatedRate);
        }

        private int desiredSlots() {
            int rateSlots = Math.max(1, (int) Math.ceil(allocatedRate / 64.0));
            return Math.min(commands.size(), rateSlots);
        }
    }

    private static final class FireAccumulator {
        private double weight, x, y, z, vx, vy, vz;
        private double heat, smoke, coverage, intensity;
        private double varianceX, varianceY, varianceZ;
        private int members;

        private void add(final FireFieldPatch patch) {
            double sampleWeight = Math.max(0.04,
                patch.coverage() * (0.35 + patch.heat() * 0.65));
            weight += sampleWeight;
            x += patch.position().x * sampleWeight;
            y += patch.position().y * sampleWeight;
            z += patch.position().z * sampleWeight;
            vx += patch.wind().x * sampleWeight;
            vy += patch.wind().y * sampleWeight;
            vz += patch.wind().z * sampleWeight;
            heat += patch.heat() * sampleWeight;
            smoke += patch.smoke() * sampleWeight;
            coverage += patch.coverage();
            intensity += patch.intensity() * sampleWeight;
            varianceX += patch.position().x * patch.position().x * sampleWeight;
            varianceY += patch.position().y * patch.position().y * sampleWeight;
            varianceZ += patch.position().z * patch.position().z * sampleWeight;
            members++;
        }

        private FireAggregate finish(final int cellSize) {
            double safe = Math.max(0.01, weight);
            Vec3 position = new Vec3(x / safe, y / safe, z / safe);
            double variance = Math.max(0.0,
                varianceX / safe - position.x * position.x
                + varianceY / safe - position.y * position.y
                + varianceZ / safe - position.z * position.z);
            float aggregateHeat = (float) (heat / safe);
            float aggregateSmoke = (float) (smoke / safe);
            float aggregateIntensity = (float) (intensity / safe);
            float volumeRadius = (float) Math.min(cellSize * 0.72,
                Math.max(0.16, Math.sqrt(Math.max(0.08, coverage)) * 0.34));
            float spread = (float) Math.min(cellSize * 0.75,
                Math.max(0.08, Math.sqrt(variance) + volumeRadius * 0.38));
            float scale = Math.max(0.18F, volumeRadius);
            float flameSize = Math.min(cellSize * 0.58F,
                Math.max(0.18F, volumeRadius * (0.82F + aggregateIntensity * 0.32F)));
            float smokeSize = Math.min(cellSize * 0.72F,
                Math.max(0.42F, volumeRadius * (1.28F + aggregateSmoke * 0.48F)));
            float flameRate = members * (38.0F + aggregateIntensity * 72.0F);
            float smokeRate = members * (14.0F + aggregateSmoke * 40.0F);
            Vec3 velocity = new Vec3(vx / safe, vy / safe, vz / safe)
                .scale(0.11).add(0.0, 1.12 + aggregateHeat * 0.86, 0.0);
            return new FireAggregate(position, velocity, scale, aggregateHeat,
                aggregateSmoke, flameSize, smokeSize, spread, flameRate, smokeRate);
        }
    }

    private record FireAggregate(Vec3 position, Vec3 velocity, float scale,
        float heat, float smoke, float flameSize, float smokeSize, float spread,
        float flameRate, float smokeRate) { }
}
