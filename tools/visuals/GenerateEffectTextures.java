import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class GenerateEffectTextures {
	private GenerateEffectTextures() {
	}

	public static void main(final String[] args) throws IOException {
		Path outputDirectory = args.length == 0
			? Path.of("src/main/resources/assets/war_mod/textures/effect")
			: Path.of(args[0]);
		Files.createDirectories(outputDirectory);
		writeWarhead(outputDirectory.resolve("warhead.png"));
		writeSoftDisc(outputDirectory.resolve("soft_disc.png"));
		writeShockwaveStrip(outputDirectory.resolve("shockwave_strip.png"));
	}

	private static void writeWarhead(final Path path) throws IOException {
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				float u = x / 31.0F;
				float v = y / 31.0F;
				int base = 76 + Math.round(20.0F * (1.0F - Math.abs(u - 0.5F) * 1.6F));
				if (v > 0.36F && v < 0.64F) {
					base += 16;
				}
				int blue = Math.min(255, base + 8);
				image.setRGB(x, y, new Color(clamp(base), clamp(base + 4), blue, 255).getRGB());
			}
		}
		ImageIO.write(image, "png", path.toFile());
	}

	private static void writeSoftDisc(final Path path) throws IOException {
		BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				double dx = (x + 0.5 - 64.0) / 64.0;
				double dy = (y + 0.5 - 64.0) / 64.0;
				double distance = Math.sqrt(dx * dx + dy * dy);
				double falloff = Math.max(0.0, Math.min(1.0, 1.0 - distance));
				int alpha = (int) Math.round(255.0 * Math.pow(falloff, 0.65));
				image.setRGB(x, y, new Color(255, 255, 255, alpha).getRGB());
			}
		}
		ImageIO.write(image, "png", path.toFile());
	}

	private static void writeShockwaveStrip(final Path path) throws IOException {
		BufferedImage image = new BufferedImage(256, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			double normalized = Math.abs((y + 0.5 - 16.0) / 16.0);
			double band = Math.max(0.0, Math.min(1.0, 1.0 - normalized));
			int alpha = (int) Math.round(255.0 * Math.pow(band, 0.55));
			for (int x = 0; x < image.getWidth(); x++) {
				int brightness = 230 + (x % 17 == 0 ? 8 : 0);
				image.setRGB(x, y, new Color(brightness, brightness, 255, alpha).getRGB());
			}
		}
		ImageIO.write(image, "png", path.toFile());
	}

	private static int clamp(final int value) {
		return Math.max(0, Math.min(255, value));
	}
}