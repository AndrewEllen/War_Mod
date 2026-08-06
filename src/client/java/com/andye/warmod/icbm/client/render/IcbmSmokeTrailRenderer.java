package com.andye.warmod.icbm.client.render;

import com.andye.warmod.icbm.client.IcbmTrailSample;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Dense analytical smoke trail with allocation-free billboard basis reuse. */
public final class IcbmSmokeTrailRenderer {
	private static final double MAX_SEGMENT_DISTANCE = 64.0;
	private IcbmSmokeTrailRenderer() { }

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
		final List<IcbmTrailSample> samples, final IcbmLongRangeRenderContext context,
		final Quaternionf camera) {
		IcbmLongRangeRenderContext.Lod lod = context.lod();
		int limit = lod == IcbmLongRangeRenderContext.Lod.NEAR ? 176
			: lod == IcbmLongRangeRenderContext.Lod.MEDIUM ? 104
			: lod == IcbmLongRangeRenderContext.Lod.FAR ? 48 : 20;
		int stride = lod == IcbmLongRangeRenderContext.Lod.EXTREME ? 3
			: lod == IcbmLongRangeRenderContext.Lod.FAR ? 2 : 1;
		int start = Math.max(0, samples.size() - limit * stride);
		Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera);
		Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera);
		Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera);
		Vec3 previous = null;
		for (int index = start; index < samples.size(); index += stride) {
			IcbmTrailSample sample = samples.get(index);
			if (!sample.position().isFinite() || !sample.drift().isFinite() || !Double.isFinite(sample.ageTicks())) continue;
			Vec3 actual = sample.position().add(sample.drift().scale(sample.ageTicks()));
			if (!actual.isFinite()) continue;
			if (previous != null) {
				double distance = previous.distanceTo(actual);
				if (distance <= 0.001 || distance > MAX_SEGMENT_DISTANCE) { previous = actual; continue; }
			}
			previous = actual;
			double life = 176.0;
			float alpha = (float) Math.max(0.0, Math.min(0.82, 1.0 - sample.ageTicks() / life));
			float lodScale = lod == IcbmLongRangeRenderContext.Lod.EXTREME ? 1.85F : 1.0F;
			float radius = sample.size() * (float) (1.14 + sample.ageTicks() * 0.021) * lodScale
				* (float) context.transform().compression();
			Vec3 center = context.transform().renderPosition(actual);
			billboard(pose, buffer, center, radius, sample.rotation(), alpha, right, up, normal);
			/* A second offset puff fills the gaps without adding trail entities or server work. */
			if (lod != IcbmLongRangeRenderContext.Lod.EXTREME && (index & 1) == 0) {
				billboard(pose, buffer, center.add(0.0, radius * 0.18, 0.0), radius * 0.72F,
					sample.rotation() + 0.73F, alpha * 0.54F, right, up, normal);
			}
		}
	}

	private static void billboard(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 center,
		final float radius, final float rotation, final float alpha,
		final Vector3f right, final Vector3f up, final Vector3f normal) {
		float cosine = Mth.cos(rotation), sine = Mth.sin(rotation);
		float ux = cosine * radius, uy = sine * radius;
		float vx = -sine * radius, vy = cosine * radius;
		vertex(pose, buffer, center, -ux - vx, -uy - vy, 0.0F, 1.0F, alpha, right, up, normal);
		vertex(pose, buffer, center, -ux + vx, -uy + vy, 0.0F, 0.0F, alpha, right, up, normal);
		vertex(pose, buffer, center, ux + vx, uy + vy, 1.0F, 0.0F, alpha, right, up, normal);
		vertex(pose, buffer, center, ux - vx, uy - vy, 1.0F, 1.0F, alpha, right, up, normal);
	}

	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 center,
		final float x, final float y, final float u, final float v, final float alpha,
		final Vector3f right, final Vector3f up, final Vector3f normal) {
		float ox = right.x * x + up.x * y;
		float oy = right.y * x + up.y * y;
		float oz = right.z * x + up.z * y;
		/* Minimum light prevents the plume becoming an unreadable black line at night. */
		buffer.addVertex(pose, (float) center.x + ox, (float) center.y + oy, (float) center.z + oz)
			.setColor(148, 153, 160, (int) (alpha * 255.0F)).setUv(u, v).setOverlay(0)
			.setLight(0xD000D0).setNormal(pose, normal.x, normal.y, normal.z);
	}
}
