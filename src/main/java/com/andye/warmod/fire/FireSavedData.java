package com.andye.warmod.fire;

import com.andye.warmod.WarMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Per-dimension durable state for authoritative custom-fire patches. */
public final class FireSavedData extends SavedData {
    private static final Codec<FireSavedData> CODEC = Entry.CODEC.listOf().xmap(
        FireSavedData::new, data -> data.entries);
    public static final SavedDataType<FireSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "fire_surface_patches"),
        FireSavedData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private List<Entry> entries = List.of();

    public FireSavedData() { }
    private FireSavedData(final List<Entry> entries) { this.entries = List.copyOf(entries); }

    public static FireSavedData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Entry> entries() { return entries; }

    public void replace(final List<Entry> replacement) {
        entries = List.copyOf(replacement);
        setDirty();
    }

    public record Entry(BlockPos host, int face, float localX, float localY, float localZ,
        float intensity, int phase, float heat, float coverage, float fuel, int burnTicks,
        long seed, long ignitionGameTime, long nextTransferDelay,
        boolean surfaceFlame, boolean consumed) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("host").forGetter(Entry::host),
            Codec.INT.fieldOf("face").forGetter(Entry::face),
            Codec.FLOAT.fieldOf("local_x").forGetter(Entry::localX),
            Codec.FLOAT.fieldOf("local_y").forGetter(Entry::localY),
            Codec.FLOAT.fieldOf("local_z").forGetter(Entry::localZ),
            Codec.FLOAT.fieldOf("intensity").forGetter(Entry::intensity),
            Codec.INT.fieldOf("phase").forGetter(Entry::phase),
            Codec.FLOAT.fieldOf("heat").forGetter(Entry::heat),
            Codec.FLOAT.fieldOf("coverage").forGetter(Entry::coverage),
            Codec.FLOAT.fieldOf("fuel").forGetter(Entry::fuel),
            Codec.INT.fieldOf("burn_ticks").forGetter(Entry::burnTicks),
            Codec.LONG.fieldOf("seed").forGetter(Entry::seed),
            Codec.LONG.fieldOf("ignition_time").forGetter(Entry::ignitionGameTime),
            Codec.LONG.fieldOf("next_transfer_delay").forGetter(Entry::nextTransferDelay),
            Codec.BOOL.fieldOf("surface_flame").forGetter(Entry::surfaceFlame),
            Codec.BOOL.fieldOf("consumed").forGetter(Entry::consumed)
        ).apply(instance, Entry::new));
    }
}
