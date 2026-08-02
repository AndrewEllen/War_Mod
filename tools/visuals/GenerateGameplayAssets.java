import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class GenerateGameplayAssets {
    private static final Path ASSETS = Path.of("src/main/resources/assets/war_mod");
    private static final Path TEXTURES = ASSETS.resolve("textures");

    public static void main(String[] args) throws IOException {
        generateTextures();
        generateGui();
        generateItemModels();
        generateGuidanceModels();
        generateItemDefinitions();
    }

    private static void generateTextures() throws IOException {
        metalTexture("guidance_tier_1", 0x4B545A, 0x78848A);
        metalTexture("guidance_tier_2", 0x3E4A50, 0x88959A);
        metalTexture("guidance_tier_3", 0x303A40, 0x9DA8AC);
        warningTexture("guidance_warning", 0xD1A326);
        metalTexture("guidance_hydraulic", 0x263038, 0x7B8990);
        metalTexture("radar_station_base", 0x343C41, 0x758087);
        metalTexture("radar_station_panel", 0x20282D, 0x4F5C63);
        metalTexture("radar_station_motor", 0x3D484E, 0x89959B);
        metalTexture("radar_station_support", 0x465158, 0x929DA2);
        warningTexture("radar_station_warning", 0xC58B24);
        metalTexture("radar_dish_front", 0x737F83, 0xA7B1B4, "entity");
        metalTexture("radar_dish_back", 0x444E53, 0x707C81, "entity");
        metalTexture("radar_receiver", 0x59656A, 0xD18A2E, "entity");
        itemTexture("rocket_launcher", 0x344333, 0x171B19, 0x78816F);
        itemTexture("target_designator", 0x292F34, 0x39C9DB, 0x68737A);
        itemTexture("remote_launch_designator", 0x252A2E, 0xD28A2F, 0xB73A2E);
        itemTexture("radar", 0x293237, 0x71B56D, 0xC18C2D);
        itemTexture("conventional_icbm", 0x40484E, 0x98783C, 0x252A2E);
        itemTexture("nuclear_icbm", 0x394147, 0xDDBA2D, 0x202427);
        itemTexture("he_rocket", 0x58633D, 0xC39B27, 0x252A2A);
    }

    private static void generateGui() throws IOException {
        BufferedImage image = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x10171B)); g.fillRect(0, 0, 376, 236);
        g.setColor(new Color(0x5B666B)); g.drawRect(0, 0, 375, 235);
        g.setColor(new Color(0x1A252A)); g.fillRect(8, 24, 92, 112);
        g.fillRect(104, 24, 130, 112); g.fillRect(240, 24, 128, 148);
        g.fillRect(100, 142, 174, 88);
        g.setColor(new Color(0x39464B));
        g.drawRect(8,24,92,112);g.drawRect(104,24,130,112);
        g.drawRect(240,24,128,148);g.drawRect(100,142,174,88);
        for (int x = 0; x < 376; x += 12) {
            g.setColor(new Color(0xD19A27)); g.fillRect(x, 230, 6, 4);
            g.setColor(new Color(0x171B1D)); g.fillRect(x + 6, 230, 6, 4);
        }
        g.dispose();
        writePng(TEXTURES.resolve("gui/missile_silo.png"), image);
    }

    private static void generateItemModels() throws IOException {
        writeJson("models/item/conventional_icbm.json", missileModel("war_mod:item/conventional_icbm"));
        writeJson("models/item/nuclear_icbm.json", missileModel("war_mod:item/nuclear_icbm"));
        writeJson("models/item/he_rocket.json", heRocketModel());
        writeJson("models/item/rocket_launcher.json", launcherModel());
        writeJson("models/item/target_designator.json", targetDesignatorModel());
        writeJson("models/item/remote_launch_designator.json", remoteDesignatorModel());
        writeJson("models/item/radar.json", radarModel());
    }

    private static String missileModel(String texture) {
        return modelHeader(texture) + """
          "elements":[
            {"name":"body","from":[6.5,2,6.5],"to":[9.5,13,9.5],"faces":%s},
            {"name":"payload_band","from":[6.35,9,6.35],"to":[9.65,10.5,9.65],"faces":%s},
            {"name":"nose_lower","from":[6.9,13,6.9],"to":[9.1,15,9.1],"faces":%s},
            {"name":"nose_tip","from":[7.5,15,7.5],"to":[8.5,16,8.5],"faces":%s},
            {"name":"nozzle","from":[7,1,7],"to":[9,2,9],"faces":%s},
            {"name":"fin_left","from":[4.8,2.2,7.6],"to":[6.5,6,8.4],"faces":%s},
            {"name":"fin_right","from":[9.5,2.2,7.6],"to":[11.2,6,8.4],"faces":%s},
            {"name":"fin_front","from":[7.6,2.2,4.8],"to":[8.4,6,6.5],"faces":%s},
            {"name":"fin_rear","from":[7.6,2.2,9.5],"to":[8.4,6,11.2],"faces":%s}
          ],%s
        }""".formatted(faces("#all"),faces("#all"),faces("#all"),faces("#all"),faces("#all"),
            faces("#all"),faces("#all"),faces("#all"),faces("#all"),display(0.7));
    }

    private static String launcherModel() {
        return modelHeader("war_mod:item/rocket_launcher") + """
          "elements":[
            {"name":"launch_tube","from":[1,6,5.5],"to":[15,10.5,10.5],"faces":%s},
            {"name":"muzzle_ring","from":[0,5.5,5],"to":[2,11,11],"faces":%s},
            {"name":"rear_vent","from":[14,5.6,5.1],"to":[16,10.9,10.9],"faces":%s},
            {"name":"front_band","from":[4,5.5,5],"to":[5.5,11,11],"faces":%s},
            {"name":"rear_band","from":[11,5.5,5],"to":[12.5,11,11],"faces":%s},
            {"name":"shoulder_rest","from":[10,3.5,6],"to":[15,6,10],"faces":%s},
            {"name":"pistol_grip","from":[8,1,6.5],"to":[10.5,6,9.5],"faces":%s},
            {"name":"front_grip","from":[4.5,2.5,6.8],"to":[6.5,6,9.2],"faces":%s},
            {"name":"sight","from":[6,10.5,6.5],"to":[9,13,9.5],"faces":%s},
            {"name":"selector","from":[9.5,10.4,5],"to":[11,11.5,6],"faces":%s}
          ],%s
        }""".formatted(faces("#all"),faces("#all"),faces("#all"),faces("#all"),faces("#all"),
            faces("#all"),faces("#all"),faces("#all"),faces("#all"),faces("#all"),launcherDisplay());
    }

    private static String targetDesignatorModel() {
        return modelHeader("war_mod:item/target_designator") + """
          "elements":[
            {"name":"solid_body","from":[3,6,4],"to":[13,12,12],"faces":%s},
            {"name":"front_optic","from":[1,7,5.5],"to":[3,11,10.5],"faces":%s},
            {"name":"rear_eyepiece","from":[13,8,6],"to":[15,10.5,10],"faces":%s},
            {"name":"grip","from":[6,1,6],"to":[10,6,10],"faces":%s},
            {"name":"side_screen","from":[6,7,3.5],"to":[11,11,4],"faces":%s},
            {"name":"buttons","from":[4,7,3.4],"to":[5.5,9.5,4],"faces":%s}
          ],%s
        }""".formatted(faces("#all"),faces("#all"),faces("#all"),faces("#all"),faces("#all"),faces("#all"),display(0.85));
    }

    private static String remoteDesignatorModel() {
        return modelHeader("war_mod:item/remote_launch_designator") + """
          "elements":[
            {"name":"controller_body","from":[3,5,4],"to":[13,13,12],"faces":%s},
            {"name":"grip","from":[5,1,6],"to":[9,5,10],"faces":%s},
            {"name":"status_screen","from":[5,8,3.5],"to":[11,12,4],"faces":%s},
            {"name":"guarded_control","from":[10,5.5,3.3],"to":[13,8,4.2],"faces":%s},
            {"name":"antenna","from":[11.5,13,7],"to":[12.5,16,8],"faces":%s},
            {"name":"side_controls","from":[2.5,7,6],"to":[3.2,11,10],"faces":%s}
          ],%s
        }""".formatted(faces("#all"),faces("#all"),faces("#all"),faces("#all"),faces("#all"),faces("#all"),display(0.82));
    }

    private static String radarModel() {
        return modelHeader("war_mod:item/radar") + """
          "elements":[
            {"name":"display_body","from":[2,4,3.5],"to":[14,13,12.5],"faces":%s},
            {"name":"recessed_screen","from":[4,6,3],"to":[12,11.5,3.6],"faces":%s},
            {"name":"side_grip","from":[0.5,5,6],"to":[2.5,12,10],"faces":%s},
            {"name":"antenna","from":[11,13,7],"to":[12,16,8],"faces":%s},
            {"name":"buttons","from":[4,4,3],"to":[9,5.5,3.7],"faces":%s},
            {"name":"screen_hood","from":[3,11.5,2.5],"to":[13,13,4],"faces":%s}
          ],%s
        }""".formatted(faces("#all"),faces("#all"),faces("#all"),faces("#all"),faces("#all"),faces("#all"),display(0.78));
    }

    private static String heRocketModel() {
        return modelHeader("war_mod:item/he_rocket") + """
          "elements":[
            {"name":"body","from":[3,6.5,6.5],"to":[13,9.5,9.5],"faces":%s},
            {"name":"nose","from":[13,7,7],"to":[16,9,9],"faces":%s},
            {"name":"band","from":[8,6.3,6.3],"to":[9.5,9.7,9.7],"faces":%s},
            {"name":"fins","from":[1,4.5,7.4],"to":[4,11.5,8.6],"faces":%s}
          ],%s
        }""".formatted(faces("#all"),faces("#all"),faces("#all"),faces("#all"),display(0.9));
    }

    private static void generateGuidanceModels() throws IOException {
        for (int tier = 1; tier <= 3; tier++) {
            for (String longitudinal : new String[]{"front", "rear"}) {
                for (String vertical : new String[]{"lower", "upper"}) {
                    writeJson("models/block/guidance_tier_" + tier + "_" + longitudinal + "_" + vertical + ".json",
                        guidanceModel(tier, vertical.equals("upper")));
                }
            }
            writeJson("models/item/missile_silo_guidance_support_tier_" + tier + ".json",
                """
                {"parent":"war_mod:block/guidance_tier_%s_front_lower",%s}
                """.formatted(tier, display(0.72)));
        }
        writeJson("blockstates/missile_silo_guidance_support.json", guidanceBlockstate());
    }

    private static String guidanceModel(int tier, boolean upper) {
        float post = 2.0F + tier;
        float arm = tier == 1 ? 5.0F : tier == 2 ? 6.5F : 8.0F;
        String texture = "war_mod:block/guidance_tier_" + tier;
        return modelHeader(texture) + """
          "elements":[
            {"name":"mounting_foot","from":[1,0,1],"to":[%s,3,10],"faces":%s},
            {"name":"upright","from":[2,0,2],"to":[%s,16,%s],"faces":%s},
            {"name":"diagonal_brace","from":[%s,2,3],"to":[%s,14,5],"rotation":{"origin":[4,8,4],"axis":"z","angle":-22.5},"faces":%s},
            {"name":"stabilising_arm","from":[%s,6,5],"to":[16,%s,11],"faces":%s},
            {"name":"clamp","from":[13,%s,4],"to":[16,%s,12],"faces":%s},
            {"name":"servo_housing","from":[2,%s,1],"to":[%s,%s,8],"faces":%s}
          ]
        }""".formatted(5+tier,faces("#all"),2+post,2+post,faces("#all"),
            3+tier,5+tier,faces("#all"),16-arm,8+tier,faces("#all"),
            7+tier,9+tier,faces("#all"),upper?9:3,6+tier,upper?14:7,faces("#all"));
    }

    private static String guidanceBlockstate() {
        StringBuilder json = new StringBuilder("""
            {"multipart":[
            """.strip());
        boolean first = true;
        for (int tier=1;tier<=3;tier++) for (String part:new String[]{"front_lower","front_upper","rear_lower","rear_upper"}) {
            for (String facing:new String[]{"north","east","south","west"}) for (String side:new String[]{"left","right"}) {
                if (!first) json.append(',');
                first=false;
                int baseRotation=switch(facing){case "east"->90;case "south"->180;case "west"->270;default->0;};
                int rotation=(baseRotation+(side.equals("right")?180:0))%360;
                json.append("""
                    {"when":{"tier":"%s","part":"%s","facing":"%s","side":"%s"},"apply":{"model":"war_mod:block/guidance_tier_%s_%s","y":%s}}
                    """.formatted(tier, part, facing, side, tier, part, rotation).strip());
            }
        }
        return json.append("]}").toString();
    }

    private static void generateItemDefinitions() throws IOException {
        for (String name : new String[]{"rocket_launcher","target_designator","remote_launch_designator","radar",
            "conventional_icbm","nuclear_icbm","he_rocket","missile_silo_guidance_support_tier_1",
            "missile_silo_guidance_support_tier_2","missile_silo_guidance_support_tier_3"}) {
            writeJson("items/" + name + ".json",
                """
                {"model":{"type":"minecraft:model","model":"war_mod:item/%s"}}
                """.formatted(name));
        }
    }

    private static String modelHeader(String texture) {
        return """
            {"textures":{"all":"%s"},
            """.formatted(texture);
    }

    private static String faces(String texture) {
        return """
            {"north":{"texture":"%s"},"south":{"texture":"%s"},"east":{"texture":"%s"},"west":{"texture":"%s"},"up":{"texture":"%s"},"down":{"texture":"%s"}}
            """.formatted(texture, texture, texture, texture, texture, texture).strip();
    }

    private static String display(double guiScale) {
        return """
            "display":{"gui":{"rotation":[25,45,0],"scale":[%s,%s,%s]},"ground":{"scale":[0.35,0.35,0.35]},"fixed":{"rotation":[0,180,0],"scale":[0.6,0.6,0.6]},"firstperson_righthand":{"rotation":[0,-90,20],"translation":[1,2,1],"scale":[0.55,0.55,0.55]},"firstperson_lefthand":{"rotation":[0,90,-20],"translation":[-1,2,1],"scale":[0.55,0.55,0.55]},"thirdperson_righthand":{"rotation":[0,-90,35],"translation":[0,2,1],"scale":[0.45,0.45,0.45]},"thirdperson_lefthand":{"rotation":[0,90,-35],"translation":[0,2,1],"scale":[0.45,0.45,0.45]}}
            """.formatted(guiScale, guiScale, guiScale).strip();
    }

    private static String launcherDisplay() {
        return """
            "display":{"gui":{"rotation":[25,135,0],"scale":[0.72,0.72,0.72]},"ground":{"rotation":[0,90,0],"scale":[0.35,0.35,0.35]},"fixed":{"rotation":[0,90,0],"scale":[0.65,0.65,0.65]},"firstperson_righthand":{"rotation":[0,90,-8],"translation":[-1,-1,1],"scale":[0.68,0.68,0.68]},"firstperson_lefthand":{"rotation":[0,-90,8],"translation":[1,-1,1],"scale":[0.68,0.68,0.68]},"thirdperson_righthand":{"rotation":[0,90,-12],"translation":[0,2,1],"scale":[0.62,0.62,0.62]},"thirdperson_lefthand":{"rotation":[0,-90,12],"translation":[0,2,1],"scale":[0.62,0.62,0.62]}}
            """.strip();
    }
    private static void metalTexture(String name, int base, int accent) throws IOException {
        metalTexture(name, base, accent, "block");
    }

    private static void metalTexture(String name, int base, int accent, String folder) throws IOException {
        BufferedImage image = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.setColor(new Color(base));g.fillRect(0,0,16,16);
        g.setColor(new Color(accent));g.drawLine(0,0,15,0);g.drawLine(0,8,15,8);
        g.setColor(new Color(0x20262A));g.drawRect(0,0,15,15);
        g.setColor(new Color(0xAAB2B5));for(int x:new int[]{2,13})for(int y:new int[]{2,13})g.fillRect(x,y,1,1);
        g.dispose();writePng(TEXTURES.resolve(folder + "/" + name + ".png"),image);
    }

    private static void warningTexture(String name,int amber) throws IOException {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();for(int x=-16;x<32;x+=8){g.setColor(new Color(amber));g.fillPolygon(new int[]{x,x+4,x+12,x+8},new int[]{0,0,16,16},4);g.setColor(new Color(0x1B1E20));g.fillPolygon(new int[]{x+4,x+8,x+16,x+12},new int[]{0,0,16,16},4);}g.dispose();
        writePng(TEXTURES.resolve("block/"+name+".png"),image);
    }

    private static void itemTexture(String name,int base,int detail,int edge) throws IOException {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.setColor(new Color(base));g.fillRect(0,0,16,16);
        g.setColor(new Color(edge));g.drawRect(0,0,15,15);g.setColor(new Color(detail));g.fillRect(5,5,6,5);
        g.dispose();writePng(TEXTURES.resolve("item/"+name+".png"),image);
    }

    private static void writeJson(String relative,String json) throws IOException {
        Path path=ASSETS.resolve(relative);Files.createDirectories(path.getParent());
        Files.writeString(path,json.stripTrailing()+System.lineSeparator(),StandardCharsets.UTF_8);
    }

    private static void writePng(Path path,BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent());ImageIO.write(image,"PNG",path.toFile());
    }
}