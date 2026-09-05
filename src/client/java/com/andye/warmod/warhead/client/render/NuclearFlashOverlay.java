package com.andye.warmod.warhead.client.render;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.client.ClientWarheadVisualManager;
import com.andye.warmod.warhead.client.ImpactVisualState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Distance- and view-aware nuclear flash/afterimage without changing world time or the sun. */
public final class NuclearFlashOverlay {
	/* Match the previous heavy-nuclear envelope for every nuclear yield. */
	private static final double NUCLEAR_BLINDING_DURATION_TICKS = 156.0;
	private static final double FULL_WHITEOUT_TICKS = 52.0;
	private static boolean registered;
	private NuclearFlashOverlay() { }

	public static void register() {
		if (registered) return;
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
			Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "nuclear_flash"), (graphics, ticker) -> {
				Minecraft client = Minecraft.getInstance();
				if (client.level == null || client.player == null) return;
				double ambientIntensity = 0.0;
				double directIntensity = 0.0;
				boolean fullWhiteout = false;
				long time = client.level.getGameTime();
				float partial = ticker.getGameTimeDeltaPartialTick(true);
				Vec3 eye = client.player.getEyePosition();
				Vec3 look = client.player.getViewVector(partial).normalize();
				for (ImpactVisualState state : ClientWarheadVisualManager.INSTANCE.snapshot(client.level).impacts()) {
					if (state.payloadType() != WarheadPayloadType.NUCLEAR) continue;
					if (!ClientWarheadVisualManager.INSTANCE
						.isNuclearFlashExposed(state.warheadId())) continue;
					Vec3 toImpact = state.impactPosition().subtract(eye);
					double distance = toImpact.length();
					if (!Double.isFinite(distance) || distance > 2_048.0 || distance < 1.0E-5) continue;
					double age = state.ageTicks(time, partial);
					double duration = NUCLEAR_BLINDING_DURATION_TICKS;
					if (age >= duration) continue;
					if (age < FULL_WHITEOUT_TICKS) fullWhiteout = true;
					double distanceFalloff = distance < 120.0 ? 1.0
						: distance < 500.0 ? 0.88 : distance < 1_200.0 ? 0.56 : 0.28;
					double fadeProgress = Math.max(0.0, Math.min(1.0,
						(age - FULL_WHITEOUT_TICKS) / (duration - FULL_WHITEOUT_TICKS)));
					double envelope = age < FULL_WHITEOUT_TICKS
						? 1.0 : Math.pow(1.0 - fadeProgress, 1.35);
					ambientIntensity = Math.max(ambientIntensity, distanceFalloff * envelope);
					double facing = Math.max(0.0, look.dot(toImpact.normalize()));
					double direct = Math.pow(facing, 5.0) * distanceFalloff * envelope;
					directIntensity = Math.max(directIntensity, direct);
				}
				if (ambientIntensity > 0.005) {
					/* Warm low-alpha wash makes night terrain read as briefly daylight-lit. */
					int ambientAlpha = Math.min(224, (int) (ambientIntensity * 214.0));
					graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
						(ambientAlpha << 24) | 0x00FFF6D8);
				}
				if (directIntensity > 0.005) {
					int directAlpha = Math.min(252, (int) (directIntensity * 252.0));
					graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
						(directAlpha << 24) | 0x00FFFDF5);
				}
				if (fullWhiteout) {
					/* A nuclear flash is genuinely blinding for its first 2.6 seconds,
					 * independent of yield, view direction, or the later afterimage. */
					graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFFFFFFFF);
				}
			});
		registered = true;
	}
}
