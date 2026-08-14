package com.andye.warmod.fire.client;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.FireSurfaceAnchor;
import com.andye.warmod.fire.network.ClientboundFireStatePayload;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class ClientFireVisualManager {
    public static final ClientFireVisualManager INSTANCE = new ClientFireVisualManager();
    private static final int EXPIRY_TICKS = 28;
    private final Map<Long, VisualPatch> patches = new LinkedHashMap<>();
    private ClientLevel activeLevel;

    private ClientFireVisualManager() { }

    public synchronized void accept(final ClientboundFireStatePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!payload.isWellFormed() || !ensureCurrentLevel(level)) return;
        long receivedAt = level.getGameTime();
        HashSet<Long> received = new HashSet<>(payload.entries().size());
        for (ClientboundFireStatePayload.Entry entry : payload.entries()) {
            received.add(entry.id());
            VisualPatch previous = patches.get(entry.id());
            Vec3 incomingWind = new Vec3(entry.windX(), entry.windY(), entry.windZ());
            Vec3 wind = previous == null ? incomingWind : previous.wind().lerp(incomingWind, 0.34);
            float heat = lerp(previous == null ? entry.heat() : previous.heat(), entry.heat(), 0.44F);
            float coverage = lerp(previous == null ? entry.coverage() : previous.coverage(),
                entry.coverage(), 0.38F);
            float smoke = lerp(previous == null ? entry.smoke() : previous.smoke(),
                entry.smoke(), 0.40F);
            Direction face = Direction.values()[Byte.toUnsignedInt(entry.face())];
            FireSurfaceAnchor anchor = new FireSurfaceAnchor(BlockPos.of(entry.packedHost()), face,
                entry.localX(), entry.localY(), entry.localZ());
            patches.put(entry.id(), new VisualPatch(entry.id(), anchor, entry.intensity(),
                heat, coverage, smoke, entry.phase(), entry.seed(), entry.ignitionGameTime(),
                wind, receivedAt));
        }
        if (payload.complete()) patches.keySet().removeIf(id -> !received.contains(id));
    }

    public synchronized void tick(final Minecraft client) {
        if (!ensureCurrentLevel(client.level)) return;
        long now = client.level.getGameTime();
        Iterator<VisualPatch> iterator = patches.values().iterator();
        while (iterator.hasNext())
            if (now - iterator.next().lastSeenClientTick() > EXPIRY_TICKS) iterator.remove();
    }

    public synchronized List<VisualPatch> snapshot(final ClientLevel level) {
        return level == null || activeLevel != level ? List.of() : List.copyOf(patches.values());
    }

    public synchronized void clear() { patches.clear(); activeLevel = null; }

    private boolean ensureCurrentLevel(final ClientLevel level) {
        if (level == null) { clear(); return false; }
        if (activeLevel != level) { patches.clear(); activeLevel = level; }
        return true;
    }

    private static float lerp(final float from, final float to, final float amount) {
        return from + (to - from) * amount;
    }

    public record VisualPatch(long id, FireSurfaceAnchor anchor, float intensity,
        float heat, float coverage, float smoke, FirePhase phase, long seed,
        long ignitionGameTime, Vec3 wind, long lastSeenClientTick) { }
}
