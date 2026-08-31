package com.andye.warmod.particle.gpu;

import com.andye.warmod.fire.FireRepresentationPlan.Card;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectClass;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectDescriptor;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EffectSubmission;
import com.andye.warmod.particle.gpu.GpuParticleEngine.EmitterCommand;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldCell;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldEmber;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FireFieldSubmission;
import com.andye.warmod.particle.gpu.GpuParticleEngine.FrameSubmissions;
import com.andye.warmod.particle.gpu.GpuParticleEngine.ParticleType;
import com.andye.warmod.particle.gpu.GpuParticleEngine.VisualLayer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

/**
 * CPU-side admission and representation planner for the GPU VFX backend.
 * It schedules semantic effect layers instead of accepting a first-come FIFO.
 * Particle state and per-particle culling remain GPU-owned.
 */
final class GpuVfxScheduler {
    static final int MAX_SCHEDULED_EMITTERS = 4_096;
    static final int PROTECTED_TRANSIENT_PARTICLE_SLOTS = 32_768;
    static final int PROTECTED_FIRE_PARTICLE_SLOTS = 32_768;
    private static final double BASE_SPAWN_RATE_BUDGET = 155_250.0;
    private static final double BASE_FRAGMENT_COST_BUDGET = 115_000.0;
    private static final int FIRE_SURFACE_DISTRIBUTION_FLAG = 1;
    private static final int STALE_LOD_FRAMES = 600;
    private static final Map<LayerKey, LodState> LOD_STATES = new HashMap<>();

    private GpuVfxScheduler() { }

