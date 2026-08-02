package com.andye.warmod.icbm.network;
import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record ClientboundIcbmRemovePayload(UUID missileId) implements CustomPacketPayload {
	public static final Type<ClientboundIcbmRemovePayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(WarMod.MOD_ID,"icbm_remove"));
	public static final StreamCodec<RegistryFriendlyByteBuf,ClientboundIcbmRemovePayload> STREAM_CODEC=StreamCodec.composite(UUIDUtil.STREAM_CODEC,ClientboundIcbmRemovePayload::missileId,ClientboundIcbmRemovePayload::new);
	public boolean isWellFormed(){return missileId!=null;} @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
