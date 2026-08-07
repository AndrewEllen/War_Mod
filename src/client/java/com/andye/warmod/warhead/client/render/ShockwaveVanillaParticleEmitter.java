package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadEffectProfile;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.WarheadYieldScaling;
import com.andye.warmod.warhead.client.ClientWarheadVisualManager;
import com.andye.warmod.warhead.client.ImpactVisualState;
import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Adds the vanilla animated explosion emitter to sampled terrain nodes reached
 * by the custom pressure-front simulation. The accompanying custom fleck layer
 * gives complete front coverage while these emitters provide the full vanilla
 * animation on a dense, deterministic subset that remains bounded per tick.
 */
public final class ShockwaveVanillaParticleEmitter {
    private static final Map<UUID, Long> LAST_PROCESSED_TICK = new HashMap<>();
    private static final Set<UUID> ACTIVE_IDS = new HashSet<>();
    private static final List<TerrainShockfrontNode> RETURN_NODE_BUFFER = new ArrayList<>(896);
    private static boolean registered;

    private ShockwaveVanillaParticleEmitter() { }

    public static void register() {
        if (registered) return;
        ClientTickEvents.END_CLIENT_TICK.register(ShockwaveVanillaParticleEmitter::tick);
        registered = true;
    }

    private static void tick(final Minecraft client) {
        ClientLevel level = client.level;
        if (level == null) {
            LAST_PROCESSED_TICK.clear();
            ACTIVE_IDS.clear();
            RETURN_NODE_BUFFER.clear();
            return;
        }
        long gameTime = level.getGameTime();
        ClientWarheadVisualManager.Snapshot snapshot =
            ClientWarheadVisualManager.INSTANCE.snapshot(level);
        ACTIVE_IDS.clear();
        for (ImpactVisualState state : snapshot.impacts()) {
            UUID id = state.warheadId();
            ACTIVE_IDS.add(id);
            if (LAST_PROCESSED_TICK.getOrDefault(id, Long.MIN_VALUE) == gameTime) continue;
            LAST_PROCESSED_TICK.put(id, gameTime);
            if (!groundEffects(state.effectProfile())) continue;
            double age = state.ageTicks(gameTime, 0.0);
            float radiusScale = WarheadYieldScaling.radiusScale(
                state.payloadType(), state.visualScale());
            double outwardRadius = WarheadVisualMath.groundShockwaveDistance(age,
                radiusScale);
            if (state.payloadType() != WarheadPayloadType.NUCLEAR) {
                outwardRadius = Math.min(outwardRadius,
                    conventionalVisualRange(state.visualScale()));
            }
            emitOutward(level, state, outwardRadius, gameTime);
            if (state.payloadType() == WarheadPayloadType.NUCLEAR) {
                double previousReturn = WarheadVisualMath.nuclearReturnWaveRadius(
                    Math.max(0.0, age - 1.0), radiusScale);
                double currentReturn = WarheadVisualMath.nuclearReturnWaveRadius(
                    age, radiusScale);
                emitReturn(level, state, previousReturn, currentReturn);
            }
        }
        LAST_PROCESSED_TICK.keySet().retainAll(ACTIVE_IDS);
    }

    private static void emitOutward(final ClientLevel level,
        final ImpactVisualState state, final double radius, final long gameTime) {
        if (!Double.isFinite(radius) || radius <= 0.0) return;
        boolean nuclear = state.payloadType() == WarheadPayloadType.NUCLEAR;
        int maximumNodes = nuclear ? 2_048 : 768;
        List<TerrainShockfrontNode> nodes = state.terrainShockfrontField().readyNodes(
            radius, nuclear ? 256 : 192, maximumNodes, gameTime);
        int emitterLimit = nuclear ? 768 : 256;
        int emitted = 0;
        for (TerrainShockfrontNode node : nodes) {
            if (node.state() != TerrainShockfrontNode.State.READY) continue;
            long hash = mix(state.visualSeed() ^ node.surfaceBlock().asLong());
            if (emitted < emitterLimit) {
                spawnOutward(level, node.position(), hash);
                emitted++;
            }
            state.terrainShockfrontField().markEmitted(node, gameTime);
        }
    }

    private static void emitReturn(final ClientLevel level,
        final ImpactVisualState state, final double previousRadius,
        final double currentRadius) {
        if (!Double.isFinite(previousRadius) || !Double.isFinite(currentRadius)
            || previousRadius <= 0.0 || currentRadius < 0.0
            || currentRadius >= previousRadius) return;
        double outer = previousRadius + 2.5;
        double inner = Math.max(0.0, currentRadius - 2.5);
        final int emitterLimit = 896;
        RETURN_NODE_BUFFER.clear();
        for (TerrainShockfrontSpoke spoke
            : state.terrainShockfrontField().snapshotSpokes()) {
            int remaining = emitterLimit - RETURN_NODE_BUFFER.size();
            if (remaining <= 0) break;
            spoke.appendNodesInDistanceBandDescending(
                outer, inner, RETURN_NODE_BUFFER, remaining);
        }
        for (TerrainShockfrontNode node : RETURN_NODE_BUFFER) {
            long hash = mix(state.visualSeed() ^ node.surfaceBlock().asLong()
                ^ 0x52455455524E4558L);
            spawnReturn(level, node.position(), hash);
        }
    }

    private static void spawnOutward(final ClientLevel level, final Vec3 position,
        final long hash) {
        double x = position.x + signed(hash, 0) * 0.42;
        double y = position.y + 0.12 + unit(hash, 1) * 0.34;
        double z = position.z + signed(hash, 2) * 0.42;
        level.addParticle(ParticleTypes.EXPLOSION_EMITTER,
            x, y, z, 0.0, 0.0, 0.0);
    }

    private static void spawnReturn(final ClientLevel level, final Vec3 position,
        final long hash) {
        double x = position.x + signed(hash, 0) * 0.20;
        double y = position.y + 0.025;
        double z = position.z + signed(hash, 2) * 0.20;
        /* A direct ground-hugging explosion does not leave the return wave suspended in mid-air. */
        level.addParticle(ParticleTypes.EXPLOSION,
            x, y, z, 0.0, -0.03, 0.0);
    }

    private static boolean groundEffects(final WarheadEffectProfile effect) {
        return effect != WarheadEffectProfile.ANTI_AIR_INTERCEPTION
            && effect != WarheadEffectProfile.ANTI_AIR_SAFE_SELF_DESTRUCT;
    }

    private static double conventionalVisualRange(final float visualScale) {
        if (visualScale < 0.49F) return 128.0;
        if (visualScale < 0.82F) return 256.0;
        if (visualScale < 1.19F) return 384.0;
        return 512.0;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double unit(final long value, final int lane) {
        long mixed = mix(value + lane * 0x9E3779B97F4A7C15L);
        return (mixed >>> 11) * 0x1.0p-53;
    }

    private static double signed(final long value, final int lane) {
        return unit(value, lane) * 2.0 - 1.0;
    }
}
