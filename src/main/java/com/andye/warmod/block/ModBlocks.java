package com.andye.warmod.block;

import com.andye.warmod.WarMod;
import com.andye.warmod.artillery.ArtilleryPayload;
import com.andye.warmod.warhead.WarheadYield;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    public static final ResourceKey<Block> MISSILE_SILO_KEY = key("missile_silo");
    public static final ResourceKey<Block> MISSILE_SILO_GUIDANCE_SUPPORT_KEY = key("missile_silo_guidance_support");
    public static final ResourceKey<Block> RADAR_STATION_KEY = key("radar_station");
    public static final ResourceKey<Block> PHALANX_TURRET_KEY = key("phalanx_turret");
    public static final ResourceKey<Block> RADAR_DISPLAY_PANEL_KEY = key("radar_display_panel");
    public static final ResourceKey<Block> ITEM_PIPE_KEY = key("item_pipe");
    public static final ResourceKey<Block> ARTILLERY_CANNON_KEY = key("artillery_cannon");
    private static final Map<WarheadYield, TimedWarheadTntBlock> TIMED_TNT =
        createTimedTntBlocks(false);
    private static final Map<WarheadYield, TimedWarheadTntBlock> CLUSTER_TNT =
        createTimedTntBlocks(true);

    public static final MissileSiloGuidanceSupportBlock MISSILE_SILO_GUIDANCE_SUPPORT =
        new MissileSiloGuidanceSupportBlock(BlockBehaviour.Properties.of()
            .setId(MISSILE_SILO_GUIDANCE_SUPPORT_KEY)
            .strength(6.0F, 18.0F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.METAL)
            .pushReaction(PushReaction.BLOCK)
            .noOcclusion());

    public static final ArtilleryCannonBlock ARTILLERY_CANNON =
        new ArtilleryCannonBlock(BlockBehaviour.Properties.of()
            .setId(ARTILLERY_CANNON_KEY)
            .strength(7.0F, 24.0F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.METAL)
            .pushReaction(PushReaction.BLOCK)
            .noOcclusion());
    public static final RadarStationBlock RADAR_STATION =
        new RadarStationBlock(BlockBehaviour.Properties.of()
            .setId(RADAR_STATION_KEY)
            .strength(8.0F, 24.0F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.METAL)
            .pushReaction(PushReaction.BLOCK)
            .noOcclusion());

    public static final PhalanxTurretBlock PHALANX_TURRET =
        new PhalanxTurretBlock(BlockBehaviour.Properties.of()
            .setId(PHALANX_TURRET_KEY)
            .strength(8.0F, 24.0F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.METAL)
            .pushReaction(PushReaction.BLOCK)
            .noOcclusion());

    public static final RadarDisplayPanelBlock RADAR_DISPLAY_PANEL =
        new RadarDisplayPanelBlock(BlockBehaviour.Properties.of()
            .setId(RADAR_DISPLAY_PANEL_KEY)
            .strength(4.0F, 12.0F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.METAL)
            .pushReaction(PushReaction.BLOCK)
            .noOcclusion());

    public static final ItemPipeBlock ITEM_PIPE =
        new ItemPipeBlock(BlockBehaviour.Properties.of()
            .setId(ITEM_PIPE_KEY)
            .strength(2.5F, 8.0F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.METAL)
            .pushReaction(PushReaction.BLOCK)
            .noOcclusion());

    public static final MissileSiloBlock MISSILE_SILO =
        new MissileSiloBlock(BlockBehaviour.Properties.of()
            .setId(MISSILE_SILO_KEY)
            .strength(8.0F, 24.0F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.METAL)
            .pushReaction(PushReaction.BLOCK));

    private static boolean registered;

    private ModBlocks() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        Registry.register(BuiltInRegistries.BLOCK, MISSILE_SILO_KEY, MISSILE_SILO);
        Registry.register(BuiltInRegistries.BLOCK, MISSILE_SILO_GUIDANCE_SUPPORT_KEY, MISSILE_SILO_GUIDANCE_SUPPORT);
        Registry.register(BuiltInRegistries.BLOCK, RADAR_STATION_KEY, RADAR_STATION);
        Registry.register(BuiltInRegistries.BLOCK, PHALANX_TURRET_KEY, PHALANX_TURRET);
        Registry.register(BuiltInRegistries.BLOCK, RADAR_DISPLAY_PANEL_KEY, RADAR_DISPLAY_PANEL);
        Registry.register(BuiltInRegistries.BLOCK, ITEM_PIPE_KEY, ITEM_PIPE);
        Registry.register(BuiltInRegistries.BLOCK, ARTILLERY_CANNON_KEY, ARTILLERY_CANNON);
        for (WarheadYield yield : WarheadYield.values()) {
            Registry.register(BuiltInRegistries.BLOCK, key(tntPath(yield, false)),
                timedTnt(yield, false));
            Registry.register(BuiltInRegistries.BLOCK, key(tntPath(yield, true)),
                timedTnt(yield, true));
        }
        registered = true;
    }

    public static TimedWarheadTntBlock timedTnt(final WarheadYield yield,
        final boolean cluster) {
        return (cluster ? CLUSTER_TNT : TIMED_TNT).get(yield);
    }

    public static String tntPath(final WarheadYield yield, final boolean cluster) {
        return yield.getSerializedName() + (cluster ? "_cluster_tnt" : "_tnt");
    }

    private static Map<WarheadYield, TimedWarheadTntBlock> createTimedTntBlocks(
        final boolean cluster) {
        Map<WarheadYield, TimedWarheadTntBlock> result = new EnumMap<>(WarheadYield.class);
        for (WarheadYield yield : WarheadYield.values()) {
            ResourceKey<Block> blockKey = key(tntPath(yield, cluster));
            result.put(yield, new TimedWarheadTntBlock(BlockBehaviour.Properties.of()
                .setId(blockKey)
                .strength(0.0F)
                .sound(SoundType.GRASS)
                .ignitedByLava(), new ArtilleryPayload(yield, cluster)));
        }
        return Map.copyOf(result);
    }

    private static ResourceKey<Block> key(final String path) {
        return ResourceKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WarMod.MOD_ID, path)
        );
    }
}
