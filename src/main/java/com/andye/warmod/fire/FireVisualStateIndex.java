package com.andye.warmod.fire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Incrementally maintained, world-read-free source index for visual replication. */
final class FireVisualStateIndex {
    private static final int CELL_SIZE = 64;
    private final Map<Long, Entry> entries = new HashMap<>();
    private final Map<Long, HashSet<Long>> cells = new HashMap<>();

    void upsert(final FireCellSnapshot snapshot) {
        if (snapshot == null || snapshot.anchor() == null) return;
        Entry previous = entries.put(snapshot.id(), new Entry(snapshot, false, 0L, Long.MAX_VALUE));
        if (previous == null) {
            cells.computeIfAbsent(cellKey(snapshot.anchor().position()),
                ignored -> new HashSet<>()).add(snapshot.id());
        }
    }

    void markDormant(final long id, final long now, final long expiryTick) {
        Entry entry = entries.get(id);
        if (entry == null) return;
        entries.put(id, new Entry(entry.snapshot(), true, now,
            Math.max(now + 1L, expiryTick)));
    }

    void remove(final long id) {
        Entry removed = entries.remove(id);
        if (removed == null) return;
        long key = cellKey(removed.snapshot().anchor().position());
        Set<Long> ids = cells.get(key);
        if (ids != null) {
            ids.remove(id);
            if (ids.isEmpty()) cells.remove(key);
        }
    }

    List<FireCellSnapshot> query(final Vec3 viewer, final double radius,
        final long now) {
        if (viewer == null || !viewer.isFinite() || radius <= 0.0) return List.of();
        double radiusSquared = radius * radius;
        int cellRadius = Mth.ceil(radius / CELL_SIZE);
        int centerX = Math.floorDiv(Mth.floor(viewer.x), CELL_SIZE);
        int centerZ = Math.floorDiv(Mth.floor(viewer.z), CELL_SIZE);
        ArrayList<FireCellSnapshot> result = new ArrayList<>();
        HashSet<Long> visited = new HashSet<>();
        for (int dx = -cellRadius; dx <= cellRadius; dx++) {
            for (int dz = -cellRadius; dz <= cellRadius; dz++) {
                Set<Long> ids = cells.get(cellKey(centerX + dx, centerZ + dz));
                if (ids == null) continue;
                for (long id : ids) {
                    if (!visited.add(id)) continue;
                    Entry entry = entries.get(id);
                    if (entry == null || viewer.distanceToSqr(
                        entry.snapshot().anchor().position()) > radiusSquared) continue;
                    result.add(entry.dormant() ? dormantSnapshot(entry, now)
                        : entry.snapshot());
                }
            }
        }
        return List.copyOf(result);
    }

    int size() { return entries.size(); }

    private static FireCellSnapshot dormantSnapshot(final Entry entry, final long now) {
        FireCellSnapshot source = entry.snapshot();
        double progress = Mth.clamp((now - entry.dormantAt())
            / (double)Math.max(1L, entry.expiryTick() - entry.dormantAt()), 0.0, 1.0);
        float heat = source.heat() * (float)Math.pow(1.0 - progress, 0.72);
        float coverage = Math.max(0.03F, source.coverage()
            * (float)(1.0 - progress * 0.72));
        float intensity = source.intensity() * (float)(1.0 - progress);
        float smoke = source.smoke() * (float)Math.pow(1.0 - progress, 0.38);
        FirePhase phase = progress < 0.45 ? source.phase()
            : progress < 0.78 ? FirePhase.DECAYING : FirePhase.SMOLDERING;
        return new FireCellSnapshot(source.id(), source.anchor(), intensity, heat,
            coverage, smoke, phase, source.seed(), source.ignitionGameTime(),
            source.wind());
    }

    private static long cellKey(final Vec3 position) {
        return cellKey(Math.floorDiv(Mth.floor(position.x), CELL_SIZE),
            Math.floorDiv(Mth.floor(position.z), CELL_SIZE));
    }

    private static long cellKey(final int x, final int z) {
        return (long)x << 32 ^ z & 0xFFFF_FFFFL;
    }

    private record Entry(FireCellSnapshot snapshot, boolean dormant,
        long dormantAt, long expiryTick) { }
}
