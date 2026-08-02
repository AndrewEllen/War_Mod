package com.andye.warmod.icbm.network;

import com.andye.warmod.icbm.IcbmConstants;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class IcbmVisualNetworking {
	private static boolean registered;
	private IcbmVisualNetworking() { }

	public static void registerPayloadTypes() {
		if (registered) return;
		PayloadTypeRegistry.clientboundPlay().register(ClientboundIcbmLaunchPayload.TYPE, ClientboundIcbmLaunchPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundIcbmSeparationPayload.TYPE, ClientboundIcbmSeparationPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundIcbmRemovePayload.TYPE, ClientboundIcbmRemovePayload.STREAM_CODEC);
		registered = true;
	}

	public static void sendLaunch(final ServerLevel level, final ClientboundIcbmLaunchPayload payload, final UUID ownerId) {
		if (!payload.isWellFormed()) return;
		send(level, payload, ownerId, payload.launchPosition(), routeCenter(payload.launchPosition(), payload.intendedTarget()),
			apex(payload), payload.separationPosition(), payload.intendedTarget());
	}

	public static void sendSeparation(final ServerLevel level, final ClientboundIcbmSeparationPayload payload,
		final UUID ownerId, final Vec3 launch, final Vec3 target) {
		if (payload.isWellFormed()) send(level, payload, ownerId, launch, payload.separationPosition(), target);
	}

	public static void sendRemove(final ServerLevel level, final UUID id, final UUID ownerId,
		final Vec3 launch, final Vec3 target) {
		send(level, new ClientboundIcbmRemovePayload(id), ownerId, launch, routeCenter(launch, target), target);
	}

	private static void send(final ServerLevel level, final CustomPacketPayload payload, final UUID ownerId,
		final Vec3... centers) {
		Set<ServerPlayer> recipients = new LinkedHashSet<>();
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner != null && owner.level() == level) recipients.add(owner);
		double rangeSquared = IcbmConstants.CARRIER_VISUAL_RANGE_BLOCKS * IcbmConstants.CARRIER_VISUAL_RANGE_BLOCKS;
		for (ServerPlayer player : PlayerLookup.level(level)) for (Vec3 center : centers) {
			if (center != null && center.isFinite() && player.position().distanceToSqr(center) <= rangeSquared) {
				recipients.add(player);
				break;
			}
		}
		for (ServerPlayer player : recipients) ServerPlayNetworking.send(player, payload);
	}

	private static Vec3 apex(final ClientboundIcbmLaunchPayload payload) {
		double duration = payload.coastTicks();
		Vec3 gravity = new Vec3(0.0, -IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED, 0.0);
		Vec3 velocity = payload.separationPosition().subtract(payload.burnoutPosition())
			.subtract(gravity.scale(0.5 * duration * duration)).scale(1.0 / duration);
		double age = Mth.clamp(velocity.y / IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED, 0.0, duration);
		return payload.burnoutPosition().add(velocity.scale(age)).add(gravity.scale(0.5 * age * age));
	}

	private static Vec3 routeCenter(final Vec3 a, final Vec3 b) { return a.add(b).scale(0.5); }
}