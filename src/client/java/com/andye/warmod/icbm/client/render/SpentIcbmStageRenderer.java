package com.andye.warmod.icbm.client.render;
import com.andye.warmod.warhead.client.render.WarheadMesh;import com.mojang.blaze3d.vertex.PoseStack;import com.mojang.blaze3d.vertex.VertexConsumer;
public final class SpentIcbmStageRenderer {private SpentIcbmStageRenderer(){}public static void render(final PoseStack.Pose p,final VertexConsumer b,final WarheadMesh.Lod lod,final int light){IcbmMissileMesh.render(p,b,lod,light);}}
