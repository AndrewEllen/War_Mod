package com.andye.warmod.warhead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

public final class WarheadMesh {
	public enum Lod {
		NEAR,
		MEDIUM,
		FAR
	}

	private static final float BODY_BOTTOM = -0.525F;
	private static final float BODY_TOP = 0.525F;
	private static final float NOSE_TIP = 0.875F;
	private static final float BODY_RADIUS = 0.20F;

	private WarheadMesh() {
	}

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final Lod lod, final int packedLight) {
		int sides = lod == Lod.NEAR ? 12 : lod == Lod.MEDIUM ? 8 : 6;
		renderBody(pose, buffer, sides, packedLight);
		renderNose(pose, buffer, sides, packedLight);
		renderTailCap(pose, buffer, sides, packedLight);
		if (lod == Lod.NEAR) {
			for (int fin = 0; fin < 4; fin++) {
				renderFin(pose, buffer, fin, 0.36F, packedLight);
			}
		} else if (lod == Lod.MEDIUM) {
			for (int fin = 0; fin < 4; fin += 2) {
				renderFin(pose, buffer, fin, 0.32F, packedLight);
			}
		}
	}

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final Lod lod) {
		render(pose, buffer, lod, LightCoordsUtil.FULL_BRIGHT);
	}

	private static void renderBody(final PoseStack.Pose pose, final VertexConsumer buffer, final int sides, final int packedLight) {
		for (int index = 0; index < sides; index++) {
			int next = (index + 1) % sides;
			float angle = Mth.TWO_PI * index / sides;
			float nextAngle = Mth.TWO_PI * next / sides;
			float x = BODY_RADIUS * Mth.cos(angle);
			float z = BODY_RADIUS * Mth.sin(angle);
			float nextX = BODY_RADIUS * Mth.cos(nextAngle);
			float nextZ = BODY_RADIUS * Mth.sin(nextAngle);
			int shade = index % 3 == 0 ? 248 : 224;
			vertex(pose, buffer, x, BODY_BOTTOM, z, 242, 242, 242, 255, (float) index / sides, 1.0F, Mth.cos(angle), 0.0F, Mth.sin(angle), packedLight);
			vertex(pose, buffer, x, BODY_TOP, z, 242, 242, 242, 255, (float) index / sides, 0.0F, Mth.cos(angle), 0.0F, Mth.sin(angle), packedLight);
			vertex(pose, buffer, nextX, BODY_TOP, nextZ, shade, shade, shade + 2, 255, (float) next / sides, 0.0F, Mth.cos(nextAngle), 0.0F, Mth.sin(nextAngle), packedLight);
			vertex(pose, buffer, nextX, BODY_BOTTOM, nextZ, shade, shade, shade + 2, 255, (float) next / sides, 1.0F, Mth.cos(nextAngle), 0.0F, Mth.sin(nextAngle), packedLight);
		}
	}

	private static void renderNose(final PoseStack.Pose pose, final VertexConsumer buffer, final int sides, final int packedLight) {
		for (int index = 0; index < sides; index++) {
			int next = (index + 1) % sides;
			float angle = Mth.TWO_PI * index / sides;
			float nextAngle = Mth.TWO_PI * next / sides;
			float x = BODY_RADIUS * Mth.cos(angle);
			float z = BODY_RADIUS * Mth.sin(angle);
			float nextX = BODY_RADIUS * Mth.cos(nextAngle);
			float nextZ = BODY_RADIUS * Mth.sin(nextAngle);
			float normalY = 0.70F;
			float normalScale = Mth.sqrt(1.0F - normalY * normalY);
			vertex(pose, buffer, x, BODY_TOP, z, 232, 232, 232, 255, (float) index / sides, 1.0F, Mth.cos(angle) * normalScale, normalY, Mth.sin(angle) * normalScale, packedLight);
			vertex(pose, buffer, 0.0F, NOSE_TIP, 0.0F, 248, 248, 248, 255, 0.5F, 0.0F, Mth.cos(angle) * normalScale, normalY, Mth.sin(angle) * normalScale, packedLight);
			vertex(pose, buffer, nextX, BODY_TOP, nextZ, 218, 218, 220, 255, (float) (next + 1) / sides, 1.0F, Mth.cos(nextAngle) * normalScale, normalY, Mth.sin(nextAngle) * normalScale, packedLight);
		}
	}

	private static void renderTailCap(final PoseStack.Pose pose, final VertexConsumer buffer, final int sides, final int packedLight) {
		for (int index = 1; index < sides - 1; index++) {
			float angle = Mth.TWO_PI * index / sides;
			float nextAngle = Mth.TWO_PI * (index + 1) / sides;
			vertex(pose, buffer, 0.0F, BODY_BOTTOM, 0.0F, 206, 206, 208, 255, 0.5F, 0.5F, 0.0F, -1.0F, 0.0F, packedLight);
			vertex(pose, buffer, BODY_RADIUS * Mth.cos(angle), BODY_BOTTOM, BODY_RADIUS * Mth.sin(angle), 206, 206, 208, 255, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F, packedLight);
			vertex(pose, buffer, BODY_RADIUS * Mth.cos(nextAngle), BODY_BOTTOM, BODY_RADIUS * Mth.sin(nextAngle), 206, 206, 208, 255, 1.0F, 0.0F, 0.0F, -1.0F, 0.0F, packedLight);
		}
	}

	private static void renderFin(final PoseStack.Pose pose, final VertexConsumer buffer, final int fin, final float outerRadius, final int packedLight) {
		float angle = Mth.TWO_PI * fin / 4.0F;
		float normalX = Mth.cos(angle);
		float normalZ = Mth.sin(angle);
		float tangentX = -normalZ;
		float tangentZ = normalX;
		float innerBackY = -0.63F;
		float outerBackY = -0.54F;
		float outerFrontY = -0.28F;
		float innerRadius = 0.16F;
		float thickness = 0.018F;
		float[] radii = {innerRadius, outerRadius, outerRadius, innerRadius};
		float[] ys = {innerBackY, outerBackY, outerFrontY, -0.34F};
		float[] tangent = {-0.10F, -0.02F, 0.02F, 0.08F};
		for (int side = 0; side < 2; side++) {
			float offset = side == 0 ? -thickness : thickness;
			for (int index = 0; index < 4; index++) {
				float x = normalX * radii[index] + tangentX * (tangent[index] + offset);
				float z = normalZ * radii[index] + tangentZ * (tangent[index] + offset);
				float nx = side == 0 ? normalX : -normalX;
				float nz = side == 0 ? normalZ : -normalZ;
				vertex(pose, buffer, x, ys[index], z, 218, 218, 220, 255, index == 0 || index == 3 ? 0.0F : 1.0F, index == 0 || index == 3 ? 1.0F : 0.0F, nx, 0.0F, nz, packedLight);
			}
		}
	}

	private static void vertex(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final float x,
		final float y,
		final float z,
		final int red,
		final int green,
		final int blue,
		final int alpha,
		final float u,
		final float v,
		final float normalX,
		final float normalY,
		final float normalZ,
		final int packedLight
	) {
		buffer.addVertex(pose, x, y, z)
			.setColor(red, green, blue, alpha)
			.setUv(u, v)
			.setOverlay(0)
			.setLight(packedLight)
			.setNormal(pose, normalX, normalY, normalZ);
	}
}