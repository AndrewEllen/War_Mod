package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public record GroundRippleRenderState(
	Vec3 impactPosition,
	List<TerrainShockfrontSpoke> spokes,
	double ageTicks,
	float visualScale,
	WarheadMesh.Lod lod
) { }