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
 * <p>Stage 7 uses parametric surfaces instead of scanning a large voxel volume.
 * This removes visible scanlines and prevents a cell budget from cutting a cloud
 * in half. Every cell remains exactly one Minecraft block in size.</p>
 */
public final class VoxelImpactCloudRenderer {
	private static final int FACE_NEG_X = 1;
	private static final int FACE_POS_X = 2;
	private static final int FACE_NEG_Y = 4;
	private static final int FACE_POS_Y = 8;
	private static final int FACE_NEG_Z = 16;
	private static final int FACE_POS_Z = 32;
	private static final int MAX_CACHE = 16;
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
		for (Cell cell : mesh.cells) {
			long hash = mix(seed ^ cell.packed);
			double heightFraction = Mth.clamp((cell.y - mesh.minimumY)
				/ Math.max(1.0, mesh.maximumY - mesh.minimumY), 0.0, 1.0);
			/* The stem clears bottom-up and the cap breaks into patches instead of all fading together. */
			if (dissipation > 0.0) {
				double bottomClear = smooth(dissipation * 1.18);
				if (heightFraction < bottomClear * 0.76 && unit(hash, 0) < bottomClear * 0.92) continue;
				if (unit(hash, 1) < dissipation * (0.18 + 0.54 * heightFraction)) continue;
			}
			int variation = (int) (unit(hash, 2) * 32.0);
			int red = Mth.clamp(27 + variation + (int) (dissipation * 58.0), 24, 148);
			int green = Mth.clamp(red + 4, 28, 154);
			int blue = Mth.clamp(red + 10 + (int) (heightFraction * 10.0), 34, 166);
			emitCube(pose, buffer, cell.x, cell.y, cell.z, cell.faces,
				red, green, blue, 255, 0xA000A0);
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
		double intensity = hotPass ? 1.0 - Math.pow(cooling, 1.12)
			: smooth(cooling / 0.18) * Math.pow(1.0 - cooling, 0.54);
		if (intensity <= 0.01) return;
		for (Cell cell : mesh.cells) {
			long hash = mix(seed ^ cell.packed ^ (hotPass ? 0x484F545F564F584CL : 0x434F4F4C5F564F58L));
			if (unit(hash, 0) > intensity * (hotPass ? 1.08 : 0.92)) continue;
			int red;
			int green;
			int blue;
			if (hotPass) {
				red = 255;
				green = Mth.clamp(174 + (int) (unit(hash, 1) * 78.0) - (int) (cooling * 44.0), 126, 252);
				blue = Mth.clamp(34 + (int) (unit(hash, 2) * 86.0), 24, 126);
			} else {
				red = Mth.clamp(232 - (int) (cooling * 132.0), 92, 232);
				green = Mth.clamp(116 - (int) (cooling * 62.0), 44, 124);
				blue = Mth.clamp(28 + (int) (unit(hash, 2) * 34.0), 22, 64);
			}
			emitCube(pose, buffer, cell.x, cell.y, cell.z, cell.faces,
				red, green, blue, 255, hotPass ? 0xF000F0 : 0xD000D0);
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
		Key key = new Key(pass, Mth.floor(age), Float.floatToIntBits(visualScale), seed, lod.ordinal(), sourceHash);
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
		int budget = switch (lod) {
			case NEAR -> pass == 0 ? 14_000 : 9_000;
			case MEDIUM -> pass == 0 ? 7_000 : 4_400;
			case FAR -> pass == 0 ? 2_600 : 1_700;
		};
		SurfaceBuilder builder = new SurfaceBuilder(budget);
		double craterBase = -(6.0 + 10.0 * yieldScale);
		double rise = smooth((age - 18.0) / (250.0 + 90.0 * yieldScale));
		double capGrowth = smooth((age - 54.0) / (165.0 + 45.0 * yieldScale));
		double fireGrowth = smooth(age / 25.0);
		double cooling = smooth((age - profile.fireballHoldEndTick())
			/ Math.max(1.0, profile.fireballCoolingEndTick() - profile.fireballHoldEndTick()));

		if (pass == 0) {
			double stemHeight = 12.0 + (55.0 + 82.0 * yieldScale) * rise;
			double stemRadius = 4.0 + 5.6 * yieldScale;
			double capRadius = (12.0 + 34.0 * yieldScale) * (0.18 + 0.82 * capGrowth);
			double capHalfHeight = (6.0 + 13.0 * yieldScale) * (0.28 + 0.72 * capGrowth);
			/* Separate cluster sources converge continuously into one central stem. */
			for (CloudSource source : sources) {
				double sourceScale = Mth.clamp(source.visualScale() / Math.max(0.2F, visualScale), 0.32, 1.0);
				builder.curvedTube(source.offset().x, source.offset().z, craterBase + 1.0,
					stemHeight * 0.58, stemRadius * sourceScale * 0.82,
					seed ^ source.seed(), lod);
			}
			builder.curvedTube(0.0, 0.0, craterBase + stemHeight * 0.24,
				stemHeight * 0.82, stemRadius * (0.86 + 0.24 * rise), seed ^ 0x5354454D5F535552L, lod);
			double capY = craterBase + stemHeight;
			/* A thick oblate cap connected to the stem; never a one-cell disc. */
			builder.ellipsoidSurface(0.0, capY, 0.0, capRadius, capHalfHeight,
				capRadius, seed ^ 0x4341505F53555246L, lod, 2);
			builder.torusSurface(0.0, capY - capHalfHeight * 0.08, 0.0,
				capRadius * 0.68, Math.max(3.0, capHalfHeight * 0.48),
				seed ^ 0x4341505F52494D30L, lod);
			/* Neck overlap prevents the cap floating above the stem. */
			builder.ellipsoidSurface(0.0, capY - capHalfHeight * 0.55, 0.0,
				stemRadius * 1.55, capHalfHeight * 0.85, stemRadius * 1.55,
				seed ^ 0x4E45434B5F535552L, lod, 1);
		} else {
			/* The initial fire volume is larger than the crater and begins below the old surface. */
			double baseRadius = (19.0 + 25.0 * yieldScale) * fireGrowth * (1.0 - 0.38 * cooling);
			double lift = rise * (32.0 + 54.0 * yieldScale);
			double coreY = craterBase + baseRadius * 0.46 + lift;
			double shellScale = pass == 1 ? 0.90 : 1.08;
			for (CloudSource source : sources) {
				double convergence = smooth(Math.min(1.0, rise * 1.30));
				double cx = source.offset().x * (1.0 - convergence);
				double cz = source.offset().z * (1.0 - convergence);
				builder.ellipsoidSurface(cx, coreY, cz,
					baseRadius * shellScale, baseRadius * 0.80 * shellScale,
					baseRadius * shellScale, seed ^ source.seed() ^ pass, lod, pass == 1 ? 2 : 1);
			}
			/* Hot material is pulled from the crater through the centre into the forming cap. */
			double tongueHeight = Math.max(6.0, lift + baseRadius * 0.92);
			double tongueRadius = Math.max(2.2, baseRadius * (0.25 - 0.09 * cooling));
			builder.curvedTube(0.0, 0.0, craterBase + 1.0, tongueHeight,
				tongueRadius * shellScale, seed ^ 0x464952455F544E47L ^ pass, lod);
			if (rise > 0.28) {
				double capFireRadius = baseRadius * (0.34 + 0.30 * rise) * (1.0 - 0.52 * cooling);
				builder.ellipsoidSurface(0.0, craterBase + tongueHeight, 0.0,
					Math.max(2.0, capFireRadius), Math.max(2.0, capFireRadius * 0.42),
					Math.max(2.0, capFireRadius), seed ^ 0x464952455F434150L ^ pass, lod, 1);
			}
		}

		Map<Long, Byte> occupied = builder.cells;
		ArrayList<Cell> cells = new ArrayList<>(occupied.size());
		int minimumY = Integer.MAX_VALUE;
		int maximumY = Integer.MIN_VALUE;
		for (long packed : occupied.keySet()) {
			int x = unpackX(packed), y = unpackY(packed), z = unpackZ(packed);
			/* Vent gaps reveal the emissive fire beneath the smoke shell. */
			if (pass == 0 && age < profile.fireballCoolingEndTick()) {
				double radial = Math.sqrt((double) x * x + (double) z * z);
				if (radial < 7.0 + 5.0 * yieldScale && unit(seed ^ packed, 4) < 0.19) continue;
			}
			int faces = exposedFaces(occupied, x, y, z);
			if (faces == 0) continue;
			cells.add(new Cell(packed, x, y, z, faces));
			minimumY = Math.min(minimumY, y);
			maximumY = Math.max(maximumY, y);
		}
		if (minimumY == Integer.MAX_VALUE) { minimumY = 0; maximumY = 1; }
		return new Mesh(List.copyOf(cells), minimumY, maximumY);
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
		final int red, final int green, final int blue, final int alpha, final int light) {
		float x0 = x, y0 = y, z0 = z, x1 = x + 1.0F, y1 = y + 1.0F, z1 = z + 1.0F;
		if ((faces & FACE_NEG_X) != 0) quad(pose, buffer,
			x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, red,green,blue,alpha,light,-1,0,0);
		if ((faces & FACE_POS_X) != 0) quad(pose, buffer,
			x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, red,green,blue,alpha,light,1,0,0);
		if ((faces & FACE_NEG_Y) != 0) quad(pose, buffer,
			x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, red,green,blue,alpha,light,0,-1,0);
		if ((faces & FACE_POS_Y) != 0) quad(pose, buffer,
			x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0, red,green,blue,alpha,light,0,1,0);
		if ((faces & FACE_NEG_Z) != 0) quad(pose, buffer,
			x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, red,green,blue,alpha,light,0,0,-1);
		if ((faces & FACE_POS_Z) != 0) quad(pose, buffer,
			x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1, red,green,blue,alpha,light,0,0,1);
	}

	private static void quad(final PoseStack.Pose pose, final VertexConsumer buffer,
		final float ax, final float ay, final float az, final float bx, final float by, final float bz,
		final float cx, final float cy, final float cz, final float dx, final float dy, final float dz,
		final int red, final int green, final int blue, final int alpha, final int light,
		final float nx, final float ny, final float nz) {
		vertex(pose, buffer, ax,ay,az, 0,1, red,green,blue,alpha,light,nx,ny,nz);
		vertex(pose, buffer, bx,by,bz, 1,1, red,green,blue,alpha,light,nx,ny,nz);
		vertex(pose, buffer, cx,cy,cz, 1,0, red,green,blue,alpha,light,nx,ny,nz);
		vertex(pose, buffer, dx,dy,dz, 0,0, red,green,blue,alpha,light,nx,ny,nz);
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

	private static final class SurfaceBuilder {
		private final int budget;
		private final Map<Long, Byte> cells;
		private SurfaceBuilder(final int budget) {
			this.budget = Math.max(128, budget);
			this.cells = new HashMap<>(Math.min(this.budget * 2, 32_768));
		}

		private void add(final double x, final double y, final double z) {
			if (cells.size() >= budget) return;
			cells.put(pack(Mth.floor(x), Mth.floor(y), Mth.floor(z)), (byte) 1);
		}

		private void thicken(final double x, final double y, final double z, final int thickness, final long seed) {
			add(x, y, z);
			if (thickness <= 1 || cells.size() >= budget) return;
			int[][] offsets = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
			int start = (int) (mix(seed) & 5L);
			for (int index = 0; index < offsets.length && index < thickness + 1 && cells.size() < budget; index++) {
				int[] offset = offsets[(start + index) % offsets.length];
				add(x + offset[0], y + offset[1], z + offset[2]);
			}
		}

		private void ellipsoidSurface(final double cx, final double cy, final double cz,
			final double rx, final double ry, final double rz, final long seed,
			final WarheadMesh.Lod lod, final int thickness) {
			if (rx < 0.5 || ry < 0.5 || rz < 0.5 || cells.size() >= budget) return;
			int latitudeSteps = switch (lod) {
				case NEAR -> Math.max(12, (int) Math.ceil(Math.PI * ry * 0.82));
				case MEDIUM -> Math.max(10, (int) Math.ceil(Math.PI * ry * 0.52));
				case FAR -> Math.max(8, (int) Math.ceil(Math.PI * ry * 0.30));
			};
			for (int latitude = 0; latitude <= latitudeSteps && cells.size() < budget; latitude++) {
				double theta = Math.PI * latitude / latitudeSteps;
				double sinTheta = Math.sin(theta);
				double ringRadius = Math.max(rx, rz) * sinTheta;
				int longitudeSteps = switch (lod) {
					case NEAR -> Math.max(12, (int) Math.ceil(Mth.TWO_PI * Math.max(1.0, ringRadius) * 0.86));
					case MEDIUM -> Math.max(10, (int) Math.ceil(Mth.TWO_PI * Math.max(1.0, ringRadius) * 0.54));
					case FAR -> Math.max(8, (int) Math.ceil(Mth.TWO_PI * Math.max(1.0, ringRadius) * 0.31));
				};
				for (int longitude = 0; longitude < longitudeSteps && cells.size() < budget; longitude++) {
					double phi = Mth.TWO_PI * longitude / longitudeSteps;
					double x = cx + rx * sinTheta * Math.cos(phi);
					double y = cy + ry * Math.cos(theta);
					double z = cz + rz * sinTheta * Math.sin(phi);
					thicken(x, y, z, thickness, seed ^ (long) latitude << 32 ^ longitude);
				}
			}
		}

		private void curvedTube(final double sourceX, final double sourceZ, final double baseY,
			final double height, final double radius, final long seed, final WarheadMesh.Lod lod) {
			if (height <= 1.0 || radius <= 0.5 || cells.size() >= budget) return;
			int verticalSteps = switch (lod) {
				case NEAR -> Math.max(8, (int) Math.ceil(height));
				case MEDIUM -> Math.max(7, (int) Math.ceil(height * 0.62));
				case FAR -> Math.max(6, (int) Math.ceil(height * 0.36));
			};
			int ringSteps = switch (lod) {
				case NEAR -> Math.max(12, (int) Math.ceil(Mth.TWO_PI * radius * 0.90));
				case MEDIUM -> Math.max(10, (int) Math.ceil(Mth.TWO_PI * radius * 0.58));
				case FAR -> Math.max(8, (int) Math.ceil(Mth.TWO_PI * radius * 0.34));
			};
			for (int step = 0; step <= verticalSteps && cells.size() < budget; step++) {
				double t = step / (double) verticalSteps;
				double convergence = smooth(t);
				double phase = t * 7.2 + (seed & 1023L) * 0.006;
				double cx = sourceX * (1.0 - convergence) + Math.cos(phase) * (0.35 + 1.4 * t);
				double cz = sourceZ * (1.0 - convergence) + Math.sin(phase) * (0.35 + 1.4 * t);
				double localRadius = radius * (0.82 + 0.24 * t + 0.08 * Math.sin(phase * 1.7));
				double y = baseY + height * t;
				for (int ring = 0; ring < ringSteps && cells.size() < budget; ring++) {
					double angle = Mth.TWO_PI * ring / ringSteps;
					add(cx + Math.cos(angle) * localRadius, y, cz + Math.sin(angle) * localRadius);
				}
			}
		}

		private void torusSurface(final double cx, final double cy, final double cz,
			final double majorRadius, final double minorRadius, final long seed, final WarheadMesh.Lod lod) {
			if (majorRadius <= 1.0 || minorRadius <= 0.5 || cells.size() >= budget) return;
			int majorSteps = switch (lod) {
				case NEAR -> Math.max(20, (int) Math.ceil(Mth.TWO_PI * majorRadius * 0.72));
				case MEDIUM -> Math.max(16, (int) Math.ceil(Mth.TWO_PI * majorRadius * 0.44));
				case FAR -> Math.max(12, (int) Math.ceil(Mth.TWO_PI * majorRadius * 0.26));
			};
			int minorSteps = switch (lod) {
				case NEAR -> Math.max(10, (int) Math.ceil(Mth.TWO_PI * minorRadius * 0.70));
				case MEDIUM -> Math.max(8, (int) Math.ceil(Mth.TWO_PI * minorRadius * 0.44));
				case FAR -> Math.max(6, (int) Math.ceil(Mth.TWO_PI * minorRadius * 0.28));
			};
			for (int major = 0; major < majorSteps && cells.size() < budget; major++) {
				double a = Mth.TWO_PI * major / majorSteps;
				for (int minor = 0; minor < minorSteps && cells.size() < budget; minor++) {
					double b = Mth.TWO_PI * minor / minorSteps;
					double ring = majorRadius + minorRadius * Math.cos(b);
					add(cx + Math.cos(a) * ring, cy + minorRadius * Math.sin(b), cz + Math.sin(a) * ring);
				}
			}
		}
	}

	private static final class MeshCache extends LinkedHashMap<Key, Mesh> {
		private MeshCache() { super(20, 0.75F, true); }
		@Override protected boolean removeEldestEntry(final Map.Entry<Key, Mesh> eldest) { return size() > MAX_CACHE; }
	}
}
