package com.andye.warmod.warhead.client.render;

import net.minecraft.world.phys.Vec3;

public record TerrainDeformationPatch(Vec3 corner0, Vec3 corner1, Vec3 corner2, Vec3 corner3,
	double cumulativePathDistance, int packedLight, int tintColor,
	float u0, float v0, float u1, float v1, long seed) { }