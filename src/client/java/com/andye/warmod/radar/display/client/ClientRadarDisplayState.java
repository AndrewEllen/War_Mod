package com.andye.warmod.radar.display.client;

import com.andye.warmod.radar.display.RadarDisplaySnapshot;
import com.andye.warmod.radar.display.network.ClientboundRadarDisplayClearPayload;
import com.andye.warmod.radar.display.network.ClientboundRadarDisplayStatePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class ClientRadarDisplayState {
    public static final ClientRadarDisplayState INSTANCE =
        new ClientRadarDisplayState();

    private static final double EXPIRY_TICKS = 120.0;

    private final Map<Key, Entry> states = new HashMap<>();

    private ClientRadarDisplayState() {
    }

    public void update(
        final ClientboundRadarDisplayStatePayload payload
    ) {
        RadarDisplaySnapshot snapshot = payload.snapshot();

        double clientTime = currentClientTime();

        Key key = new Key(
            snapshot.dimension(),
            snapshot.controller().immutable(),
            snapshot.displayId()
        );

        states.put(
            key,
            new Entry(snapshot, clientTime)
        );
    }

    public void clear(
        final ClientboundRadarDisplayClearPayload payload
    ) {
        states.remove(new Key(
            payload.dimension(),
            payload.controller().immutable(),
            payload.displayId()
        ));
    }

    public @Nullable View view(
        final Identifier dimension,
        final BlockPos controller,
        final UUID displayId,
        final double clientTime
    ) {
        Key key = new Key(
            dimension,
            controller,
            displayId
        );

        Entry entry = states.get(key);

        if (entry == null) {
            return null;
        }

        double age = Math.max(0.0, clientTime - entry.receivedClientTime());
        double serverNow = entry.snapshot().serverGameTime() + age;
        return new View(entry.snapshot(), serverNow, age > 60.0);
    }

    public void clearAll() {
        states.clear();
    }

    private static double currentClientTime() {
        Minecraft minecraft = Minecraft.getInstance();

        return minecraft.level == null
            ? 0.0
            : minecraft.level.getGameTime();
    }

    public record View(
        RadarDisplaySnapshot snapshot,
        double serverNow,
        boolean stale
    ) {
    }

    private record Key(
        Identifier dimension,
        BlockPos controller,
        UUID displayId
    ) {
    }

    private record Entry(
        RadarDisplaySnapshot snapshot,
        double receivedClientTime
    ) {
    }
}
