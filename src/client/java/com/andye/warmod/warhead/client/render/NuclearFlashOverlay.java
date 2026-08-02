package com.andye.warmod.warhead.client.render;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.client.ClientWarheadVisualManager;
import com.andye.warmod.warhead.client.ImpactVisualState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class NuclearFlashOverlay {
	private static boolean registered;private NuclearFlashOverlay(){}
	public static void register(){if(registered)return;HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,Identifier.fromNamespaceAndPath(WarMod.MOD_ID,"nuclear_flash"),(graphics,ticker)->{Minecraft client=Minecraft.getInstance();if(client.level==null||client.player==null)return;double intensity=0;long time=client.level.getGameTime();for(ImpactVisualState state:ClientWarheadVisualManager.INSTANCE.snapshot(client.level).impacts()){if(state.payloadType()!=WarheadPayloadType.NUCLEAR)continue;double distance=client.player.getEyePosition().distanceTo(state.impactPosition()),age=state.ageTicks(time,ticker.getGameTimeDeltaPartialTick(true));if(distance>1536)continue;double strength=distance<100?1:distance<400?.82:distance<1000?.48:.22;double duration=distance<100?24:distance<400?18:distance<1000?12:8;if(age<duration)intensity=Math.max(intensity,strength*Math.pow(1-age/duration,1.6));}if(intensity>.01){int alpha=Math.min(245,(int)(intensity*245));graphics.fill(0,0,graphics.guiWidth(),graphics.guiHeight(),(alpha<<24)|0x00FFF8E8);}});registered=true;}
}
