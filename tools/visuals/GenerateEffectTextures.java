import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class GenerateEffectTextures {
	private static final int FIREBALL_FRAME_COUNT = 8;
	private static final int FIREBALL_FRAME_SIZE = 64;
	private static final int SMOKE_FRAME_COUNT = 4;
	private static final int SMOKE_FRAME_SIZE = 64;

	private GenerateEffectTextures() {
	}

	public static void main(final String[] args) throws IOException {
		Path outputDirectory = args.length == 0
			? Path.of("src/main/resources/assets/war_mod/textures/effect")
			: Path.of(args[0]);
		Path particleDirectory = outputDirectory.resolveSibling("particle");
		Files.createDirectories(outputDirectory);
		Files.createDirectories(particleDirectory);
		if (args.length > 1 && "--icbm-only".equals(args[1])) {
			writeImage(outputDirectory.resolve("icbm_albedo.png"), createIcbmAlbedo());
			verifyImage(outputDirectory.resolve("icbm_albedo.png"), 64, 64);
			return;
		}

		writeImage(outputDirectory.resolve("warhead_albedo.png"), createWarheadAlbedo());
		writeImage(outputDirectory.resolve("icbm_albedo.png"), createIcbmAlbedo());
		writeImage(outputDirectory.resolve("vapor_noise.png"), createVaporNoise());
		writeImage(outputDirectory.resolve("vapor_band.png"), createVaporBand());
		writeImage(outputDirectory.resolve("pressure_shell.png"), createPressureShell());
		writeImage(outputDirectory.resolve("shockwave_strip.png"), createShockwaveStrip());
		writeFireballAssets(outputDirectory.resolve("fireball_sheet.png"), particleDirectory);
		writeImage(outputDirectory.resolve("smoke_lobe.png"), createSmokeLobe());
		writeImage(outputDirectory.resolve("ground_ripple_noise.png"), createGroundRippleNoise());
		writeSmokeAssets(particleDirectory);

		verifyImage(outputDirectory.resolve("warhead_albedo.png"), 64, 64);
		verifyImage(outputDirectory.resolve("icbm_albedo.png"), 64, 64);
		verifyImage(outputDirectory.resolve("vapor_noise.png"), 128, 128);
		verifyImage(outputDirectory.resolve("vapor_band.png"), 128, 32);
		verifyImage(outputDirectory.resolve("pressure_shell.png"), 128, 128);
		verifyImage(outputDirectory.resolve("shockwave_strip.png"), 128, 32);
		verifyImage(outputDirectory.resolve("fireball_sheet.png"), 512, 64);
		verifyImage(outputDirectory.resolve("smoke_lobe.png"), 128, 128);
		verifyImage(outputDirectory.resolve("ground_ripple_noise.png"), 128, 128);
		for (int frame = 0; frame < FIREBALL_FRAME_COUNT; frame++) {
			verifyImage(particleDirectory.resolve("warhead_fireball_" + frame + ".png"), FIREBALL_FRAME_SIZE, FIREBALL_FRAME_SIZE);
		}
		for (int frame = 0; frame < SMOKE_FRAME_COUNT; frame++) {
			verifyImage(particleDirectory.resolve("warhead_smoke_" + frame + ".png"), SMOKE_FRAME_SIZE, SMOKE_FRAME_SIZE);
		}
	}

	private static BufferedImage createWarheadAlbedo() {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				double u = (x + 0.5) / image.getWidth();
				double v = (y + 0.5) / image.getHeight();
				double sideFalloff = 1.0 - Math.min(1.0, Math.abs(u - 0.5) * 2.0);
				double longitudinal = 0.5 + 0.5 * Math.cos((v - 0.5) * Math.PI * 2.0);
				int base = 24 + (int) Math.round(22.0 * sideFalloff + 8.0 * longitudinal);
				if (v > 0.40 && v < 0.60) {
					base += 18;
				}
				if (Math.abs(v - 0.18) < 0.025 || Math.abs(v - 0.82) < 0.025) {
					base += 10;
				}
				int red = clampByte(base);
				int green = clampByte(base + 3);
				int blue = clampByte(base + 7);
				image.setRGB(x, y, rgba(red, green, blue, 255));
			}
		}
		return image;
	}

	private static BufferedImage createIcbmAlbedo() {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
			double u = (x + 0.5) / 64.0, v = (y + 0.5) / 64.0;
			int base = 34 + (int) Math.round(16.0 * (1.0 - Math.abs(u - 0.5) * 2.0));
			if (v < 0.18) base += 22;
			if (Math.abs(v - 0.28) < 0.018 || Math.abs(v - 0.72) < 0.018) base -= 12;
			if (v > 0.88) base = 18;
			image.setRGB(x, y, rgba(base, base + 4, base + 9, 255));
		}
		return image;
	}

	private static BufferedImage createVaporNoise() {
		BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				double noise = noise(x, y, 0x5641504F);
				double edgeDistance = Math.min(
					Math.min(x, image.getWidth() - 1 - x),
					Math.min(y, image.getHeight() - 1 - y)
				);
				double edgeFade = clamp01(edgeDistance / 8.0);
				double alpha = noise < 0.32 ? 0.0 : Math.pow((noise - 0.32) / 0.68, 1.65) * 0.88 * edgeFade;
				if (alpha < 0.025) {
					alpha = 0.0;
				}
				image.setRGB(x, y, rgba(255, 255, 255, clampByte((int) Math.round(alpha * 255.0))));
			}
		}
		return image;
	}

	private static BufferedImage createVaporBand() {
		BufferedImage image = new BufferedImage(128, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			double vertical = Math.abs((y + 0.5 - 16.0) / 16.0);
			double band = Math.max(0.0, 1.0 - vertical);
			for (int x = 0; x < image.getWidth(); x++) {
				double horizontal = Math.sin((x + 0.5) / 128.0 * Math.PI);
				double localNoise = 0.62 + 0.38 * noise(x * 2, y * 3, 0x42414E44);
				double alpha = band < 0.08 ? 0.0 : Math.pow(band, 0.72) * Math.pow(horizontal, 0.35) * localNoise * 0.92;
				image.setRGB(x, y, rgba(255, 255, 255, clampByte((int) Math.round(alpha * 255.0))));
			}
		}
		return image;
	}

	private static BufferedImage createPressureShell() {
		BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				double dx = (x + 0.5 - 64.0) / 64.0;
				double dy = (y + 0.5 - 64.0) / 64.0;
				double distance = Math.sqrt(dx * dx + dy * dy);
				double shell = Math.exp(-Math.pow((distance - 0.72) / 0.12, 2.0));
				double highlight = 0.72 + 0.28 * Math.max(0.0, 1.0 - distance);
				int alpha = clampByte((int) Math.round(shell * highlight * 210.0));
				image.setRGB(x, y, rgba(220, 238, 255, alpha));
			}
		}
		return image;
	}

	private static BufferedImage createShockwaveStrip() {
		BufferedImage image = new BufferedImage(128, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			double vertical = Math.abs((y + 0.5 - 16.0) / 16.0);
			double band = Math.max(0.0, 1.0 - vertical);
			for (int x = 0; x < image.getWidth(); x++) {
				double irregular = 0.82 + 0.18 * noise(x, y * 5, 0x53484F43);
				double alpha = band < 0.08 ? 0.0 : Math.pow(band, 0.55) * irregular * 0.90;
				image.setRGB(x, y, rgba(226, 240, 255, clampByte((int) Math.round(alpha * 255.0))));
			}
		}
		return image;
	}

	private static void writeFireballAssets(final Path sheetPath, final Path particleDirectory) throws IOException {
		BufferedImage sheet = new BufferedImage(FIREBALL_FRAME_SIZE * FIREBALL_FRAME_COUNT, FIREBALL_FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);
		for (int frame = 0; frame < FIREBALL_FRAME_COUNT; frame++) {
			Path framePath = particleDirectory.resolve("warhead_fireball_" + frame + ".png");
			BufferedImage frameImage = ImageIO.read(framePath.toFile());
			if (frameImage == null || frameImage.getWidth() != FIREBALL_FRAME_SIZE || frameImage.getHeight() != FIREBALL_FRAME_SIZE) {
				throw new IOException("Missing or invalid canonical fireball frame: " + framePath);
			}
			for (int y = 0; y < FIREBALL_FRAME_SIZE; y++) for (int x = 0; x < FIREBALL_FRAME_SIZE; x++) {
				sheet.setRGB(frame * FIREBALL_FRAME_SIZE + x, y, frameImage.getRGB(x, y));
			}
		}
		writeImage(sheetPath, sheet);
	}
	private static BufferedImage createFireballFrame(final int frame) {
		BufferedImage image = new BufferedImage(FIREBALL_FRAME_SIZE, FIREBALL_FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);
		double expansion = 0.70 + frame * 0.045;
		for (int y = 0; y < FIREBALL_FRAME_SIZE; y++) {
			for (int x = 0; x < FIREBALL_FRAME_SIZE; x++) {
				double dx = (x + 0.5 - 32.0) / 32.0;
				double dy = (y + 0.5 - 32.0) / 32.0;
				double distance = Math.sqrt(dx * dx + dy * dy);
				double directional = 1.0 + 0.10 * dy - 0.06 * dx;
				double localRadius = expansion * directional * (0.88 + 0.20 * noise(x, y, 0x46495200 + frame * 37));
				double normalized = distance / localRadius;
				double gapNoise = noise(x * 3 + frame * 11, y * 2 - frame * 7, 0x46495247);
				double falloff = clamp01(1.0 - normalized);
				double gaps = normalized < 0.55 ? 1.0 : clamp01((gapNoise - 0.12) / 0.88);
				double alpha = Math.pow(falloff, 0.58) * gaps;
				if (normalized > 1.05 || alpha < 0.015) {
					image.setRGB(x, y, rgba(255, 255, 255, 0));
					continue;
				}

				double core = clamp01((0.58 - normalized) / 0.58);
				double middle = clamp01((0.92 - normalized) / 0.34);
				int red = 255;
				int green = clampByte((int) Math.round(70.0 + 180.0 * core + 75.0 * middle));
				int blue = clampByte((int) Math.round(10.0 + 54.0 * core));
				int finalAlpha = clampByte((int) Math.round(alpha * 255.0));
				image.setRGB(x, y, rgba(red, green, blue, finalAlpha));
			}
		}
		return image;
	}

	private static BufferedImage createSmokeLobe() {
		BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				double dx = (x + 0.5 - 64.0) / 64.0;
				double dy = (y + 0.5 - 64.0) / 64.0;
				double distance = Math.sqrt(dx * dx + dy * dy);
				double localNoise = 0.58 + 0.42 * noise(x, y, 0x534D4F4B);
				double falloff = Math.pow(clamp01(1.0 - distance), 0.72);
				int alpha = clampByte((int) Math.round(falloff * localNoise * 188.0));
				if (distance > 0.96 || alpha < 6) {
					alpha = 0;
				}
				int gray = clampByte((int) Math.round(72.0 + localNoise * 42.0));
				image.setRGB(x, y, rgba(gray, gray + 2, gray + 4, alpha));
			}
		}
		return image;
	}

	private static BufferedImage createGroundRippleNoise() {
		BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				double value = 0.42 + 0.58 * noise(x * 2, y * 2, 0x52495050);
				int shade = clampByte((int) Math.round(118.0 + value * 42.0));
				int alpha = clampByte((int) Math.round(18.0 + value * 34.0));
				image.setRGB(x, y, rgba(shade, shade - 3, shade - 8, alpha));
			}
		}
		return image;
	}

	private static void writeSmokeAssets(final Path particleDirectory) throws IOException {
		for (int frame = 0; frame < SMOKE_FRAME_COUNT; frame++) {
			writeImage(particleDirectory.resolve("warhead_smoke_" + frame + ".png"), createSmokeFrame(frame));
		}
	}

	private static BufferedImage createSmokeFrame(final int frame) {
		BufferedImage image = new BufferedImage(SMOKE_FRAME_SIZE, SMOKE_FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);
		double expansion = 0.72 + frame * 0.06;
		for (int y = 0; y < SMOKE_FRAME_SIZE; y++) {
			for (int x = 0; x < SMOKE_FRAME_SIZE; x++) {
				double dx = (x + 0.5 - 32.0) / 32.0;
				double dy = (y + 0.5 - 32.0) / 32.0;
				double distance = Math.sqrt(dx * dx + dy * dy);
				double localNoise = 0.58 + 0.42 * noise(x + frame * 17, y - frame * 11, 0x534D5000 + frame);
				double falloff = Math.pow(clamp01(1.0 - distance / expansion), 0.58);
				int gray = clampByte((int) Math.round(52.0 + localNoise * 48.0));
				int alpha = clampByte((int) Math.round(falloff * localNoise * 235.0));
				image.setRGB(x, y, rgba(gray, gray + 2, gray + 6, alpha));
			}
		}
		return image;
	}
	private static void writeImage(final Path path, final BufferedImage image) throws IOException {
		Files.createDirectories(path.getParent());
		if (!ImageIO.write(image, "png", path.toFile())) {
			throw new IOException("No PNG writer available for " + path);
		}
	}

	private static void verifyImage(final Path path, final int expectedWidth, final int expectedHeight) throws IOException {
		BufferedImage image = ImageIO.read(path.toFile());
		if (image == null || image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
			throw new IOException("Unexpected PNG dimensions for " + path + ": " + (
				image == null ? "unreadable" : image.getWidth() + "x" + image.getHeight()
			));
		}
		Color center = new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2), true);
		Color edge = new Color(image.getRGB(image.getWidth() - 1, image.getHeight() / 2), true);
		Color corner = new Color(image.getRGB(0, 0), true);
		System.out.printf(
			"verified %-52s %4dx%-4d center=%3d,%3d,%3d,%3d edge=%3d,%3d,%3d,%3d corner=%3d,%3d,%3d,%3d%n",
			path,
			image.getWidth(),
			image.getHeight(),
			center.getRed(),
			center.getGreen(),
			center.getBlue(),
			center.getAlpha(),
			edge.getRed(),
			edge.getGreen(),
			edge.getBlue(),
			edge.getAlpha(),
			corner.getRed(),
			corner.getGreen(),
			corner.getBlue(),
			corner.getAlpha()
		);
	}

	private static int rgba(final int red, final int green, final int blue, final int alpha) {
		return new Color(clampByte(red), clampByte(green), clampByte(blue), clampByte(alpha)).getRGB();
	}

	private static double noise(final int x, final int y, final int seed) {
		long value = (long) x * 0x9E3779B97F4A7C15L
			+ (long) y * 0xC2B2AE3D27D4EB4FL
			+ (long) seed * 0x165667B19E3779F9L;
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		value ^= value >>> 31;
		return (value >>> 11) * (1.0 / (1L << 53));
	}

	private static double clamp01(final double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}

	private static int clampByte(final int value) {
		return Math.max(0, Math.min(255, value));
	}
}