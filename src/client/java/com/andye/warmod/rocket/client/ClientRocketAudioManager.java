package com.andye.warmod.rocket.client;

import com.andye.warmod.acoustics.ModSoundEvents;
import com.andye.warmod.entity.RocketProjectileEntity;
import com.andye.warmod.icbm.client.audio.IcbmEngineLoopSound;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Keeps rocket motor audio attached to the projectile instead of the shooter. */
public final class ClientRocketAudioManager {
    private static final double MAX_DISTANCE = 1_200.0;
    private static final int MAX_ACTIVE = 32;
    private static final Map<UUID, IcbmEngineLoopSound> ACTIVE = new LinkedHashMap<>();
    private static ClientLevel activeLevel;
    private static boolean registered;

    private ClientRocketAudioManager() { }

    public static void register() {
        if (registered) return;
        ClientTickEvents.END_CLIENT_TICK.register(ClientRocketAudioManager::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear(client));
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> clear(client));
        registered = true;
    }

    private static synchronized void tick(final Minecraft client) {
        if (client.level == null || client.player == null) { clear(client); return; }
        if (activeLevel != null && activeLevel != client.level) clear(client);
        activeLevel = client.level;
        Vec3 listener = client.player.getEyePosition();
        AABB search = AABB.ofSize(listener, MAX_DISTANCE * 2.0,
            MAX_DISTANCE * 2.0, MAX_DISTANCE * 2.0);
        Set<UUID> seen = new HashSet<>();
        for (RocketProjectileEntity rocket : client.level.getEntitiesOfClass(
            RocketProjectileEntity.class, search, Entity -> Entity.isAlive())) {
            UUID id = rocket.getUUID();
            seen.add(id);
            double distance = listener.distanceTo(rocket.position());
            if (distance > MAX_DISTANCE) continue;
            IcbmEngineLoopSound sound = ACTIVE.get(id);
            if (sound == null && ACTIVE.size() < MAX_ACTIVE) {
                sound = new IcbmEngineLoopSound(soundFor(distance), true, 4,
                    rocket.position());
                client.getSoundManager().play(sound);
                ACTIVE.put(id, sound);
            }
            if (sound != null) {
                float payloadGain = switch (rocket.payloadType()) {
                    case HE -> 0.72F;
                    case CONVENTIONAL_ICBM -> 0.88F;
                    case NUCLEAR_ICBM -> 1.0F;
                };
                float volume = Mth.clamp((float) (0.54 * payloadGain
                    / (1.0 + distance / 110.0)), 0.006F, 0.46F);
                sound.update(rocket.position(), volume);
            }
        }
        Iterator<Map.Entry<UUID, IcbmEngineLoopSound>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, IcbmEngineLoopSound> entry = iterator.next();
            if (!seen.contains(entry.getKey())) {
                entry.getValue().fadeOut(8);
                iterator.remove();
            } else if (entry.getValue().isStopped()) iterator.remove();
        }
    }

    private static SoundEvent soundFor(final double distance) {
        if (distance < 140.0) return ModSoundEvents.MISSILE_ENGINE_SUSTAIN_NEAR;
        if (distance < 400.0) return ModSoundEvents.MISSILE_ENGINE_SUSTAIN_MEDIUM;
        if (distance < 850.0) return ModSoundEvents.MISSILE_ENGINE_SUSTAIN_FAR;
        return ModSoundEvents.MISSILE_ENGINE_SUSTAIN_EXTREME;
    }

    private static synchronized void clear(final Minecraft client) {
        for (IcbmEngineLoopSound sound : ACTIVE.values()) client.getSoundManager().stop(sound);
        ACTIVE.clear(); activeLevel = null;
    }
}
