package com.andye.warmod.warhead.client.render;

import com.andye.warmod.WarMod;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public final class WarheadRenderPipelines {
	public static final RenderType PROJECTILE = RenderTypes.entityCutout(texture("warhead.png"));
	public static final RenderType CONE = RenderTypes.entityTranslucent(texture("soft_disc.png"));
	public static final RenderType FIREBALL = RenderTypes.entityTranslucentEmissive(texture("soft_disc.png"));
	public static final RenderType SHOCKWAVE = RenderTypes.entityTranslucent(texture("shockwave_strip.png"));

	private WarheadRenderPipelines() {
	}

	private static Identifier texture(final String name) {
		return Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/effect/" + name);
	}
}