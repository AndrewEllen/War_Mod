import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class GenerateGameplayAssets {
    private static final Path ROOT = Path.of("src/main/resources/assets/war_mod/textures");

    public static void main(String[] args) throws IOException {
        block("missile_silo_steel", 0x30363B, false, false);
        block("missile_silo_center_top", 0x252B30, true, true);
        block("missile_silo_edge_top", 0x353B40, false, true);
        block("missile_silo_corner_top", 0x3A4045, false, true);
        controlPanel();
        missile("conventional_icbm", new Color(0x9A7940), false);
        missile("nuclear_icbm", new Color(0xD4B329), true);
        designator("target_designator", new Color(0x36C8D8), false);
        designator("remote_launch_designator", new Color(0xD0522B), true);
        rocketLauncher();
        heRocket(ROOT.resolve("item/he_rocket.png"), 16, 16);
        heRocket(ROOT.resolve("entity/he_rocket.png"), 32, 16);
        block("guidance_frame_steel", 0x30363B, false, false);
        block("guidance_frame_warning", 0xB28B21, false, true);
        block("radar_station_base", 0x343A3E, false, true);
        block("radar_station_mast", 0x252B2F, false, false);
        block("radar_station_motor", 0x3F474C, false, false);
        block("radar_station_warning", 0xA66D1E, false, true);
        designator("radar", new Color(0x69B66F), true);
        rocketTexture(ROOT.resolve("entity/rocket_he.png"), new Color(0x59633D), new Color(0xC39B27));
        rocketTexture(ROOT.resolve("entity/rocket_conventional.png"), new Color(0x454C51), new Color(0xB07828));
        rocketTexture(ROOT.resolve("entity/rocket_nuclear.png"), new Color(0x343A3E), new Color(0xE0BA29));
        rocketTexture(ROOT.resolve("entity/radar_dish.png"), new Color(0x656E72), new Color(0xB87925));
    }

    private static void block(String name, int base, boolean opening, boolean warnings) throws IOException {
        BufferedImage image = image(16, 16, base);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x202529));
        g.drawRect(0, 0, 15, 15);
        g.drawLine(0, 7, 15, 7);
        g.drawLine(7, 0, 7, 15);
        g.setColor(new Color(0x737B80));
        for (int x : new int[]{2, 13}) for (int y : new int[]{2, 13}) g.fillRect(x, y, 1, 1);
        if (opening) {
            g.setColor(new Color(0x080A0C)); g.fillOval(2, 2, 12, 12);
            g.setColor(new Color(0x545B60)); g.drawOval(2, 2, 11, 11);
        }
        if (warnings) {
            for (int x = 0; x < 16; x += 4) {
                g.setColor(new Color(0xC49B24)); g.fillRect(x, 0, 2, 2);
                g.setColor(new Color(0x17191A)); g.fillRect(x + 2, 0, 2, 2);
            }
        }
        g.dispose(); write(ROOT.resolve("block/" + name + ".png"), image);
    }

    private static void controlPanel() throws IOException {
        BufferedImage image = image(16, 16, 0x292F34);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x141719)); g.fillRect(2, 3, 12, 10);
        g.setColor(new Color(0xB98B21)); g.fillRect(4, 5, 5, 3);
        g.setColor(new Color(0xB83228)); g.fillRect(11, 5, 1, 1);
        g.setColor(new Color(0x58A66A)); g.fillRect(11, 8, 1, 1);
        g.setColor(new Color(0x6D757A)); g.drawRect(2, 3, 11, 9);
        g.dispose(); write(ROOT.resolve("block/missile_silo_control_panel.png"), image);
    }

    private static void missile(String name, Color band, boolean hazard) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x41484E)); g.fillRect(6, 3, 4, 10);
        g.setColor(new Color(0x697178)); g.fillRect(7, 2, 2, 1);
        g.setColor(new Color(0x24292D)); g.fillRect(5, 12, 2, 3); g.fillRect(9, 12, 2, 3);
        g.setColor(band); g.fillRect(6, 6, 4, 2);
        if (hazard) { g.setColor(Color.BLACK); g.fillRect(7, 6, 1, 2); g.fillRect(9, 6, 1, 2); }
        g.dispose(); write(ROOT.resolve("item/" + name + ".png"), image);
    }

    private static void designator(String name, Color display, boolean antenna) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x252A2E)); g.fillRect(3, 3, 10, 7); g.fillRect(6, 10, 4, 5);
        g.setColor(display); g.fillRect(5, 5, 6, 3);
        g.setColor(new Color(0x60686E)); g.drawRect(3, 3, 9, 6);
        if (antenna) { g.setColor(new Color(0x858D92)); g.fillRect(11, 0, 1, 4); g.fillRect(10, 0, 3, 1); }
        g.dispose(); write(ROOT.resolve("item/" + name + ".png"), image);
    }

    private static void rocketLauncher() throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x344333)); g.fillRect(1, 5, 14, 5);
        g.setColor(new Color(0x171B19)); g.fillRect(0, 4, 3, 7); g.fillRect(13, 4, 3, 7);
        g.setColor(new Color(0x252A28)); g.fillRect(6, 10, 3, 5); g.fillRect(9, 9, 4, 2);
        g.setColor(new Color(0x687064)); g.drawLine(3, 6, 12, 6);
        g.dispose(); write(ROOT.resolve("item/rocket_launcher.png"), image);
    }

    private static void heRocket(Path path, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        int cy = height / 2;
        g.setColor(new Color(0x59633D)); g.fillRect(3, cy - 2, width - 7, 5);
        g.setColor(new Color(0x25292A)); g.fillRect(width - 4, cy - 1, 3, 3);
        g.setColor(new Color(0xC39B27)); g.fillRect(width / 2, cy - 2, 2, 5);
        g.setColor(new Color(0x30352C)); g.fillRect(1, cy - 4, 3, 3); g.fillRect(1, cy + 2, 3, 3);
        g.dispose(); write(path, image);
    }

    private static void rocketTexture(Path path, Color body, Color band) throws IOException {
        BufferedImage image = image(32, 32, body.getRGB() & 0xFFFFFF);
        Graphics2D g = image.createGraphics();
        g.setColor(body.brighter()); for (int x = 0; x < 32; x += 4) g.drawLine(x, 0, x, 31);
        g.setColor(band); g.fillRect(13, 0, 5, 32);
        g.setColor(new Color(0x202427)); g.drawRect(0, 0, 31, 31);
        g.dispose(); write(path, image);
    }
    private static BufferedImage image(int width, int height, int rgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics(); g.setColor(new Color(rgb)); g.fillRect(0, 0, width, height); g.dispose();
        return image;
    }

    private static void write(Path path, BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "PNG", path.toFile());
    }
}
