package com.andye.warmod.fire.client;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.network.ClientboundFireStatePayload;
import java.util.Iterator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class ClientFireVisualManager {
    public static final ClientFireVisualManager INSTANCE = new ClientFireVisualManager();
    private static final int EXPIRY_TICKS = 35;
    private final Map<Long, VisualCell> cells = new LinkedHashMap<>();
    private ClientLevel activeLevel;

    private ClientFireVisualManager() { }

    public synchronized void accept(final ClientboundFireStatePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!payload.isWellFormed() || !ensureCurrentLevel(level)) return;
        long receivedAt = level.getGameTime();
        HashSet<Long> received = new HashSet<>(payload.entries().size());
        for (ClientboundFireStatePayload.Entry entry : payload.entries()) {
            received.add(entry.packedPosition());
            VisualCell previous = cells.get(entry.packedPosition());
            Vec3 incomingWind = new Vec3(entry.windX(), entry.windY(), entry.windZ());
            Vec3 wind = previous == null ? incomingWind : previous.wind().lerp(incomingWind, 0.42);
            float heat = previous == null ? entry.heat()
                : previous.heat() + (entry.heat() - previous.heat()) * 0.55F;
            cells.put(entry.packedPosition(), new VisualCell(
                BlockPos.of(entry.packedPosition()), entry.intensity(), heat, entry.phase(),
                entry.seed(), wind,
                payload.serverGameTime(), receivedAt));
        }
        cells.keySet().removeIf(key -> !received.contains(key));
    }

    public synchronized void tick(final Minecraft client) {
        if (!ensureCurrentLevel(client.level)) return;
        long now = client.level.getGameTime();
        Iterator<VisualCell> iterator = cells.values().iterator();
        while (iterator.hasNext()) if (now - iterator.next().lastSeenClientTick() > EXPIRY_TICKS) iterator.remove();
    }

    public synchronized List<VisualCell> snapshot(final ClientLevel level) {
        if (level == null || activeLevel != level) return List.of();
        return List.copyOf(cells.values());
    }

    public synchronized void clear() {
        cells.clear();
        activeLevel = null;
    }

    private boolean ensureCurrentLevel(final ClientLevel level) {
        if (level == null) {
            clear();
            return false;
        }
        if (activeLevel != level) {
            cells.clear();
            activeLevel = level;
        }
        return true;
    }

    public record VisualCell(BlockPos position, float intensity, float heat, FirePhase phase,
        long seed, Vec3 wind, long serverGameTime, long lastSeenClientTick) { }
}
