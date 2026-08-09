package com.andye.warmod.menu;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public final class ModMenus {
    public record SiloOpeningData(BlockPos centre, UUID siloId) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SiloOpeningData> STREAM_CODEC = StreamCodec.of((buffer, data) -> { buffer.writeBlockPos(data.centre); buffer.writeUUID(data.siloId); }, buffer -> new SiloOpeningData(buffer.readBlockPos(), buffer.readUUID()));
    }
    public record PhalanxOpeningData(BlockPos centre) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PhalanxOpeningData> STREAM_CODEC = StreamCodec.of((buffer, data) -> buffer.writeBlockPos(data.centre), buffer -> new PhalanxOpeningData(buffer.readBlockPos()));
    }
    public record ArtilleryOpeningData(BlockPos position) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ArtilleryOpeningData> STREAM_CODEC = StreamCodec.of((buffer, data) -> buffer.writeBlockPos(data.position), buffer -> new ArtilleryOpeningData(buffer.readBlockPos()));
    }
    public static final ExtendedMenuType<MissileSiloMenu, SiloOpeningData> MISSILE_SILO = new ExtendedMenuType<>((id, inventory, data) -> new MissileSiloMenu(id, inventory, data), SiloOpeningData.STREAM_CODEC);
    public static final ExtendedMenuType<PhalanxMenu, PhalanxOpeningData> PHALANX = new ExtendedMenuType<>((id, inventory, data) -> new PhalanxMenu(id, inventory, data), PhalanxOpeningData.STREAM_CODEC);
    public static final ExtendedMenuType<ArtilleryCannonMenu, ArtilleryOpeningData> ARTILLERY_CANNON = new ExtendedMenuType<>((id, inventory, data) -> new ArtilleryCannonMenu(id, inventory, data), ArtilleryOpeningData.STREAM_CODEC);
    private static boolean registered;
    private ModMenus() { }
    public static void register() { if (registered) return; Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "missile_silo"), MISSILE_SILO); Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "phalanx_turret"), PHALANX); Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "artillery_cannon"), ARTILLERY_CANNON); registered = true; }
}