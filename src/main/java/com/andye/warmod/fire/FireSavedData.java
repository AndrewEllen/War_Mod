package com.andye.warmod.fire;

import com.andye.warmod.WarMod;
import com.mojang.datafixers.util.Either;
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
        boolean surfaceFlame, boolean consumed, long hardBurnEndTick,
        long lastExternalHeatTick, float remainingExternalHeatBudget,
        boolean surfaceBurnLocked) {
        private static final Codec<LegacyEntry> LEGACY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("host").forGetter(LegacyEntry::host),
            Codec.INT.fieldOf("face").forGetter(LegacyEntry::face),
            Codec.FLOAT.fieldOf("local_x").forGetter(LegacyEntry::localX),
            Codec.FLOAT.fieldOf("local_y").forGetter(LegacyEntry::localY),
            Codec.FLOAT.fieldOf("local_z").forGetter(LegacyEntry::localZ),
            Codec.FLOAT.fieldOf("intensity").forGetter(LegacyEntry::intensity),
            Codec.INT.fieldOf("phase").forGetter(LegacyEntry::phase),
            Codec.FLOAT.fieldOf("heat").forGetter(LegacyEntry::heat),
            Codec.FLOAT.fieldOf("coverage").forGetter(LegacyEntry::coverage),
            Codec.FLOAT.fieldOf("fuel").forGetter(LegacyEntry::fuel),
            Codec.INT.fieldOf("burn_ticks").forGetter(LegacyEntry::burnTicks),
            Codec.LONG.fieldOf("seed").forGetter(LegacyEntry::seed),
            Codec.LONG.fieldOf("ignition_time").forGetter(LegacyEntry::ignitionGameTime),
            Codec.LONG.fieldOf("next_transfer_delay").forGetter(LegacyEntry::nextTransferDelay),
            Codec.BOOL.fieldOf("surface_flame").forGetter(LegacyEntry::surfaceFlame),
            Codec.BOOL.fieldOf("consumed").forGetter(LegacyEntry::consumed)
        ).apply(instance, LegacyEntry::new));
        private static final Codec<SurfaceLifetime> SURFACE_LIFETIME_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("hard_burn_end_tick")
                    .forGetter(SurfaceLifetime::hardBurnEndTick),
                Codec.LONG.fieldOf("last_external_heat_tick")
                    .forGetter(SurfaceLifetime::lastExternalHeatTick),
                Codec.FLOAT.fieldOf("remaining_external_heat_budget")
                    .forGetter(SurfaceLifetime::remainingExternalHeatBudget),
                Codec.BOOL.fieldOf("surface_burn_locked")
                    .forGetter(SurfaceLifetime::surfaceBurnLocked)
            ).apply(instance, SurfaceLifetime::new));
        private static final Codec<Entry> EXTENDED_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                LEGACY_CODEC.fieldOf("patch").forGetter(Entry::legacy),
                SURFACE_LIFETIME_CODEC.fieldOf("surface_lifetime")
                    .forGetter(Entry::surfaceLifetime)
            ).apply(instance, Entry::from));
        public static final Codec<Entry> CODEC = Codec.either(LEGACY_CODEC, EXTENDED_CODEC)
            .xmap(value -> value.map(Entry::fromLegacy, entry -> entry),
                entry -> Either.right(entry));

        private LegacyEntry legacy() {
            return new LegacyEntry(host, face, localX, localY, localZ, intensity, phase,
                heat, coverage, fuel, burnTicks, seed, ignitionGameTime,
                nextTransferDelay, surfaceFlame, consumed);
        }

        private SurfaceLifetime surfaceLifetime() {
            return new SurfaceLifetime(hardBurnEndTick, lastExternalHeatTick,
                remainingExternalHeatBudget, surfaceBurnLocked);
        }

        private static Entry from(final LegacyEntry legacy, final SurfaceLifetime lifetime) {
            return new Entry(legacy.host, legacy.face, legacy.localX, legacy.localY,
                legacy.localZ, legacy.intensity, legacy.phase, legacy.heat,
                legacy.coverage, legacy.fuel, legacy.burnTicks, legacy.seed,
                legacy.ignitionGameTime, legacy.nextTransferDelay,
                legacy.surfaceFlame, legacy.consumed, lifetime.hardBurnEndTick,
                lifetime.lastExternalHeatTick, lifetime.remainingExternalHeatBudget,
                lifetime.surfaceBurnLocked);
        }

        private static Entry fromLegacy(final LegacyEntry legacy) {
            return new Entry(legacy.host, legacy.face, legacy.localX, legacy.localY,
                legacy.localZ, legacy.intensity, legacy.phase, legacy.heat,
                legacy.coverage, legacy.fuel, legacy.burnTicks, legacy.seed,
                legacy.ignitionGameTime, legacy.nextTransferDelay,
                legacy.surfaceFlame, legacy.consumed, Long.MAX_VALUE,
                Long.MIN_VALUE, 0.0F, false);
        }

        private record SurfaceLifetime(long hardBurnEndTick, long lastExternalHeatTick,
            float remainingExternalHeatBudget, boolean surfaceBurnLocked) { }

        private record LegacyEntry(BlockPos host, int face, float localX, float localY,
            float localZ, float intensity, int phase, float heat, float coverage,
            float fuel, int burnTicks, long seed, long ignitionGameTime,
            long nextTransferDelay, boolean surfaceFlame, boolean consumed) { }
    }
}
