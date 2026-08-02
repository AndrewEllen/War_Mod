package com.andye.warmod.warhead.client.render;

import java.util.List;
import net.minecraft.world.phys.Vec3;

public record TerrainDeformationRenderState(Vec3 impactPosition, double ageTicks, float visualScale,
	WarheadMesh.Lod lod, List<TerrainDeformationPatch> patches) { }