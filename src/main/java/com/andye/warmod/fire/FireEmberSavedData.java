package com.andye.warmod.fire;

import com.andye.warmod.WarMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Separate durable stream for in-flight firebrands, preserving the patch save schema. */
public final class FireEmberSavedData extends SavedData {
    private static final Codec<FireEmberSavedData> CODEC = Entry.CODEC.listOf().xmap(
        FireEmberSavedData::new, data -> data.entries);
    public static final SavedDataType<FireEmberSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "fire_embers"),
        FireEmberSavedData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private List<Entry> entries = List.of();

    public FireEmberSavedData() { }
    private FireEmberSavedData(final List<Entry> entries) { this.entries = List.copyOf(entries); }

    public static FireEmberSavedData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Entry> entries() { return entries; }

    public void replace(final List<Entry> replacement) {
        entries = List.copyOf(replacement);
        setDirty();
    }

    public record Entry(double x, double y, double z, double velocityX, double velocityY,
        double velocityZ, float intensity, long seed, int remainingLifetime) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(Entry::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Entry::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Entry::z),
            Codec.DOUBLE.fieldOf("velocity_x").forGetter(Entry::velocityX),
            Codec.DOUBLE.fieldOf("velocity_y").forGetter(Entry::velocityY),
            Codec.DOUBLE.fieldOf("velocity_z").forGetter(Entry::velocityZ),
            Codec.FLOAT.fieldOf("intensity").forGetter(Entry::intensity),
            Codec.LONG.fieldOf("seed").forGetter(Entry::seed),
            Codec.INT.fieldOf("remaining_lifetime").forGetter(Entry::remainingLifetime)
        ).apply(instance, Entry::new));
    }
}
