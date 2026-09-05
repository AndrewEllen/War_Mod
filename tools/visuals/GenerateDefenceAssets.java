import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Reproducible block-material textures and inventory models for defensive hardware. */
public final class GenerateDefenceAssets {
    private static final Path ASSETS = Path.of("src/main/resources/assets/war_mod");
    private static final Path TEXTURES = ASSETS.resolve("textures");

    public static void main(final String[] args) throws Exception {
        metal("block/phalanx_turret_base", 0x3F494E, 0xC58B24);
        metal("block/phalanx_turret_side", 0x4D575C, 0x20272B);
        metal("block/phalanx_turret_top", 0x687277, 0xD49A27);
        screen();
        pipeTextures();

        material("item/anti_air_gun_ammo", 0x3A4244, 0xB77C27, 0x1E2528);
        material("item/radar_linking_tool", 0x2C3736, 0x58B88D, 0x182124);
        material("item/anti_air_missile_mk1", 0xC8C2AA, 0xB77C27, 0x4A4E49);
        material("item/anti_air_missile_mk2", 0xD2CCB5, 0x4AA9B7, 0x4A4E49);
        material("item/anti_air_missile_mk1_marking", 0xA66B1D, 0xD49A27, 0x422D14);
        material("item/anti_air_missile_mk2_marking", 0x277F8C, 0x65C5D0, 0x15383E);
        material("item/anti_air_missile_hardware", 0x424C50, 0x899397, 0x1D2427);
        material("item/anti_air_sensor", 0x102529, 0x55C7D2, 0x071315);
        writeJson("models/item/anti_air_gun_ammo.json", ammunitionModel());
        writeJson("models/item/radar_linking_tool.json", radarLinkerModel());
        writeJson("models/item/anti_air_missile_mk1.json", antiAirMissileModel(false));
        writeJson("models/item/anti_air_missile_mk2.json", antiAirMissileModel(true));
        writePipeModels();
    }

    private static String ammunitionModel() {
        return """
            {"textures":{"case":"war_mod:item/anti_air_gun_ammo","particle":"#case"},
              "elements":[
                {"name":"armoured_case","from":[2,3,3],"to":[14,13,13],"faces":%s},
                {"name":"top_lid","from":[1.6,12.5,2.6],"to":[14.4,14,13.4],"faces":%s},
                {"name":"carry_handle_left","from":[4,14,6],"to":[6,16,10],"faces":%s},
                {"name":"carry_handle_right","from":[10,14,6],"to":[12,16,10],"faces":%s},
                {"name":"carry_handle_bridge","from":[4,15,6],"to":[12,16,10],"faces":%s},
                {"name":"feed_latch","from":[6,7,2.4],"to":[10,11,3.1],"faces":%s},
                {"name":"belt_port","from":[13.8,7,6],"to":[15.5,11,10],"faces":%s}
              ],%s}
            """.formatted(faces("#case"), faces("#case"), faces("#case"), faces("#case"),
                faces("#case"), faces("#case"), faces("#case"), display(.78));
    }

    private static String radarLinkerModel() {
        return """
            {"textures":{"body":"war_mod:item/radar_linking_tool","particle":"#body"},
              "elements":[
                {"name":"receiver_body","from":[4,4,4],"to":[12,13,12],"faces":%s},
                {"name":"rubber_grip","from":[6,0,6],"to":[10,5,10],"faces":%s},
                {"name":"status_screen","from":[5,7,3.4],"to":[11,11,4.1],"faces":%s},
                {"name":"link_button","from":[11.6,5.5,6],"to":[12.5,8,9],"faces":%s},
                {"name":"antenna_left","from":[4.5,13,6],"to":[5.5,16,7],"faces":%s},
                {"name":"antenna_right","from":[10.5,13,9],"to":[11.5,16,10],"faces":%s},
                {"name":"cable_socket","from":[7,12.8,11.5],"to":[9,15,13],"faces":%s}
              ],%s}
            """.formatted(faces("#body"), faces("#body"), faces("#body"), faces("#body"),
                faces("#body"), faces("#body"), faces("#body"), display(.82));
    }

