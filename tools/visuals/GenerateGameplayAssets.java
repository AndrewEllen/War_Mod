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
        metalTexture("missile_silo_missile", 0x40484E, 0x98783C);
        warningTexture("radar_station_warning", 0xC58B24);
        metalTexture("radar_dish_front", 0x737F83, 0xA7B1B4, "entity");
        metalTexture("radar_dish_back", 0x444E53, 0x707C81, "entity");
        metalTexture("radar_receiver", 0x59656A, 0xD18A2E, "entity");
        materialTexture("field_hardware", 0x4C575A, 0xAAB3B5, 0x202729);
        materialTexture("field_grip", 0x242B28, 0x6F786D, 0x121715);
        materialTexture("field_optic", 0x102326, 0x53C9CE, 0x071113);
        materialTexture("field_warning", 0x9B681E, 0xD9A12C, 0x352510);
        materialTexture("rocket_launcher", 0x3C4A38, 0x75816E, 0x1E2820);
        materialTexture("target_designator", 0x30383D, 0x627078, 0x171D20);
        materialTexture("remote_launch_designator", 0x2A3135, 0xB87A28, 0x151A1D);
        materialTexture("radar", 0x2B353A, 0x6DB278, 0x151C1F);
        materialTexture("fire_extinguisher_body", 0xA53A31, 0xE7E1CD, 0x47201D);
        materialTexture("fire_equipment_metal", 0x4C575A, 0xC69134, 0x202729);
        materialTexture("fire_hose_body", 0x303B3C, 0xB67C2A, 0x151D1F);
        materialTexture("pipe_wrench_handle", 0xA95D2C, 0xD28A39, 0x3B2921);
        materialTexture("pipe_wrench_jaw", 0x626E72, 0xB4BEC0, 0x293134);
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
        writeJson("models/item/fire_extinguisher.json", fireExtinguisherModel());
        writeJson("models/item/fire_hose.json", fireHoseModel());
        writeJson("models/item/pipe_wrench.json", pipeWrenchModel());
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
        return """
          {"textures":{"body":"war_mod:item/rocket_launcher","metal":"war_mod:item/field_hardware","grip":"war_mod:item/field_grip","optic":"war_mod:item/field_optic","warning":"war_mod:item/field_warning","particle":"#body"},
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
        }""".formatted(faces("#body"),faces("#metal"),faces("#metal"),faces("#warning"),faces("#warning"),
            faces("#grip"),faces("#grip"),faces("#grip"),faces("#optic"),faces("#warning"),launcherDisplay());
    }

    private static String targetDesignatorModel() {
        return """
          {"textures":{"body":"war_mod:item/target_designator","grip":"war_mod:item/field_grip","optic":"war_mod:item/field_optic","warning":"war_mod:item/field_warning","particle":"#body"},
          "elements":[
            {"name":"solid_body","from":[3,6,4],"to":[13,12,12],"faces":%s},
            {"name":"front_optic","from":[1,7,5.5],"to":[3,11,10.5],"faces":%s},
            {"name":"rear_eyepiece","from":[13,8,6],"to":[15,10.5,10],"faces":%s},
            {"name":"grip","from":[6,1,6],"to":[10,6,10],"faces":%s},
            {"name":"side_screen","from":[6,7,3.5],"to":[11,11,4],"faces":%s},
            {"name":"buttons","from":[4,7,3.4],"to":[5.5,9.5,4],"faces":%s}
          ],%s
        }""".formatted(faces("#body"),faces("#optic"),faces("#grip"),faces("#grip"),faces("#optic"),faces("#warning"),display(0.85));
    }

    private static String remoteDesignatorModel() {
        return """
          {"textures":{"body":"war_mod:item/remote_launch_designator","metal":"war_mod:item/field_hardware","grip":"war_mod:item/field_grip","screen":"war_mod:item/field_optic","warning":"war_mod:item/field_warning","particle":"#body"},
          "elements":[
            {"name":"controller_body","from":[3,5,4],"to":[13,13,12],"faces":%s},
            {"name":"grip","from":[5,1,6],"to":[9,5,10],"faces":%s},
            {"name":"status_screen","from":[5,8,3.5],"to":[11,12,4],"faces":%s},
            {"name":"guarded_control","from":[10,5.5,3.3],"to":[13,8,4.2],"faces":%s},
            {"name":"antenna","from":[11.5,13,7],"to":[12.5,16,8],"faces":%s},
            {"name":"side_controls","from":[2.5,7,6],"to":[3.2,11,10],"faces":%s}
          ],%s
        }""".formatted(faces("#body"),faces("#grip"),faces("#screen"),faces("#warning"),faces("#metal"),faces("#warning"),display(0.82));
    }

    private static String fireExtinguisherModel() {
        return """
            {"textures":{"body":"war_mod:item/fire_extinguisher_body","metal":"war_mod:item/fire_equipment_metal","hose":"war_mod:item/fire_hose_body","particle":"#body"},
              "elements":[
                {"name":"tank","from":[5,1,5],"to":[11,12.5,11],"faces":%s},
                {"name":"tank_shoulder","from":[5.7,12.5,5.7],"to":[10.3,14,10.3],"faces":%s},
                {"name":"valve","from":[7,14,7],"to":[9,15.5,9],"faces":%s},
                {"name":"carry_handle","from":[8.5,14.5,6.2],"to":[12.5,15.5,9.8],"faces":%s},
                {"name":"trigger","from":[8.5,13.4,6.5],"to":[11.8,14.2,9.5],"faces":%s},
                {"name":"hose_vertical","from":[3.8,6,9.7],"to":[5.2,14,11.1],"faces":%s},
                {"name":"hose_nozzle","from":[2,4,9.4],"to":[5,7,11.4],"faces":%s},
                {"name":"label_plate","from":[6,5,4.7],"to":[10,9,5.05],"faces":%s}
              ],%s}
            """.formatted(faces("#body"), faces("#body"), faces("#metal"), faces("#metal"),
                faces("#metal"), faces("#hose"), faces("#hose"), faces("#metal"), display(.82));
    }

    private static String fireHoseModel() {
        return """
            {"textures":{"body":"war_mod:item/fire_hose_body","metal":"war_mod:item/fire_equipment_metal","particle":"#body"},
              "elements":[
                {"name":"nozzle_body","from":[2,6,5.5],"to":[13,10.5,10.5],"faces":%s},
                {"name":"wide_muzzle","from":[0,5.5,5],"to":[3,11,11],"faces":%s},
                {"name":"coupling","from":[11.5,5.4,5.2],"to":[15,10.8,10.8],"faces":%s},
                {"name":"hose_tail","from":[14,6.5,6.2],"to":[16,9.7,9.8],"faces":%s},
                {"name":"pistol_grip","from":[8,1,6.5],"to":[11,6.5,9.5],"faces":%s},
                {"name":"shutoff_lever","from":[6,10.4,6.5],"to":[10,12,9.5],"faces":%s},
                {"name":"front_guard","from":[3.5,5,4.8],"to":[5,11.5,11.2],"faces":%s}
              ],%s}
            """.formatted(faces("#metal"), faces("#metal"), faces("#body"), faces("#body"),
                faces("#body"), faces("#metal"), faces("#body"), launcherDisplay());
    }

    private static String pipeWrenchModel() {
        return """
            {"textures":{"handle":"war_mod:item/pipe_wrench_handle","jaw":"war_mod:item/pipe_wrench_jaw","particle":"#handle"},
              "elements":[
                {"name":"handle","from":[6.7,1,6.5],"to":[9.3,12,9.5],"faces":%s},
                {"name":"wrench_head","from":[4.5,10.5,5.5],"to":[10.8,14,10.5],"faces":%s},
                {"name":"fixed_jaw","from":[3,12.5,5.5],"to":[5.5,16,10.5],"faces":%s},
                {"name":"sliding_jaw","from":[9.8,12,5.5],"to":[12.5,15.2,10.5],"faces":%s},
                {"name":"adjuster","from":[8.8,9.5,5.2],"to":[11,12,10.8],"faces":%s},
                {"name":"lanyard_hole","from":[7.2,0.2,7],"to":[8.8,1.5,9],"faces":%s}
              ],%s}
            """.formatted(faces("#handle"), faces("#handle"), faces("#jaw"), faces("#jaw"),
                faces("#jaw"), faces("#jaw"), display(.86));
    }

    private static String radarModel() {
        return """
          {"textures":{"body":"war_mod:item/radar","metal":"war_mod:item/field_hardware","grip":"war_mod:item/field_grip","screen":"war_mod:item/field_optic","warning":"war_mod:item/field_warning","particle":"#body"},
          "elements":[
            {"name":"display_body","from":[2,4,3.5],"to":[14,13,12.5],"faces":%s},
            {"name":"recessed_screen","from":[4,6,3],"to":[12,11.5,3.6],"faces":%s},
            {"name":"side_grip","from":[0.5,5,6],"to":[2.5,12,10],"faces":%s},
            {"name":"antenna","from":[11,13,7],"to":[12,16,8],"faces":%s},
            {"name":"buttons","from":[4,4,3],"to":[9,5.5,3.7],"faces":%s},
            {"name":"screen_hood","from":[3,11.5,2.5],"to":[13,13,4],"faces":%s}
          ],%s
        }""".formatted(faces("#body"),faces("#screen"),faces("#grip"),faces("#metal"),faces("#warning"),faces("#body"),display(0.78));
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
        return """
          {"textures":{"frame":"war_mod:block/guidance_tier_%s","hydraulic":"war_mod:block/guidance_hydraulic","warning":"war_mod:block/guidance_warning","particle":"#frame"},
          "elements":[
            {"name":"mounting_foot","from":[1,0,1],"to":[%s,3,10],"faces":%s},
            {"name":"upright","from":[2,0,2],"to":[%s,16,%s],"faces":%s},
            {"name":"diagonal_brace","from":[%s,2,3],"to":[%s,14,5],"rotation":{"origin":[4,8,4],"axis":"z","angle":-22.5},"faces":%s},
            {"name":"stabilising_arm","from":[%s,6,5],"to":[16,%s,11],"faces":%s},
            {"name":"clamp","from":[13,%s,4],"to":[16,%s,12],"faces":%s},
            {"name":"servo_housing","from":[2,%s,1],"to":[%s,%s,8],"faces":%s}
          ]
        }""".formatted(tier, 5+tier,faces("#frame"),2+post,2+post,faces("#frame"),
            3+tier,5+tier,faces("#frame"),16-arm,8+tier,faces("#hydraulic"),
            7+tier,9+tier,faces("#warning"),upper?9:3,6+tier,upper?14:7,
            faces("#hydraulic"));
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
            "fire_extinguisher","fire_hose","pipe_wrench",
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
            {"textures":{"all":"%s","particle":"#all"},
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

    private static void materialTexture(String name,int base,int accent,int seam) throws IOException {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.setColor(new Color(base));g.fillRect(0,0,16,16);
        g.setColor(new Color(seam));g.drawRect(0,0,15,15);g.drawLine(0,6,15,6);g.drawLine(0,13,15,13);
        g.setColor(new Color(accent));g.fillRect(2,2,5,2);g.fillRect(10,8,3,3);
        g.setColor(new Color(0xC3C8C4));for(int x:new int[]{1,14})for(int y:new int[]{1,14})g.fillRect(x,y,1,1);
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
