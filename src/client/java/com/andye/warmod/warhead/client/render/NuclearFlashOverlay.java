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
				long time = client.level.getGameTime();
				float partial = ticker.getGameTimeDeltaPartialTick(true);
				Vec3 eye = client.player.getEyePosition();
				Vec3 look = client.player.getViewVector(partial).normalize();
				for (ImpactVisualState state : ClientWarheadVisualManager.INSTANCE.snapshot(client.level).impacts()) {
					if (state.payloadType() != WarheadPayloadType.NUCLEAR) continue;
					Vec3 toImpact = state.impactPosition().subtract(eye);
					double distance = toImpact.length();
					if (!Double.isFinite(distance) || distance > 2_048.0 || distance < 1.0E-5) continue;
					double age = state.ageTicks(time, partial);
					double yield = Math.max(0.6, state.visualScale() / 2.7);
					double duration = 30.0 + 44.0 * yield;
					if (age >= duration) continue;
					double distanceFalloff = distance < 120.0 ? 1.0
						: distance < 500.0 ? 0.88 : distance < 1_200.0 ? 0.56 : 0.28;
					double envelope = age < 5.0 ? 1.0 : Math.pow(1.0 - age / duration, 1.35);
					ambientIntensity = Math.max(ambientIntensity, distanceFalloff * envelope * Math.min(1.0, yield));
					double facing = Math.max(0.0, look.dot(toImpact.normalize()));
					double direct = Math.pow(facing, 5.0) * distanceFalloff * envelope * Math.min(1.25, yield);
					directIntensity = Math.max(directIntensity, direct);
				}
				if (ambientIntensity > 0.005) {
					/* Warm low-alpha wash makes night terrain read as briefly daylight-lit. */
					int ambientAlpha = Math.min(170, (int) (ambientIntensity * 150.0));
					graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
						(ambientAlpha << 24) | 0x00FFF0C8);
				}
				if (directIntensity > 0.005) {
					int directAlpha = Math.min(250, (int) (directIntensity * 245.0));
					graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
						(directAlpha << 24) | 0x00FFFDF5);
				}
			});
		registered = true;
	}
}