    private static String antiAirMissileModel(final boolean markTwo) {
        String mark = markTwo ? "mk2" : "mk1";
        String sensor = markTwo
            ? "{\"name\":\"seeker_array\",\"from\":[6.4,11.4,6.4],\"to\":[9.6,13.2,9.6],\"faces\":"
                + faces("#sensor") + "},"
            : "{\"name\":\"seeker_cap\",\"from\":[7,11.8,7],\"to\":[9,13.2,9],\"faces\":"
                + faces("#hardware") + "},";
        return """
            {"textures":{"body":"war_mod:item/anti_air_missile_%s","band":"war_mod:item/anti_air_missile_%s_marking","hardware":"war_mod:item/anti_air_missile_hardware","sensor":"war_mod:item/anti_air_sensor","particle":"#body"},
              "elements":[
                %s
                {"name":"body","from":[6.5,2,6.5],"to":[9.5,13,9.5],"faces":%s},
                {"name":"identification_band","from":[6.1,8.8,6.1],"to":[9.9,10.4,9.9],"faces":%s},
                {"name":"nose","from":[6.9,13,6.9],"to":[9.1,15.2,9.1],"faces":%s},
                {"name":"nose_tip","from":[7.5,15.2,7.5],"to":[8.5,16,8.5],"faces":%s},
                {"name":"nozzle","from":[6.8,0.7,6.8],"to":[9.2,2,9.2],"faces":%s},
                {"name":"fin_left","from":[4.6,2.1,7.5],"to":[6.5,6.2,8.5],"faces":%s},
                {"name":"fin_right","from":[9.5,2.1,7.5],"to":[11.4,6.2,8.5],"faces":%s},
                {"name":"fin_front","from":[7.5,2.1,4.6],"to":[8.5,6.2,6.5],"faces":%s},
                {"name":"fin_rear","from":[7.5,2.1,9.5],"to":[8.5,6.2,11.4],"faces":%s}
              ],%s}
            """.formatted(mark, mark, sensor, faces("#body"), faces("#band"),
                faces("#body"), faces("#sensor"), faces("#hardware"),
                faces("#hardware"), faces("#hardware"), faces("#hardware"),
                faces("#hardware"), display(.72));
    }

    private static void writePipeModels() throws Exception {
        String tube = "{\"name\":\"tube\",\"from\":[5,5,0],\"to\":[11,11,5],\"faces\":"
            + faces("#pipe") + "}";
        String collar = "{\"name\":\"coupling_collar\",\"from\":[4.5,4.5,3.5],\"to\":[11.5,11.5,5.5],\"faces\":"
            + faces("#pipe") + "}";
        writeJson("models/block/item_pipe_core.json", """
            {"textures":{"pipe":"war_mod:block/item_pipe","particle":"#pipe"},"elements":[
              {"name":"junction","from":[5,5,5],"to":[11,11,11],"faces":%s},
              {"name":"junction_band","from":[4.5,6,4.5],"to":[11.5,10,11.5],"faces":%s}
            ]}
            """.formatted(faces("#pipe"), faces("#pipe")));
        writeJson("models/block/item_pipe_arm.json", """
            {"textures":{"pipe":"war_mod:block/item_pipe","particle":"#pipe"},"elements":[%s,%s]}
            """.formatted(tube, collar));
        writeJson("models/block/item_pipe_arm_input.json", portArmModel(tube, collar,
            "war_mod:block/item_pipe_input"));
        writeJson("models/block/item_pipe_arm_output.json", portArmModel(tube, collar,
            "war_mod:block/item_pipe_output"));
    }

    private static String portArmModel(final String tube, final String collar,
        final String portTexture) {
        String portFaces = "{\"north\":{\"texture\":\"#port\"},\"south\":{\"texture\":\"#pipe\"},\"east\":{\"texture\":\"#pipe\"},\"west\":{\"texture\":\"#pipe\"},\"up\":{\"texture\":\"#pipe\"},\"down\":{\"texture\":\"#pipe\"}}";
        String port = "{\"name\":\"direction_port\",\"from\":[4.5,4.5,0],\"to\":[11.5,11.5,1.5],\"faces\":"
            + portFaces + "}";
        return """
            {"textures":{"pipe":"war_mod:block/item_pipe","port":"%s","particle":"#pipe"},"elements":[%s,%s,%s]}
            """.formatted(portTexture, tube, collar, port);
    }

    private static String faces(final String texture) {
        return """
            {"north":{"texture":"%s"},"south":{"texture":"%s"},"east":{"texture":"%s"},"west":{"texture":"%s"},"up":{"texture":"%s"},"down":{"texture":"%s"}}
            """.formatted(texture, texture, texture, texture, texture, texture).strip();
    }

