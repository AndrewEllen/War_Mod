package com.andye.warmod.warhead;

import com.andye.warmod.WarMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Durable per-dimension policy for fire left by War Mod explosives. */
public final class WarheadFireSettings extends SavedData {
    private static final Codec<WarheadFireSettings> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(Codec.BOOL.optionalFieldOf("custom_fire", true)
            .forGetter(WarheadFireSettings::customFire))
            .apply(instance, WarheadFireSettings::new));
    public static final SavedDataType<WarheadFireSettings> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_fire_settings"),
        WarheadFireSettings::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private boolean customFire = true;

    public WarheadFireSettings() { }
    private WarheadFireSettings(final boolean customFire) { this.customFire = customFire; }

    public static WarheadFireSettings get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean customFire() { return customFire; }

    public void setCustomFire(final boolean value) {
        if (customFire == value) return;
        customFire = value;
        setDirty();
    }
}
