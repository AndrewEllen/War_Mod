package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Nuclear-only one-block voxel plume.
 *
 * <p>Every visible cell is exactly one Minecraft block in size. Yield changes the
 * number and occupied volume of cells, never their physical scale. The generator
 * builds a crater-coupled hot core, converging stems and a rolling cap, then emits
 * only exposed faces. Nearby cluster sources feed the same volume.</p>
 */
public final class VoxelImpactCloudRenderer {
	private static final int FACE_NEG_X = 1;
	private static final int FACE_POS_X = 2;
	private static final int FACE_NEG_Y = 4;
	private static final int FACE_POS_Y = 8;
	private static final int FACE_NEG_Z = 16;
	private static final int FACE_POS_Z = 32;
	private static final int MAX_CACHE = 12;
	private static final ThreadLocal<MeshCache> CACHE = ThreadLocal.withInitial(MeshCache::new);

	private VoxelImpactCloudRenderer() { }

	public record CloudSource(Vec3 offset, double ageTicks, float visualScale, long seed) {
		public CloudSource {
			if (offset == null || !offset.isFinite()) offset = Vec3.ZERO;
		}
	}

	public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod) {
		renderSmoke(pose, buffer, age, visualScale, profile, seed, lod,
			List.of(new CloudSource(Vec3.ZERO, age, visualScale, seed)));
	}

	public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final List<CloudSource> sources) {
		if (profile == null || profile.payloadType() != WarheadPayloadType.NUCLEAR
			|| age < profile.smokeStartTick() || age >= profile.cloudDissipationEndTick()) return;
		Mesh mesh = mesh(0, age, visualScale, profile, seed, lod, sources);
		double dissipation = WarheadVisualMath.clamp((age - profile.cloudRiseEndTick())
			/ Math.max(1.0, profile.cloudDissipationEndTick() - profile.cloudRiseEndTick()), 0.0, 1.0);
		float globalAlpha = (float) Math.pow(1.0 - dissipation, 0.58);
		for (Cell cell : mesh.cells) {
			long hash = mix(seed ^ cell.packed);
			double breakup = unit(hash, 0);
			if (dissipation > 0.08 && breakup < dissipation * 0.62) continue;
			double heightFraction = Mth.clamp((cell.y - mesh.minimumY) / Math.max(1.0, mesh.maximumY - mesh.minimumY), 0.0, 1.0);
			int variation = (int) (unit(hash, 1) * 34.0);
			int red = Mth.clamp(28 + variation + (int) (dissipation * 48.0), 24, 136);
			int green = Mth.clamp(red + 4, 28, 142);
			int blue = Mth.clamp(red + 10 + (int) (heightFraction * 8.0), 34, 154);
			float alpha = (float) Mth.clamp((0.78 + unit(hash, 2) * 0.16) * globalAlpha, 0.0, 0.94);
			emitCube(pose, buffer, cell.x, cell.y, cell.z, cell.faces, red, green, blue, alpha, 0xA000A0);
		}
	}

	public static void renderFire(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final boolean hotPass) {
		renderFire(pose, buffer, age, visualScale, profile, seed, lod, hotPass,
			List.of(new CloudSource(Vec3.ZERO, age, visualScale, seed)));
	}

	public static void renderFire(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final boolean hotPass,
		final List<CloudSource> sources) {
		if (profile == null || profile.payloadType() != WarheadPayloadType.NUCLEAR
			|| age < profile.fireballGrowthStartTick() || age >= profile.fireballCoolingEndTick()) return;
		Mesh mesh = mesh(hotPass ? 1 : 2, age, visualScale, profile, seed, lod, sources);
		double cooling = WarheadVisualMath.clamp((age - profile.fireballHoldEndTick())
			/ Math.max(1.0, profile.fireballCoolingEndTick() - profile.fireballHoldEndTick()), 0.0, 1.0);
		double intensity = hotPass ? 1.0 - Math.pow(cooling, 1.16)
			: smooth(cooling / 0.22) * Math.pow(1.0 - cooling, 0.62);
		if (intensity <= 0.01) return;
		for (Cell cell : mesh.cells) {
			long hash = mix(seed ^ cell.packed ^ (hotPass ? 0x484F545F564F584CL : 0x434F4F4C5F564F58L));
			int red;
			int green;
			int blue;
			if (hotPass) {
				red = 255;
				green = Mth.clamp(158 + (int) (unit(hash, 0) * 82.0) - (int) (cooling * 42.0), 118, 240);
				blue = Mth.clamp(22 + (int) (unit(hash, 1) * 58.0), 18, 86);
			} else {
				red = Mth.clamp(208 - (int) (cooling * 112.0), 86, 208);
				green = Mth.clamp(96 - (int) (cooling * 48.0), 42, 104);
				blue = Mth.clamp(24 + (int) (unit(hash, 1) * 28.0), 20, 56);
			}
			float alpha = (float) Mth.clamp((hotPass ? 0.92 : 0.78) * intensity, 0.0, 0.94);
			emitCube(pose, buffer, cell.x, cell.y, cell.z, cell.faces, red, green, blue, alpha,
				hotPass ? 0xF000F0 : 0xD000D0);
		}
	}

	private static Mesh mesh(final int pass, final double age, final float visualScale,
		final WarheadClientVisualProfile profile, final long seed, final WarheadMesh.Lod lod,
		final List<CloudSource> sources) {
		long sourceHash = 0L;
		if (sources != null) {
			for (CloudSource source : sources) {
				sourceHash ^= mix(source.seed() ^ Double.doubleToLongBits(source.offset().x)
					^ Long.rotateLeft(Double.doubleToLongBits(source.offset().z), 17));
			}
		}
		int ageBucket = Mth.floor(age);
		Key key = new Key(pass, ageBucket, Float.floatToIntBits(visualScale), seed, lod.ordinal(), sourceHash);
		Mesh cached = CACHE.get().get(key);
		if (cached != null) return cached;
		Mesh created = build(pass, age, visualScale, profile, seed, lod,
			sources == null || sources.isEmpty()
				? List.of(new CloudSource(Vec3.ZERO, age, visualScale, seed)) : sources);
		CACHE.get().put(key, created);
		return created;
	}

	private static Mesh build(final int pass, final double age, final float visualScale,
		final WarheadClientVisualProfile profile, final long seed, final WarheadMesh.Lod lod,
		final List<CloudSource> sources) {
		float yieldScale = Mth.clamp(visualScale / 2.70F, 0.62F, 1.34F);
		double rise = smooth((age - profile.smokeStartTick())
			/ Math.max(1.0, profile.cloudRiseEndTick() - profile.smokeStartTick()));
		double fireGrowth = smooth(age / Math.max(1.0, profile.fireballGrowthEndTick()));
		double craterBase = -(7.0 + 15.0 * yieldScale);
		double height = (58.0 + 76.0 * yieldScale) * (0.18 + 0.82 * rise);
		double capRadius = (20.0 + 30.0 * yieldScale) * (0.24 + 0.76 * rise);
		double stemRadius = 3.0 + 4.8 * yieldScale;
		int stride = lod == WarheadMesh.Lod.NEAR ? 1 : lod == WarheadMesh.Lod.MEDIUM ? 2 : 3;
		int cellBudget = switch (lod) {
			case NEAR -> pass == 0 ? 9_500 : 5_500;
			case MEDIUM -> pass == 0 ? 4_500 : 2_600;
			case FAR -> pass == 0 ? 1_500 : 900;
		};
		Map<Long, Byte> occupied = new HashMap<>(Math.min(cellBudget * 2, 24_000));

		if (pass == 0) {
			/* Each impact feeds a narrowing lower plume that converges into one shared stem. */
			for (CloudSource source : sources) {
				double sourceStrength = Mth.clamp(source.visualScale() / Math.max(0.2F, visualScale), 0.35, 1.0);
				int steps = Math.max(5, (int) ((height * 0.58) / stride));
				for (int step = 0; step <= steps; step++) {
					double t = step / (double) steps;
					double convergence = smooth(t);
					double cx = source.offset().x * (1.0 - convergence);
					double cz = source.offset().z * (1.0 - convergence);
					double y = craterBase + t * height * 0.68;
					double swirl = (1.0 - t) * 0.8 + t * 2.2;
					double phase = t * 7.0 + (source.seed() & 1023L) * 0.006;
					cx += Math.cos(phase) * swirl;
					cz += Math.sin(phase) * swirl;
					double radius = stemRadius * sourceStrength * (0.72 + 0.40 * t);
					stampSphere(occupied, cx, y, cz, radius, stride, cellBudget, seed ^ source.seed());
					if (occupied.size() >= cellBudget) break;
				}
			}
			/* Rolling cap: an oblate centre plus a toroidal outer rim. */
			double capY = craterBase + height;
			stampEllipsoid(occupied, 0.0, capY, 0.0, capRadius * 0.70,
				capRadius * 0.33, capRadius * 0.70, stride, cellBudget, seed ^ 0x4341505F434F5245L);
			int ringSamples = Math.max(18, (int) (capRadius * 2.2 / stride));
			for (int index = 0; index < ringSamples && occupied.size() < cellBudget; index++) {
				double angle = index / (double) ringSamples * Mth.TWO_PI;
				double ring = capRadius * (0.60 + 0.08 * Math.sin(angle * 5.0 + age * 0.025));
				double cx = Math.cos(angle) * ring;
				double cz = Math.sin(angle) * ring;
				double cy = capY - capRadius * 0.05 + Math.sin(angle * 3.0) * capRadius * 0.06;
				stampSphere(occupied, cx, cy, cz, capRadius * 0.22, stride, cellBudget,
					seed ^ (long) index * 0x9E3779B97F4A7C15L);
			}
			/* A restrained crater-connected dust skirt; never render-distance sized. */
			double skirtRadius = capRadius * 0.68;
			int skirtSamples = Math.max(14, (int) (skirtRadius * 1.6 / stride));
			for (int index = 0; index < skirtSamples && occupied.size() < cellBudget; index++) {
				double angle = index / (double) skirtSamples * Mth.TWO_PI;
				double radial = skirtRadius * (0.62 + 0.28 * rise);
				stampSphere(occupied, Math.cos(angle) * radial, craterBase + 3.0 + unit(seed, index) * 3.0,
					Math.sin(angle) * radial, 2.5 + 2.2 * yieldScale, stride, cellBudget,
					seed ^ (long) index * 0xD1B54A32D192ED03L);
			}
		} else {
			/* Emissive hot material begins in the crater and is progressively pulled up the stem. */
			double cooling = WarheadVisualMath.clamp((age - profile.fireballHoldEndTick())
				/ Math.max(1.0, profile.fireballCoolingEndTick() - profile.fireballHoldEndTick()), 0.0, 1.0);
			double coreRadius = (10.0 + 14.0 * yieldScale) * (0.22 + 0.78 * fireGrowth) * (1.0 - 0.34 * cooling);
			double coreY = craterBase + coreRadius * 0.62 + rise * height * 0.30;
			for (CloudSource source : sources) {
				double convergence = smooth(Math.min(1.0, rise * 1.35));
				double cx = source.offset().x * (1.0 - convergence);
				double cz = source.offset().z * (1.0 - convergence);
				stampEllipsoid(occupied, cx, coreY, cz, coreRadius, coreRadius * 0.78,
					coreRadius, stride, cellBudget, seed ^ source.seed());
			}
			double tongueHeight = height * (0.18 + 0.38 * rise);
			int steps = Math.max(4, (int) (tongueHeight / stride));
			for (int step = 0; step <= steps && occupied.size() < cellBudget; step++) {
				double t = step / (double) steps;
				double y = craterBase + coreRadius * 0.35 + t * tongueHeight;
				double phase = t * 8.0 + age * 0.028;
				double radius = Math.max(2.0, coreRadius * (0.40 - 0.25 * t));
				stampSphere(occupied, Math.cos(phase) * 1.8 * t, y,
					Math.sin(phase) * 1.8 * t, radius, stride, cellBudget, seed ^ step);
			}
			if (pass == 2) {
				/* Cooling orange shell surrounds, rather than replaces, the hot core. */
				stampEllipsoid(occupied, 0.0, coreY, 0.0, coreRadius * 1.12,
					coreRadius * 0.90, coreRadius * 1.12, stride, cellBudget, seed ^ 0x434F4F4C5F53484CL);
			}
		}

		ArrayList<Cell> cells = new ArrayList<>(occupied.size());
		int minimumY = Integer.MAX_VALUE;
		int maximumY = Integer.MIN_VALUE;
		for (long packed : occupied.keySet()) {
			int x = unpackX(packed), y = unpackY(packed), z = unpackZ(packed);
			int faces = exposedFaces(occupied, x, y, z);
			if (faces == 0) continue;
			cells.add(new Cell(packed, x, y, z, faces));
			minimumY = Math.min(minimumY, y);
			maximumY = Math.max(maximumY, y);
		}
		if (minimumY == Integer.MAX_VALUE) { minimumY = 0; maximumY = 1; }
		return new Mesh(List.copyOf(cells), minimumY, maximumY);
	}

	private static void stampSphere(final Map<Long, Byte> occupied, final double cx, final double cy,
		final double cz, final double radius, final int stride, final int budget, final long seed) {
		stampEllipsoid(occupied, cx, cy, cz, radius, radius, radius, stride, budget, seed);
	}

	private static void stampEllipsoid(final Map<Long, Byte> occupied, final double cx, final double cy,
		final double cz, final double rx, final double ry, final double rz,
		final int stride, final int budget, final long seed) {
		int minX = Mth.floor(cx - rx), maxX = Mth.ceil(cx + rx);
		int minY = Mth.floor(cy - ry), maxY = Mth.ceil(cy + ry);
		int minZ = Mth.floor(cz - rz), maxZ = Mth.ceil(cz + rz);
		for (int y = minY; y <= maxY && occupied.size() < budget; y += stride) {
			for (int z = minZ; z <= maxZ && occupied.size() < budget; z += stride) {
				for (int x = minX; x <= maxX && occupied.size() < budget; x += stride) {
					double nx = (x + 0.5 - cx) / Math.max(0.5, rx);
					double ny = (y + 0.5 - cy) / Math.max(0.5, ry);
					double nz = (z + 0.5 - cz) / Math.max(0.5, rz);
					double density = nx * nx + ny * ny + nz * nz;
					if (density > 1.0) continue;
					long packed = pack(x, y, z);
					if (unit(seed ^ packed, 0) < 0.035 * density) continue;
					occupied.put(packed, (byte) 1);
				}
			}
		}
	}

	private static int exposedFaces(final Map<Long, Byte> occupied, final int x, final int y, final int z) {
		int faces = 0;
		if (!occupied.containsKey(pack(x - 1, y, z))) faces |= FACE_NEG_X;
		if (!occupied.containsKey(pack(x + 1, y, z))) faces |= FACE_POS_X;
		if (!occupied.containsKey(pack(x, y - 1, z))) faces |= FACE_NEG_Y;
		if (!occupied.containsKey(pack(x, y + 1, z))) faces |= FACE_POS_Y;
		if (!occupied.containsKey(pack(x, y, z - 1))) faces |= FACE_NEG_Z;
		if (!occupied.containsKey(pack(x, y, z + 1))) faces |= FACE_POS_Z;
		return faces;
	}

	private static void emitCube(final PoseStack.Pose pose, final VertexConsumer buffer,
		final int x, final int y, final int z, final int faces,
		final int red, final int green, final int blue, final float alpha, final int light) {
		float x0 = x, y0 = y, z0 = z, x1 = x + 1.0F, y1 = y + 1.0F, z1 = z + 1.0F;
		int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		if ((faces & FACE_NEG_X) != 0) face(pose, buffer, x0,y0,z1, x0,y1,z0, red,green,blue,a,light, -1,0,0);
		if ((faces & FACE_POS_X) != 0) face(pose, buffer, x1,y0,z0, x1,y1,z1, red,green,blue,a,light, 1,0,0);
		if ((faces & FACE_NEG_Y) != 0) face(pose, buffer, x0,y0,z0, x1,y0,z1, red,green,blue,a,light, 0,-1,0);
		if ((faces & FACE_POS_Y) != 0) face(pose, buffer, x0,y1,z1, x1,y1,z0, red,green,blue,a,light, 0,1,0);
		if ((faces & FACE_NEG_Z) != 0) face(pose, buffer, x1,y0,z0, x0,y1,z0, red,green,blue,a,light, 0,0,-1);
		if ((faces & FACE_POS_Z) != 0) face(pose, buffer, x0,y0,z1, x1,y1,z1, red,green,blue,a,light, 0,0,1);
	}

	private static void face(final PoseStack.Pose pose, final VertexConsumer buffer,
		final float x0, final float y0, final float z0, final float x1, final float y1, final float z1,
		final int red, final int green, final int blue, final int alpha, final int light,
		final float nx, final float ny, final float nz) {
		if (nx != 0.0F) {
			vertex(pose,buffer,x0,y0,z0,0,1,red,green,blue,alpha,light,nx,ny,nz);
			vertex(pose,buffer,x0,y1,z1,0,0,red,green,blue,alpha,light,nx,ny,nz);
			vertex(pose,buffer,x1,y1,z1,1,0,red,green,blue,alpha,light,nx,ny,nz);
			vertex(pose,buffer,x1,y0,z0,1,1,red,green,blue,alpha,light,nx,ny,nz);
		} else if (ny != 0.0F) {
			vertex(pose,buffer,x0,y0,z0,0,1,red,green,blue,alpha,light,nx,ny,nz);
			vertex(pose,buffer,x0,y0,z1,0,0,red,green,blue,alpha,light,nx,ny,nz);
			vertex(pose,buffer,x1,y1,z1,1,0,red,green,blue,alpha,light,nx,ny,nz);
			vertex(pose,buffer,x1,y1,z0,1,1,red,green,blue,alpha,light,nx,ny,nz);
		} else {
			vertex(pose,buffer,x0,y0,z0,0,1,red,green,blue,alpha,light,nx,ny,nz);
			vertex(pose,buffer,x0,y1,z0,0,0,red,green,blue,alpha,light,nx,ny,nz);
			vertex(pose,buffer,x1,y1,z1,1,0,red,green,blue,alpha,light,nx,ny,nz);
			vertex(pose,buffer,x1,y0,z1,1,1,red,green,blue,alpha,light,nx,ny,nz);
		}
	}

	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
		final float x, final float y, final float z, final float u, final float v,
		final int red, final int green, final int blue, final int alpha, final int light,
		final float nx, final float ny, final float nz) {
		buffer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha).setUv(u, v)
			.setOverlay(0).setLight(light).setNormal(pose, nx, ny, nz);
	}

	private static long pack(final int x, final int y, final int z) {
		return ((long) (x + 2048) & 0xFFFL) << 24
			| ((long) (y + 2048) & 0xFFFL) << 12
			| ((long) (z + 2048) & 0xFFFL);
	}
	private static int unpackX(final long packed) { return (int) ((packed >>> 24) & 0xFFFL) - 2048; }
	private static int unpackY(final long packed) { return (int) ((packed >>> 12) & 0xFFFL) - 2048; }
	private static int unpackZ(final long packed) { return (int) (packed & 0xFFFL) - 2048; }
	private static double smooth(final double value) {
		double t = WarheadVisualMath.clamp(value, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
	private static double unit(final long value, final int lane) {
		long mixed = mix(value + (long) lane * 0x9E3779B97F4A7C15L);
		return (mixed >>> 11) * 0x1.0p-53;
	}
	private static long mix(long value) {
		value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27; value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	private record Key(int pass, int age, int scale, long seed, int lod, long sources) { }
	private record Cell(long packed, int x, int y, int z, int faces) { }
	private record Mesh(List<Cell> cells, int minimumY, int maximumY) { }
	private static final class MeshCache extends LinkedHashMap<Key, Mesh> {
		private MeshCache() { super(16, 0.75F, true); }
		@Override protected boolean removeEldestEntry(final Map.Entry<Key, Mesh> eldest) { return size() > MAX_CACHE; }
	}
}
