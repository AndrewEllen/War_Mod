package com.andye.warmod.silo;

import com.andye.warmod.WarMod;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class MissileSiloSavedData extends SavedData {
    private static final Codec<MissileSiloSavedData> CODEC = MissileSiloRecord.CODEC.listOf().xmap(
        MissileSiloSavedData::new, data -> List.copyOf(data.records.values()));
    public static final SavedDataType<MissileSiloSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "missile_silos"), MissileSiloSavedData::new,
        CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final Map<UUID, MissileSiloRecord> records = new LinkedHashMap<>();

    public MissileSiloSavedData() {
    }

    private MissileSiloSavedData(final List<MissileSiloRecord> loaded) {
        for (MissileSiloRecord record : loaded) this.records.put(record.siloId(), record);
    }

    public static MissileSiloSavedData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<MissileSiloRecord> records() { return List.copyOf(this.records.values()); }
    public int size() { return this.records.size(); }
    public void put(final MissileSiloRecord record) { this.records.put(record.siloId(), record); this.setDirty(); }
    public void remove(final UUID siloId) { if (this.records.remove(siloId) != null) this.setDirty(); }
}
