package com.andye.warmod.entity.client;

import com.andye.warmod.entity.TimedWarheadTntEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Keeps custom timed charges trackable; their fuse/impact effects are server-authoritative. */
public final class TimedWarheadTntRenderer extends EntityRenderer<TimedWarheadTntEntity, EntityRenderState> {
    public TimedWarheadTntRenderer(final EntityRendererProvider.Context context) { super(context); shadowRadius = 0.2F; }
    @Override public EntityRenderState createRenderState() { return new EntityRenderState(); }
}