    private static String display(final double scale) {
        return """
            "display":{"gui":{"rotation":[25,45,0],"scale":[%s,%s,%s]},"ground":{"scale":[0.35,0.35,0.35]},"fixed":{"rotation":[0,180,0],"scale":[0.6,0.6,0.6]},"firstperson_righthand":{"rotation":[0,-90,20],"translation":[1,2,1],"scale":[0.55,0.55,0.55]},"firstperson_lefthand":{"rotation":[0,90,-20],"translation":[-1,2,1],"scale":[0.55,0.55,0.55]},"thirdperson_righthand":{"rotation":[0,-90,35],"translation":[0,2,1],"scale":[0.45,0.45,0.45]},"thirdperson_lefthand":{"rotation":[0,90,-35],"translation":[0,2,1],"scale":[0.45,0.45,0.45]}}
            """.formatted(scale, scale, scale).strip();
    }

    private static void metal(final String name, final int base, final int marking)
        throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(base));
        graphics.fillRect(0, 0, 16, 16);
        graphics.setColor(new Color(0x1D2326));
        graphics.drawRect(0, 0, 15, 15);
        graphics.drawLine(0, 8, 15, 8);
        graphics.setColor(new Color(marking));
        graphics.fillRect(2, 2, 4, 2);
        graphics.dispose();
        writePng(name, image);
    }

    private static void screen() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x080D0E));
        graphics.fillRect(0, 0, 16, 16);
        graphics.setColor(new Color(0x263238));
        graphics.drawRect(0, 0, 15, 15);
        graphics.setColor(new Color(0x163B31));
        for (int y = 3; y < 14; y += 3) graphics.drawLine(2, y, 13, y);
        graphics.dispose();
        writePng("block/radar_display_screen", image);
        metal("block/radar_display_bezel", 0x242D31, 0x57656A);
    }

    private static void material(final String name, final int base,
        final int accent, final int seam) throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(base));
        graphics.fillRect(0, 0, 16, 16);
        graphics.setColor(new Color(seam));
        graphics.drawRect(0, 0, 15, 15);
        graphics.drawLine(0, 5, 15, 5);
        graphics.drawLine(0, 12, 15, 12);
        graphics.setColor(new Color(accent));
        graphics.fillRect(2, 2, 5, 2);
        graphics.fillRect(10, 7, 3, 4);
        graphics.setColor(new Color(0xB5BDBC));
        for (int x : new int[] { 1, 14 }) {
            for (int y : new int[] { 1, 14 }) graphics.fillRect(x, y, 1, 1);
        }
        graphics.dispose();
        writePng(name, image);
    }

    private static void pipeTextures() throws Exception {
        BufferedImage pipe = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = pipe.createGraphics();
        graphics.setColor(new Color(0x344045));
        graphics.fillRect(0, 0, 16, 16);
        graphics.setColor(new Color(0x222B2F));
        graphics.drawLine(0, 3, 15, 3);
        graphics.drawLine(0, 12, 15, 12);
        graphics.setColor(new Color(0x566267));
        graphics.drawLine(0, 4, 15, 4);
        graphics.drawLine(0, 11, 15, 11);
        graphics.setColor(new Color(0x9CA5A7));
        for (int x : new int[] { 2, 13 }) {
            graphics.fillRect(x, 2, 1, 1);
            graphics.fillRect(x, 13, 1, 1);
        }
        graphics.dispose();
        writePng("block/item_pipe", pipe);
        writePng("block/item_pipe_input", pipePort(pipe, 0x45B7C6, true));
        writePng("block/item_pipe_output", pipePort(pipe, 0xD18F2D, false));
    }

    private static BufferedImage pipePort(final BufferedImage base, final int colour,
        final boolean inward) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.drawImage(base, 0, 0, null);
        graphics.setColor(new Color(0x151B1D));
        graphics.fillRect(3, 5, 10, 6);
        graphics.setColor(new Color(colour));
        int[] xs = inward ? new int[] { 11, 6, 6, 3, 6, 6 }
            : new int[] { 4, 9, 9, 12, 9, 9 };
        graphics.fillPolygon(xs, new int[] { 4, 4, 6, 8, 10, 12 }, 6);
        graphics.dispose();
        return image;
    }

    private static void writeJson(final String relative, final String json) throws Exception {
        Path path = ASSETS.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, json.stripTrailing() + System.lineSeparator(),
            StandardCharsets.UTF_8);
    }

    private static void writePng(final String name, final BufferedImage image) throws Exception {
        Path path = TEXTURES.resolve(name + ".png");
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "PNG", path.toFile());
    }
}
