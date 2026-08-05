package com.andye.warmod.radar.station;

import com.andye.warmod.WarMod;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class RadarStationSavedData extends SavedData {
    private static final Codec<RadarStationSavedData> CODEC =
        RadarStationRecord.CODEC.listOf().xmap(
            RadarStationSavedData::new,
            data -> List.copyOf(data.records.values())
        );
    public static final SavedDataType<RadarStationSavedData> TYPE =
        new SavedDataType<>(
            Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "radar_stations"),
            RadarStationSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
        );

    private final Map<UUID, RadarStationRecord> records = new LinkedHashMap<>();

    public RadarStationSavedData() {
    }

    private RadarStationSavedData(final List<RadarStationRecord> loaded) {
        for (RadarStationRecord record : loaded) records.put(record.radarId(), record);
    }

    public static RadarStationSavedData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<RadarStationRecord> records() {
        return List.copyOf(records.values());
    }

    public Optional<RadarStationRecord> find(final UUID radarId) {
        return Optional.ofNullable(records.get(radarId));
    }

    public int size() {
        return records.size();
    }

    public void put(final RadarStationRecord record) {
        records.put(record.radarId(), record);
        setDirty();
    }

    public void remove(final UUID id) {
        if (records.remove(id) != null) setDirty();
    }
}