    static synchronized ScheduledFrame schedule(final FrameSubmissions submissions,
        final CameraInfo camera, final double adaptiveQuality, final long frameSequence,
        final float deltaSeconds, final long deadSlots, final double budgetScale) {
        ArrayList<EffectSubmission> effects = new ArrayList<>(submissions.effects());
        for (FireFieldSubmission field : submissions.fireFields()) {
            effects.addAll(expandFireField(field, camera));
        }

        ArrayList<LayerDemand> demands = new ArrayList<>();
        EnumMap<VisualLayer, MutableLayerSchedule> layerSchedules =
            new EnumMap<>(VisualLayer.class);
        for (EffectSubmission effect : effects) {
            EffectDescriptor descriptor = effect.descriptor();
            if (descriptor == null || !descriptor.valid()) continue;
            if (!camera.visible(descriptor.position(), descriptor.boundsRadius())) {
                for (Map.Entry<VisualLayer, List<EmitterCommand>> entry
                    : effect.layers().entrySet()) {
                    List<EmitterCommand> submitted = entry.getValue();
                    if (submitted == null || submitted.isEmpty()) continue;
                    MutableLayerSchedule rejected = layerSchedules.computeIfAbsent(
                        entry.getKey(), ignored -> new MutableLayerSchedule());
                    long particles = Math.round(submitted.stream()
                        .mapToDouble(EmitterCommand::spawnCount).sum());
                    rejected.commandsSubmitted += submitted.size();
                    rejected.emittersRequested += submitted.size();
                    rejected.particlesRequested += particles;
                    rejected.frustumCulled += submitted.size();
                }
                continue;
            }
            double projectedDiameter = camera.projectedDiameter(
                descriptor.position(), descriptor.boundsRadius());
            double screenImportance = screenImportance(projectedDiameter, camera.viewportHeight());
            for (Map.Entry<VisualLayer, List<EmitterCommand>> entry
                : effect.layers().entrySet()) {
                VisualLayer layer = entry.getKey();
                List<EmitterCommand> submitted = entry.getValue();
                if (submitted == null || submitted.isEmpty()) continue;
                List<EmitterCommand> commands = submitted.stream()
                    .map(command -> command.withSemanticLayer(layer)).toList();
                LayerKey layerKey = new LayerKey(descriptor.effectClass(), descriptor.id(), layer);
                LodState lod = descriptor.effectClass() == EffectClass.FIRE_FIELD
                    ? LodState.full(frameSequence)
                    : lodState(layerKey, projectedDiameter, frameSequence);
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

        BudgetLimits limits = budgetLimits(adaptiveQuality, budgetScale);
        double liveSlotRate = Math.max(0.0, deadSlots) / Math.max(0.001, deltaSeconds);
        boolean hasCriticalTransient = demands.stream().anyMatch(demand ->
            demand.admission == AdmissionClass.CRITICAL_TRANSIENT);
        boolean hasPersistentFire = demands.stream().anyMatch(demand ->
            demand.admission == AdmissionClass.PERSISTENT_WORLD);
        double persistentLiveSlotRate = Math.max(0.0,
            deadSlots - (hasCriticalTransient ? PROTECTED_TRANSIENT_PARTICLE_SLOTS : 0))
            / Math.max(0.001, deltaSeconds);
        double transientLiveSlotRate = Math.max(0.0,
            deadSlots - (hasPersistentFire ? PROTECTED_FIRE_PARTICLE_SLOTS : 0))
            / Math.max(0.001, deltaSeconds);
        BudgetState budget = new BudgetState(
            limits.spawnRatePerSecond(),
            limits.fragmentCostPerSecond(),
            liveSlotRate, transientLiveSlotRate, persistentLiveSlotRate,
            (hasCriticalTransient ? PROTECTED_TRANSIENT_PARTICLE_SLOTS : 0)
                / Math.max(0.001, deltaSeconds),
            (hasPersistentFire ? PROTECTED_FIRE_PARTICLE_SLOTS : 0)
                / Math.max(0.001, deltaSeconds));
        allocateGuaranteedFloors(demands, budget);
        for (AdmissionClass admission : AdmissionClass.values())
            allocatePhase(demands, budget, AllocationPhase.CRITICAL, admission);
        budget.releaseUnusedReservations();
        for (AllocationPhase phase : List.of(AllocationPhase.QUALITY,
            AllocationPhase.TARGET, AllocationPhase.MAXIMUM)) {
            for (AdmissionClass admission : AdmissionClass.values())
                allocatePhase(demands, budget, phase, admission);
        }
        allocateEmitterSlots(demands);

        ArrayList<EmitterCommand> scheduled = new ArrayList<>(MAX_SCHEDULED_EMITTERS);
        EnumMap<VisualLayer, Integer> scheduledByLayer = new EnumMap<>(VisualLayer.class);
        for (LayerDemand demand : demands) {
            MutableLayerSchedule layerSchedule = layerSchedules.computeIfAbsent(
                demand.layer, ignored -> new MutableLayerSchedule());
            layerSchedule.commandsSubmitted += demand.commands.size();
            layerSchedule.emittersRequested += demand.commands.size();
            layerSchedule.particlesRequested += Math.round(demand.requestedRate);
            layerSchedule.particlesAccepted += Math.round(demand.allocatedRate);
            if (demand.allocatedRate <= 0.0 || demand.allocatedSlots <= 0) continue;
            List<EmitterCommand> represented = represent(demand);
            scheduled.addAll(represented);
            scheduledByLayer.merge(demand.layer, represented.size(), Integer::sum);
            layerSchedule.emittersScheduled += represented.size();
        }
        if (scheduled.size() > MAX_SCHEDULED_EMITTERS) {
            throw new IllegalStateException("VFX scheduler exceeded emitter capacity: "
                + scheduled.size());
        }
        LOD_STATES.entrySet().removeIf(entry ->
            frameSequence - entry.getValue().lastSeenFrame > STALE_LOD_FRAMES);
        EnumMap<VisualLayer, LayerSchedule> immutableLayerSchedules =
            new EnumMap<>(VisualLayer.class);
        layerSchedules.forEach((layer, values) -> immutableLayerSchedules.put(layer,
            values.snapshot()));
        return new ScheduledFrame(List.copyOf(scheduled), Map.copyOf(scheduledByLayer),
            Map.copyOf(immutableLayerSchedules), demands.size(),
            budget.allocatedFragmentCost);
    }

    static synchronized void clear() { LOD_STATES.clear(); }

    static BudgetLimits budgetLimits(final double adaptiveQuality,
        final double budgetScale) {
        double quality = Mth.clamp(adaptiveQuality, 0.22, 1.35);
        double scale = Math.max(0.001, budgetScale);
        return new BudgetLimits(BASE_SPAWN_RATE_BUDGET * quality * scale,
            BASE_FRAGMENT_COST_BUDGET * quality * scale,
            MAX_SCHEDULED_EMITTERS, PROTECTED_TRANSIENT_PARTICLE_SLOTS,
            PROTECTED_FIRE_PARTICLE_SLOTS);
    }

    private static List<EffectSubmission> expandFireField(final FireFieldSubmission field,
        final CameraInfo camera) {
        if (field == null || !field.valid() || !camera.visible(field.center(), field.radius()))
            return List.of();
        ArrayList<EmitterCommand> flames = new ArrayList<>();
        ArrayList<EmitterCommand> smoke = new ArrayList<>();
        for (FireFieldCell cell : field.cells()) {
            if (!cell.valid() || !camera.visible(cell.position(), cell.boundsRadius())) continue;
            Vec3 normal = cell.surfaceNormal().normalize();
            boolean exactPatch = cell.representation().exactPatch();
            if (!cell.plan().flames().isEmpty()) {
                int cardIndex = 0;
                for (Card card : cell.plan().flames()) {
                    long tongueSeed = mix64(cell.seed() ^ card.seed()
                        ^ cardIndex++ * 0x9E3779B97F4A7C15L);
                    float lifetime = 0.60F + (float) unit(tongueSeed, 0) * 0.80F;
                    float envelope = Math.max(card.radius() * 1.6F,
                        cell.flameEnvelopeHeight());
                    Vec3 position = card.position().add(normal.scale(exactPatch ? 0.035 : 0.015));
                    Vec3 velocity = cell.wind().scale(0.10 + lifetime * 0.035)
                        .add(normal.scale(exactPatch ? 0.10 + cell.heat() * 0.08 : 0.04))
                        .add(0.0, 0.18 + envelope / Math.max(1.2F, lifetime * 3.4F), 0.0);
                    flames.add(new EmitterCommand(position, velocity, 1.0F,
                        lifetime, 1.0F, 0.18F + cell.heat() * 0.42F, 0.018F,
                        Math.min(0.98F, card.opacity()), card.radius(),
                        Math.max(0.025F, card.radius() * (exactPatch ? 0.34F : 0.52F)),
                        0.08F + cell.heat() * 0.08F,
                        exactPatch ? 2 : 1,
                        mix32(tongueSeed), ParticleType.FIRE,
                        FIRE_SURFACE_DISTRIBUTION_FLAG,
                        1.0F + cell.heat(), VisualLayer.FLAMES, normal, 2));
                }
            }
            if (!cell.plan().smoke().isEmpty()) {
                int cardIndex = 0;
                for (Card card : cell.plan().smoke()) {
                    long smokeSeed = mix64(cell.seed() ^ card.seed()
                        ^ cardIndex++ * 0xD1B54A32D192ED03L);
                    float lifetime = 3.5F + (float) unit(smokeSeed, 1) * 1.7F;
                    smoke.add(new EmitterCommand(card.position().add(normal.scale(0.06))
                        .add(0.0, Math.max(0.12, cell.flameEnvelopeHeight() * 0.18), 0.0),
                        cell.wind().scale(0.16 + lifetime * 0.012)
                            .add(0.0, 0.46 + cell.heat() * 0.24, 0.0), 1.0F,
                        lifetime, 0.15F, 0.16F, 0.15F,
                        Math.min(0.72F, card.opacity()), card.radius(),
                        Math.max(0.08F, card.radius() * 0.52F), 0.12F,
                        exactPatch ? 1 : 1, mix32(smokeSeed), ParticleType.SMOKE,
                        FIRE_SURFACE_DISTRIBUTION_FLAG,
                        0.74F + cell.plan().representedSmokeOpticalDepth(),
                        VisualLayer.SMOKE, normal, 0));
                }
            }
        }

        ArrayList<FireFieldEmber> rankedEmbers = new ArrayList<>(field.embers());
        rankedEmbers.sort(Comparator.comparingDouble(FireFieldEmber::importance).reversed()
            .thenComparingLong(FireFieldEmber::id));
        ArrayList<EmitterCommand> embers = new ArrayList<>();
        for (FireFieldEmber ember : rankedEmbers) {
            embers.add(new EmitterCommand(ember.position(), ember.velocity(), 1.0F,
                0.62F, 1.0F, 0.46F, 0.07F, 0.98F,
                Math.max(0.075F, ember.size()), 0.06F, 0.24F,
                Math.max(3, Math.round(18.0F * ember.intensity())), mix32(ember.seed()),
                ParticleType.EMBER, 0, Math.max(0.1F, ember.importance())));
        }
        EnumMap<VisualLayer, List<EmitterCommand>> layers =
            new EnumMap<>(VisualLayer.class);
        if (!flames.isEmpty()) layers.put(VisualLayer.FLAMES, List.copyOf(flames));
        if (!smoke.isEmpty()) layers.put(VisualLayer.SMOKE, List.copyOf(smoke));
        if (!embers.isEmpty()) layers.put(VisualLayer.EMBERS, List.copyOf(embers));
        if (layers.isEmpty()) return List.of();
        return List.of(new EffectSubmission(new EffectDescriptor(EffectClass.FIRE_FIELD,
            mix64(field.regionId() ^ 0x464952455F464945L), field.center(),
            field.radius(), 1.0F), Map.copyOf(layers)));
    }

    private static int compactRate(final int representatives, final int perRepresentative) {
        return Math.max(1, Math.min(4_096,
            Math.max(1, representatives) * Math.max(1, perRepresentative)));
    }

    private static float representativeRadius(final List<Card> cards,
        final float fallback) {
        if (cards == null || cards.isEmpty()) return fallback;
        double area = 0.0;
        for (Card card : cards) area += card.radius() * card.radius();
        return (float)Math.max(fallback, Math.sqrt(area / cards.size()));
    }

    private static float representativeOpacity(final List<Card> cards,
        final float maximum) {
        if (cards == null || cards.isEmpty()) return 0.0F;
        double transmittance = 1.0;
        for (Card card : cards) transmittance *= 1.0 - Mth.clamp(card.opacity(), 0.0F, 0.98F);
        double combined = 1.0 - transmittance;
        return (float)Math.min(maximum, Math.max(0.02, combined));
    }

    private static float compactSpread(final FireFieldCell cell, final float radius,
        final boolean smoke) {
        float scale = cell.representation().exactPatch() ? 0.42F
            : switch (cell.representation()) {
                case PATCH -> 0.42F;
                case HOST -> 0.48F;
                case LOCAL -> 0.58F;
                case FAR -> 0.72F;
                case HORIZON -> 0.82F;
            };
        return Math.max(smoke ? 0.08F : 0.04F,
            Math.min(cell.boundsRadius() * scale, radius * (smoke ? 2.4F : 1.9F)));
    }

    /**
     * Allocates one global budget per admission class and semantic layer, then
     * distributes that budget among effects. Hundreds of fire cells therefore
     * cannot each claim an independent FLAMES or SMOKE minimum.
     */
    private static void allocateGuaranteedFloors(final List<LayerDemand> demands,
        final BudgetState budget) {
        ArrayList<LayerDemand> protectedDemands = new ArrayList<>();
        double requestedRate = 0.0;
        double requestedCost = 0.0;
        for (LayerDemand demand : demands) {
            if (demand.admission != AdmissionClass.CRITICAL_TRANSIENT
                && demand.admission != AdmissionClass.PERSISTENT_WORLD) continue;
            double foothold = Math.min(demand.requestedRate,
                demand.admission == AdmissionClass.CRITICAL_TRANSIENT ? 4.0 : 2.0);
            if (foothold <= 0.0) continue;
            protectedDemands.add(demand);
            requestedRate += foothold;
            requestedCost += foothold * demand.particleCost;
        }
        double scale = budget.globalFairScale(requestedRate, requestedCost);
        for (LayerDemand demand : protectedDemands) {
            double foothold = Math.min(demand.requestedRate,
                demand.admission == AdmissionClass.CRITICAL_TRANSIENT ? 4.0 : 2.0);
            demand.allocatedRate += budget.grantGuaranteed(foothold * scale,
                demand.particleCost, demand.admission);
        }
    }

    private static void allocatePhase(final List<LayerDemand> demands,
        final BudgetState budget, final AllocationPhase phase,
        final AdmissionClass admission) {
        if (budget.exhausted(admission)) return;
        EnumMap<VisualLayer, ArrayList<LayerDemand>> groups =
            new EnumMap<>(VisualLayer.class);
        for (LayerDemand demand : demands) {
            if (demand.admission != admission) continue;
            groups.computeIfAbsent(demand.layer, ignored -> new ArrayList<>()).add(demand);
        }
        ArrayList<Map.Entry<VisualLayer, ArrayList<LayerDemand>>> ordered =
            new ArrayList<>(groups.entrySet());
        ordered.sort(Comparator.comparingDouble(
            (Map.Entry<VisualLayer, ArrayList<LayerDemand>> entry) ->
                entry.getKey().priority()).reversed()
            .thenComparingInt(entry -> entry.getKey().ordinal()));

        for (Map.Entry<VisualLayer, ArrayList<LayerDemand>> entry : ordered) {
            if (budget.exhausted(admission)) break;
            VisualLayer layer = entry.getKey();
            ArrayList<LayerDemand> group = entry.getValue();
            double requested = group.stream().mapToDouble(demand -> demand.requestedRate).sum();
            double allocated = group.stream().mapToDouble(demand -> demand.allocatedRate).sum();
            double groupGoal = Math.min(requested, Math.max(0.0, phase.goal(layer)));
            if (groupGoal - allocated <= 0.01) continue;

            /* Give simultaneous critical effects a small fair foothold before
               screen importance distributes the rest of the layer minimum. */
            if (phase == AllocationPhase.CRITICAL
                && admission == AdmissionClass.CRITICAL_TRANSIENT) {
                double foothold = Math.min(12.0,
                    Math.max(0.0, groupGoal - allocated) / Math.max(1, group.size()));
                double footholdRate = 0.0;
                double footholdCost = 0.0;
                for (LayerDemand demand : group) {
                    double need = Math.min(foothold,
                        Math.max(0.0, demand.requestedRate - demand.allocatedRate));
                    footholdRate += need;
                    footholdCost += need * demand.particleCost;
                }
                double fairScale = budget.fairScale(footholdRate, footholdCost, admission);
                for (LayerDemand demand : group) {
                    if (budget.exhausted(admission)) break;
                    double need = Math.min(foothold,
                        Math.max(0.0, demand.requestedRate - demand.allocatedRate));
                    demand.allocatedRate += budget.grant(need * fairScale, demand.particleCost,
                        admission);
                }
            }

            for (int pass = 0; pass < 5 && !budget.exhausted(admission); pass++) {
                allocated = group.stream().mapToDouble(demand -> demand.allocatedRate).sum();
                double remainingGoal = groupGoal - allocated;
                if (remainingGoal <= 0.01) break;
                double totalWeight = 0.0;
                for (LayerDemand demand : group) {
                    if (demand.requestedRate - demand.allocatedRate > 0.01)
                        totalWeight += demand.weight;
                }
                if (totalWeight <= 0.0) break;
                double granted = 0.0;
                for (LayerDemand demand : group) {
                    double need = demand.requestedRate - demand.allocatedRate;
                    if (need <= 0.01) continue;
                    double share = remainingGoal * demand.weight / totalWeight;
                    double amount = budget.grant(Math.min(need, share),
                        demand.particleCost, admission);
                    demand.allocatedRate += amount;
                    granted += amount;
                }
                if (granted < 0.01) break;
            }
        }
    }

    private static void allocateEmitterSlots(final List<LayerDemand> demands) {
        int remaining = MAX_SCHEDULED_EMITTERS;
        ArrayList<LayerDemand> active = new ArrayList<>();
        for (LayerDemand demand : demands)
            if (demand.allocatedRate > 0.0 && !demand.commands.isEmpty()) active.add(demand);
        active.sort(Comparator.comparingInt((LayerDemand demand) -> demand.admission.ordinal())
            .thenComparing(Comparator.comparingDouble(
                (LayerDemand demand) -> demand.weight).reversed())
            .thenComparingLong(demand -> demand.descriptor.id())
            .thenComparingInt(demand -> demand.layer.ordinal()));
        ArrayList<LayerDemand> critical = admitted(active,
            AdmissionClass.CRITICAL_TRANSIENT);
        ArrayList<LayerDemand> persistent = admitted(active,
            AdmissionClass.PERSISTENT_WORLD);
        /* Guaranteed classes are interleaved. A maximum wildfire cannot consume
           the critical explosion floor, and simultaneous explosions cannot erase
           every emitter belonging to a persistent fire region. */
        for (int index = 0; remaining > 0
            && (index < critical.size() || index < persistent.size()); index++) {
            if (index < critical.size() && remaining > 0) {
                critical.get(index).allocatedSlots = 1;
                remaining--;
            }
            if (index < persistent.size() && remaining > 0) {
                persistent.get(index).allocatedSlots = 1;
                remaining--;
            }
        }
        boolean protectedProgress = true;
        while (remaining > 0 && protectedProgress) {
            protectedProgress = false;
            for (int index = 0; remaining > 0
                && (index < critical.size() || index < persistent.size()); index++) {
                if (index < critical.size()) {
                    LayerDemand demand = critical.get(index);
                    if (demand.allocatedSlots < demand.topologyFloor()) {
                        demand.allocatedSlots++; remaining--; protectedProgress = true;
                    }
                }
                if (remaining > 0 && index < persistent.size()) {
                    LayerDemand demand = persistent.get(index);
                    if (demand.allocatedSlots < demand.topologyFloor()) {
                        demand.allocatedSlots++; remaining--; protectedProgress = true;
                    }
                }
            }
        }
        for (AdmissionClass admission : List.of(AdmissionClass.IMPORTANT_TRANSIENT,
            AdmissionClass.AMBIENT_DETAIL)) {
            ArrayList<LayerDemand> admitted = admitted(active, admission);
            for (LayerDemand demand : admitted) {
                if (remaining <= 0) break;
                demand.allocatedSlots = 1;
                remaining--;
            }
            boolean progress = true;
            while (remaining > 0 && progress) {
                progress = false;
                for (LayerDemand demand : admitted) {
                    if (remaining <= 0) break;
                    if (demand.allocatedSlots >= demand.topologyFloor()) continue;
                    demand.allocatedSlots++;
                    remaining--;
                    progress = true;
                }
            }
        }
        for (AdmissionClass admission : AdmissionClass.values()) {
            if (remaining <= 0) break;
            ArrayList<LayerDemand> admitted = new ArrayList<>();
            for (LayerDemand demand : active) {
                if (demand.admission == admission) admitted.add(demand);
            }
            while (remaining > 0) {
                double totalWeight = 0.0;
                for (LayerDemand demand : admitted) {
                    if (demand.allocatedSlots < demand.desiredSlots())
                        totalWeight += demand.weight;
                }
                if (totalWeight <= 0.0) break;
                int before = remaining;
                for (LayerDemand demand : admitted) {
                    int need = demand.desiredSlots() - demand.allocatedSlots;
                    if (need <= 0 || remaining <= 0) continue;
                    int share = Math.max(1,
                        (int)Math.floor(before * demand.weight / totalWeight));
                    int granted = Math.min(need, Math.min(share, remaining));
                    demand.allocatedSlots += granted;
                    remaining -= granted;
                }
                if (remaining == before) break;
            }
        }
    }

    private static ArrayList<LayerDemand> admitted(final List<LayerDemand> demands,
        final AdmissionClass admission) {
        ArrayList<LayerDemand> result = new ArrayList<>();
        for (LayerDemand demand : demands) if (demand.admission == admission)
            result.add(demand);
        return result;
    }

    private static List<EmitterCommand> represent(final LayerDemand demand) {
        int slots = Math.min(demand.allocatedSlots, demand.commands.size());
        if (slots <= 0) return List.of();
        ArrayList<EmitterCommand> ordered = new ArrayList<>(demand.commands);
        if (demand.layer == VisualLayer.EMBERS) {
            ordered.sort(Comparator.comparingDouble(EmitterCommand::importance).reversed()
                .thenComparingInt(EmitterCommand::seed));
            ordered = new ArrayList<>(ordered.subList(0, slots));
            return scaleCommands(ordered, demand.allocatedRate);
        }
        if (demand.descriptor.effectClass() == EffectClass.FIRE_FIELD
            && (demand.layer == VisualLayer.FLAMES || demand.layer == VisualLayer.SMOKE)) {
            return stableFireRepresentatives(demand, slots);
        }
        List<List<EmitterCommand>> buckets = topologyBuckets(demand);
        if (buckets.isEmpty()) return List.of();
        if (buckets.size() > slots) {
            ArrayList<List<EmitterCommand>> selected = new ArrayList<>(slots);
            for (int slot = 0; slot < slots; slot++) {
                int index = (int) ((long) slot * buckets.size() / slots);
                selected.add(buckets.get(index));
            }
            buckets = selected;
        }
        int[] slotsPerBucket = new int[buckets.size()];
        Arrays.fill(slotsPerBucket, 1);
        int remaining = slots - buckets.size();
        while (remaining > 0) {
            boolean progress = false;
            for (int bucket = 0; bucket < buckets.size() && remaining > 0; bucket++) {
                if (slotsPerBucket[bucket] >= buckets.get(bucket).size()) continue;
                slotsPerBucket[bucket]++;
                remaining--;
                progress = true;
            }
            if (!progress) break;
        }
        double ratePerSlot = demand.allocatedRate / slots;
        ArrayList<EmitterCommand> result = new ArrayList<>(slots);
        for (int bucketIndex = 0; bucketIndex < buckets.size(); bucketIndex++) {
            ArrayList<EmitterCommand> bucket = new ArrayList<>(buckets.get(bucketIndex));
            bucket.sort(Comparator.comparingLong(command -> mix64(
                Integer.toUnsignedLong(command.seed()) ^ demand.descriptor.id())));
            int bucketSlots = slotsPerBucket[bucketIndex];
            for (int slot = 0; slot < bucketSlots; slot++) {
                int start = (int) ((long) slot * bucket.size() / bucketSlots);
                int end = (int) ((long) (slot + 1) * bucket.size() / bucketSlots);
                result.add(aggregate(bucket.subList(start, Math.max(start + 1, end)),
                    Math.max(1, (int) Math.round(ratePerSlot)), demand.layer,
                    demand.descriptor.id() ^ ((long) demand.layer.ordinal() << 48)
                        ^ ((long) bucketIndex << 20) ^ slot));
            }
        }
        return result;
    }

    /**
     * Fire cards are persistent world features, not interchangeable particles.
     * Select stable source cards instead of recomputing aggregate centroids when
     * the GPU budget changes; surviving flames therefore stay in place.
     */
    private static List<EmitterCommand> stableFireRepresentatives(
        final LayerDemand demand, final int slots) {
        List<List<EmitterCommand>> buckets = topologyBuckets(demand);
        if (buckets.isEmpty()) return List.of();
        if (buckets.size() > slots) {
            ArrayList<List<EmitterCommand>> selected = new ArrayList<>(slots);
            for (int slot = 0; slot < slots; slot++) {
                int index = (int) ((long) slot * buckets.size() / slots);
                selected.add(buckets.get(index));
            }
            buckets = selected;
        }
        ArrayList<List<EmitterCommand>> orderedBuckets = new ArrayList<>(buckets.size());
        for (List<EmitterCommand> source : buckets) {
            ArrayList<EmitterCommand> ordered = new ArrayList<>(source);
            ordered.sort(Comparator.comparingLong(command -> mix64(
                Integer.toUnsignedLong(command.seed()) ^ demand.descriptor.id())));
            orderedBuckets.add(ordered);
        }
        ArrayList<EmitterCommand> selected = new ArrayList<>(slots);
        for (int depth = 0; selected.size() < slots; depth++) {
            boolean added = false;
            for (List<EmitterCommand> bucket : orderedBuckets) {
                if (depth >= bucket.size() || selected.size() >= slots) continue;
                selected.add(bucket.get(depth));
                added = true;
            }
            if (!added) break;
        }
        return scaleCommands(selected, demand.allocatedRate);
    }

    private static List<EmitterCommand> scaleCommands(final List<EmitterCommand> commands,
        final double allocatedRate) {
        double requested = commands.stream().mapToDouble(EmitterCommand::spawnCount).sum();
        double scale = requested <= 0.0 ? 0.0 : allocatedRate / requested;
        ArrayList<EmitterCommand> result = new ArrayList<>(commands.size());
        for (EmitterCommand command : commands)
            result.add(command.withSpawnCount(Math.max(1,
                (int) Math.round(command.spawnCount() * scale))));
        return result;
    }

    private static List<List<EmitterCommand>> topologyBuckets(final LayerDemand demand) {
        TreeMap<Long, ArrayList<EmitterCommand>> buckets = new TreeMap<>();
        for (EmitterCommand command : demand.commands)
            buckets.computeIfAbsent(topologyKey(demand, command), ignored -> new ArrayList<>())
                .add(command);
        return new ArrayList<>(buckets.values());
    }

    private static long topologyKey(final LayerDemand demand, final EmitterCommand command) {
        Vec3 relative = command.position().subtract(demand.descriptor.position());
        double angle = Math.atan2(relative.z, relative.x);
        if (angle < 0.0) angle += Math.PI * 2.0;
        double radius = Math.sqrt(relative.x * relative.x + relative.z * relative.z);
        double bounds = Math.max(1.0, demand.descriptor.boundsRadius());
        int angularBins = switch (demand.layer) {
            case SHOCKWAVE, TERRAIN_OBSCURATION, GROUND_DUST -> 24;
            case MUSHROOM_CLOUD, SMOKE_SHROUD, FIREBALL -> 16;
            case STEM -> 8;
            default -> 12;
        };
        int angleBin = Math.min(angularBins - 1,
            (int) Math.floor(angle / (Math.PI * 2.0) * angularBins));
        int radialBin = switch (demand.layer) {
            case MUSHROOM_CLOUD, SMOKE_SHROUD, FIREBALL -> Math.min(2,
                (int) Math.floor(radius / bounds * 3.0));
            case FLAMES, SMOKE -> Math.min(3, (int) Math.floor(radius / bounds * 4.0));
            default -> 0;
        };
        int heightBin = switch (demand.layer) {
            case MUSHROOM_CLOUD, SMOKE_SHROUD, STEM -> Math.min(5, Math.max(0,
                (int) Math.floor((relative.y / bounds + 0.25) * 4.0)));
            case FIREBALL, FLAMES, SMOKE -> Math.min(3, Math.max(0,
                (int) Math.floor((relative.y / bounds + 0.25) * 3.0)));
            default -> 0;
        };
        return ((long) angleBin << 32) | ((long) heightBin << 16) | radialBin;
    }

    private static int topologyMinimum(final VisualLayer layer) {
        return switch (layer) {
            case FIREBALL -> 18;
            case MUSHROOM_CLOUD, SMOKE_SHROUD, SHOCKWAVE, TERRAIN_OBSCURATION -> 24;
            case STEM -> 16;
            case FLAMES, SMOKE -> 8;
            case GROUND_DUST -> 12;
            case DEBRIS, EMBERS, DETAIL -> 1;
        };
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
        float sizeGrowthLimit = switch (layer) {
            case FIREBALL -> 1.24F;
            case MUSHROOM_CLOUD, STEM -> 1.18F;
            case FLAMES, SMOKE, EMBERS -> 1.30F;
            default -> 1.65F;
        };
        /* Aggregation represents many sources through density and spread. Growing
           one billboard several times larger turns distant fire/cloud LOD into a
           conspicuous splodge, so semantic layers retain bounded card sizes. */
        float combinedSize = (float) Math.min(maximumSize(commands) * sizeGrowthLimit,
            Math.sqrt(sizeArea / safe) * Math.pow(representationRatio, 0.14));
        float combinedOpacity = (float) Math.min(1.0,
            opacity / safe * Math.pow(representationRatio, 0.12));
        float spread = (float) Math.sqrt(Math.max(0.0, spreadSq / safe + variance));
        int seed = mix32(Integer.toUnsignedLong(first.seed()) ^ salt ^ layer.ordinal());
        return new EmitterCommand(center, new Vec3(vx / safe, vy / safe, vz / safe),
            (float) (scale / safe), (float) (lifetime / safe),
            (float) (red / safe), (float) (green / safe), (float) (blue / safe),
            combinedOpacity, Math.max(0.02F, combinedSize), spread,
            (float) (jitter / safe), rate, seed, first.type(), first.flags(),
            (float) importance, layer, first.orientation(), first.orientationMode());
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

    private static long mix64(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static int mix32(final long value) {
        long mixed = mix64(value);
        return (int) (mixed ^ mixed >>> 32);
    }

    private static double unit(final long value, final int lane) {
        return (mix64(value + lane * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    record CameraInfo(Vec3 position, Matrix4f viewProjection, float projectionScale,
        int viewportWidth, int viewportHeight) {
        boolean visible(final Vec3 center, final float radius) {
            double radiusSquared = (double) radius * radius;
            if (center.distanceToSqr(position) <= radiusSquared) return true;
            Vec3 relative = center.subtract(position);
            return new FrustumIntersection(viewProjection).testSphere(
                (float) relative.x, (float) relative.y, (float) relative.z, radius);
        }

        double projectedDiameter(final Vec3 center, final float radius) {
            double distance = Math.max(0.25, center.distanceTo(position));
            return Math.max(0.0, radius * 2.0 * projectionScale
                * viewportHeight * 0.5 / distance);
        }
    }

    record ScheduledFrame(List<EmitterCommand> emitters,
        Map<VisualLayer, Integer> emittersByLayer,
        Map<VisualLayer, LayerSchedule> layerSchedules,
        int visibleLayerCount, double allocatedCost) { }

    record LayerSchedule(int commandsSubmitted, int emittersRequested,
        int emittersScheduled, long particlesRequested,
        long particlesAccepted, long particlesRejected,
        int frustumCulled, int capacityCulled) { }

    record BudgetLimits(double spawnRatePerSecond,
        double fragmentCostPerSecond, int emitterCapacity,
        int protectedTransientParticleSlots, int protectedFireParticleSlots) { }

    private enum AdmissionClass {
        CRITICAL_TRANSIENT,
        IMPORTANT_TRANSIENT,
        PERSISTENT_WORLD,
        AMBIENT_DETAIL;

        private static AdmissionClass classify(final EffectDescriptor descriptor,
            final VisualLayer layer) {
            if (descriptor.effectClass() == EffectClass.FIRE_FIELD) {
                return layer == VisualLayer.EMBERS
                    ? AMBIENT_DETAIL : PERSISTENT_WORLD;
            }
            if (layer == VisualLayer.DETAIL) return AMBIENT_DETAIL;
            if (layer == VisualLayer.FIREBALL || layer == VisualLayer.MUSHROOM_CLOUD
                || layer == VisualLayer.STEM || layer == VisualLayer.SHOCKWAVE) {
                return CRITICAL_TRANSIENT;
            }
            return IMPORTANT_TRANSIENT;
        }
    }

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

        private static LodState full(final long frameSequence) {
            LodState state = new LodState();
            state.initialized = true;
            state.level = 0;
            state.previousLevel = 0;
            state.density = 1.0;
            state.transition = 1.0;
            state.lastSeenFrame = frameSequence;
            return state;
        }

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
        private final AdmissionClass admission;
        private double allocatedRate;
        private int allocatedSlots;
        private int cachedTopologyFloor = -1;

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
            this.admission = AdmissionClass.classify(descriptor, layer);
        }

        private int desiredSlots() {
            if (descriptor.effectClass() == EffectClass.FIRE_FIELD)
                return commands.size();
            int rateSlots = Math.max(1, (int) Math.ceil(allocatedRate / 64.0));
            return Math.min(commands.size(), Math.max(rateSlots, topologyFloor()));
        }

        private int topologyFloor() {
            if (commands.isEmpty()) return 0;
            if (cachedTopologyFloor < 0) cachedTopologyFloor = Math.min(commands.size(),
                Math.min(topologyMinimum(layer), topologyBuckets(this).size()));
            return cachedTopologyFloor;
        }
    }

    private static final class BudgetState {
        private double remainingSpawnRate;
        private double remainingFragmentCost;
        private double remainingLiveSlotRate;
        private double remainingTransientLiveSlotRate;
        private double remainingPersistentLiveSlotRate;
        private final double transientReserveRate;
        private final double fireReserveRate;
        private double allocatedTransientRate;
        private double allocatedFireRate;
        private double allocatedFragmentCost;

        private BudgetState(final double spawnRate, final double fragmentCost,
            final double liveSlotRate, final double transientLiveSlotRate,
            final double persistentLiveSlotRate, final double transientReserveRate,
            final double fireReserveRate) {
            remainingSpawnRate = Math.max(0.0, spawnRate);
            remainingFragmentCost = Math.max(0.0, fragmentCost);
            remainingLiveSlotRate = Math.max(0.0, liveSlotRate);
            remainingTransientLiveSlotRate = Math.max(0.0, transientLiveSlotRate);
            remainingPersistentLiveSlotRate = Math.max(0.0, persistentLiveSlotRate);
            this.transientReserveRate = Math.max(0.0, transientReserveRate);
            this.fireReserveRate = Math.max(0.0, fireReserveRate);
        }

        private double grant(final double requestedRate, final double particleCost,
            final AdmissionClass admission) {
            if (requestedRate <= 0.0 || exhausted(admission)) return 0.0;
            double safeCost = Math.max(0.25, particleCost);
            double granted = Math.min(requestedRate, Math.min(remainingSpawnRate,
                Math.min(remainingLiveSlotRate, remainingFragmentCost / safeCost)));
            if (admission == AdmissionClass.PERSISTENT_WORLD
                || admission == AdmissionClass.AMBIENT_DETAIL) {
                granted = Math.min(granted, remainingPersistentLiveSlotRate);
            } else {
                granted = Math.min(granted, remainingTransientLiveSlotRate);
            }
            consume(granted, safeCost, admission);
            return Math.max(0.0, granted);
        }

        private double grantGuaranteed(final double requestedRate,
            final double particleCost, final AdmissionClass admission) {
            if (requestedRate <= 0.0) return 0.0;
            double safeCost = Math.max(0.25, particleCost);
            double granted = Math.min(requestedRate, Math.min(remainingSpawnRate,
                Math.min(remainingLiveSlotRate, remainingFragmentCost / safeCost)));
            consume(granted, safeCost, admission);
            return Math.max(0.0, granted);
        }

        private void consume(final double granted, final double safeCost,
            final AdmissionClass admission) {
            remainingSpawnRate -= granted;
            remainingLiveSlotRate -= granted;
            if (admission == AdmissionClass.PERSISTENT_WORLD
                || admission == AdmissionClass.AMBIENT_DETAIL) {
                remainingPersistentLiveSlotRate = Math.max(0.0,
                    remainingPersistentLiveSlotRate - granted);
            } else {
                remainingTransientLiveSlotRate = Math.max(0.0,
                    remainingTransientLiveSlotRate - granted);
            }
            remainingFragmentCost -= granted * safeCost;
            allocatedFragmentCost += granted * safeCost;
            if (admission == AdmissionClass.CRITICAL_TRANSIENT)
                allocatedTransientRate += granted;
            if (admission == AdmissionClass.PERSISTENT_WORLD)
                allocatedFireRate += granted;
        }

        private double globalFairScale(final double requestedRate,
            final double requestedFragmentCost) {
            if (requestedRate <= 0.0 || requestedFragmentCost <= 0.0) return 0.0;
            return Math.min(1.0, Math.min(remainingSpawnRate / requestedRate,
                Math.min(remainingLiveSlotRate / requestedRate,
                    remainingFragmentCost / requestedFragmentCost)));
        }

        private void releaseUnusedReservations() {
            double unusedTransient = Math.max(0.0,
                transientReserveRate - allocatedTransientRate);
            double unusedFire = Math.max(0.0, fireReserveRate - allocatedFireRate);
            remainingPersistentLiveSlotRate = Math.min(remainingLiveSlotRate,
                remainingPersistentLiveSlotRate + unusedTransient);
            remainingTransientLiveSlotRate = Math.min(remainingLiveSlotRate,
                remainingTransientLiveSlotRate + unusedFire);
        }

        private double fairScale(final double requestedRate,
            final double requestedFragmentCost, final AdmissionClass admission) {
            if (requestedRate <= 0.0 || requestedFragmentCost <= 0.0) return 0.0;
            double liveRate = remainingLiveSlotRate;
            if (admission == AdmissionClass.PERSISTENT_WORLD
                || admission == AdmissionClass.AMBIENT_DETAIL) {
                liveRate = Math.min(liveRate, remainingPersistentLiveSlotRate);
            } else {
                liveRate = Math.min(liveRate, remainingTransientLiveSlotRate);
            }
            return Math.min(1.0, Math.min(
                remainingSpawnRate / requestedRate,
                Math.min(liveRate / requestedRate,
                    remainingFragmentCost / requestedFragmentCost)));
        }

        private boolean exhausted(final AdmissionClass admission) {
            return remainingSpawnRate <= 0.01 || remainingLiveSlotRate <= 0.01
                || remainingFragmentCost <= 0.01
                || (admission == AdmissionClass.PERSISTENT_WORLD
                    || admission == AdmissionClass.AMBIENT_DETAIL)
                    && remainingPersistentLiveSlotRate <= 0.01
                || (admission == AdmissionClass.CRITICAL_TRANSIENT
                    || admission == AdmissionClass.IMPORTANT_TRANSIENT)
                    && remainingTransientLiveSlotRate <= 0.01;
        }
    }

    private static final class MutableLayerSchedule {
        private int commandsSubmitted;
        private int emittersRequested;
        private int emittersScheduled;
        private long particlesRequested;
        private long particlesAccepted;
        private int frustumCulled;

        private LayerSchedule snapshot() {
            return new LayerSchedule(commandsSubmitted, emittersRequested,
                emittersScheduled, particlesRequested, particlesAccepted,
                Math.max(0L, particlesRequested - particlesAccepted),
                frustumCulled, Math.max(0, emittersRequested - emittersScheduled));
        }
    }

}
