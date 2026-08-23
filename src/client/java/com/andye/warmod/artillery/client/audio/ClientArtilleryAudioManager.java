package com.andye.warmod.artillery.client.audio;

import com.andye.warmod.acoustics.ModSoundEvents;
import com.andye.warmod.entity.ArtilleryWarheadEntity;
import com.andye.warmod.warhead.client.audio.TerminalRushLoopSound;
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

/** A low-count, position-following aerodynamic rush for live artillery shells. */
public final class ClientArtilleryAudioManager {
    private static final double MAX_DISTANCE = 1_200.0;
    private static final int MAX_ACTIVE = 32;
    private static final Map<UUID, TerminalRushLoopSound> ACTIVE = new LinkedHashMap<>();
    private static ClientLevel activeLevel;
    private static boolean registered;

    private ClientArtilleryAudioManager() { }

    public static void register() {
        if (registered) return;
        ClientTickEvents.END_CLIENT_TICK.register(ClientArtilleryAudioManager::tick);
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
        for (ArtilleryWarheadEntity shell : client.level.getEntitiesOfClass(
            ArtilleryWarheadEntity.class, search,
            candidate -> candidate.isAlive() && candidate.getDeltaMovement().lengthSqr() > 0.36)) {
            UUID id = shell.getUUID();
            seen.add(id);
            double distance = listener.distanceTo(shell.position());
            if (distance > MAX_DISTANCE) continue;
            TerminalRushLoopSound sound = ACTIVE.get(id);
            if (sound == null && ACTIVE.size() < MAX_ACTIVE) {
                sound = new TerminalRushLoopSound(soundFor(distance), true, 5,
                    shell.position());
                client.getSoundManager().play(sound);
                ACTIVE.put(id, sound);
            }
            if (sound != null) {
                double speed = shell.getDeltaMovement().length();
                float volume = Mth.clamp((float) ((0.18 + speed * 0.045)
                    / (1.0 + distance / 150.0)), 0.008F, 0.34F);
                sound.update(shell.position(), volume);
            }
        }
        Iterator<Map.Entry<UUID, TerminalRushLoopSound>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TerminalRushLoopSound> entry = iterator.next();
            if (!seen.contains(entry.getKey())) {
                entry.getValue().fadeOut(9);
                iterator.remove();
            } else if (entry.getValue().isStopped()) iterator.remove();
        }
    }

    private static SoundEvent soundFor(final double distance) {
        if (distance < 120.0) return ModSoundEvents.TERMINAL_RUSH_LOOP_NEAR;
        if (distance < 320.0) return ModSoundEvents.TERMINAL_RUSH_LOOP_MEDIUM;
        if (distance < 750.0) return ModSoundEvents.TERMINAL_RUSH_LOOP_FAR;
        return ModSoundEvents.TERMINAL_RUSH_LOOP_EXTREME;
    }

    private static synchronized void clear(final Minecraft client) {
        for (TerminalRushLoopSound sound : ACTIVE.values())
            client.getSoundManager().stop(sound);
        ACTIVE.clear(); activeLevel = null;
    }
}
