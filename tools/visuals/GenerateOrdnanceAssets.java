import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Generates the authored pixel-art models and textures for artillery ammunition and cannon. */
public final class GenerateOrdnanceAssets {
    private static final String[] YIELDS = { "high_explosive", "high_capacity_he", "conventional", "heavy_conventional", "tactical_nuclear", "strategic_nuclear", "heavy_nuclear" };
    private static final int[] COLORS = { 0xC85B26, 0xD58725, 0x778B55, 0x667077, 0xB58135, 0x9A7036, 0x86626A };
    private GenerateOrdnanceAssets() { }
    public static void main(final String[] args) throws IOException {
        boolean tntOnly = args.length > 0 && "tnt-only".equalsIgnoreCase(args[0]);
        Path assets = Path.of("src/main/resources/assets/war_mod");
        Path itemTextures = assets.resolve("textures/item");
        Path blockTextures = assets.resolve("textures/block");
        Path itemModels = assets.resolve("models/item");
        Path itemDefinitions = assets.resolve("items");
        Path blockModels = assets.resolve("models/block");
        Path blockStates = assets.resolve("blockstates");
        for (int index = 0; index < YIELDS.length; index++) {
            for (String type : tntOnly ? new String[] { "tnt" }
                : new String[] { "missile", "warhead", "tnt" }) for (boolean cluster : new boolean[] { false, true }) {
                String id = YIELDS[index] + (cluster ? "_cluster_" : "_") + type;
                writeTexture(itemTextures.resolve(id + ".png"), type, COLORS[index], cluster, index >= 4);
                if (type.equals("missile")) {
                    write(itemModels.resolve(id + ".json"),
                        "{\"parent\":\"war_mod:item/" + (index >= 4 ? "nuclear_icbm" : "conventional_icbm")
                            + "\",\"textures\":{\"all\":\"war_mod:item/" + id + "\"}}\n");
                    write(itemDefinitions.resolve(id + ".json"), itemDefinition("war_mod:item/" + id));
                } else if (type.equals("warhead")) {
                    write(itemModels.resolve(id + ".json"), shellModel(id));
                    write(itemDefinitions.resolve(id + ".json"), itemDefinition("war_mod:item/" + id));
                } else {
                    write(itemModels.resolve(id + ".json"),
                        "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"war_mod:item/" + id + "\"}}\n");
                    writeTntBlockTextures(blockTextures, id, COLORS[index], cluster, index >= 4);
                    write(blockModels.resolve(id + ".json"),
                        "{\"parent\":\"minecraft:block/cube_bottom_top\",\"textures\":{"
                            + "\"top\":\"war_mod:block/" + id + "_top\","
                            + "\"bottom\":\"war_mod:block/" + id + "_bottom\","
                            + "\"side\":\"war_mod:block/" + id + "_side\"}}\n");
                    write(blockStates.resolve(id + ".json"),
                        "{\"variants\":{\"\":{\"model\":\"war_mod:block/" + id + "\"}}}\n");
                    write(itemDefinitions.resolve(id + ".json"), itemDefinition("war_mod:item/" + id));
                }
            }
        }
        if (!tntOnly) writeCannonAssets(assets);
    }
    private static void writeTexture(final Path path, final String type, final int rgb, final boolean cluster, final boolean nuclear) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Color metal = new Color(54, 59, 62), light = new Color(143, 151, 151), accent = new Color(rgb);
        if (type.equals("missile")) {
            g.setColor(metal); g.fillRect(6, 2, 3, 11); g.fillRect(5, 12, 5, 2); g.setColor(light); g.fillRect(7, 2, 1, 10); g.setColor(accent); g.fillRect(6, 7, 3, 2); if (cluster) g.fillRect(5, 10, 5, 1); if (nuclear) { g.setColor(new Color(236, 205, 59)); g.fillRect(7, 4, 1, 1); }
        } else if (type.equals("warhead")) {
            g.setColor(metal); g.fillRect(5, 6, 6, 7); g.fillRect(6, 4, 4, 2); g.fillRect(7, 3, 2, 1); g.setColor(light); g.fillRect(6, 7, 1, 5); g.setColor(accent); g.fillRect(5, 9, 6, 2); if (cluster) { g.setColor(new Color(235, 193, 51)); g.fillRect(5, 12, 6, 1); } if (nuclear) { g.setColor(new Color(236, 205, 59)); g.fillRect(7, 5, 2, 1); }
        } else {
            g.setColor(new Color(81, 48, 40)); g.fillRect(3, 4, 10, 9); g.setColor(new Color(137, 78, 52)); g.fillRect(4, 5, 8, 7); g.setColor(accent); g.fillRect(5, 8, 6, 2); g.setColor(new Color(36, 34, 31)); g.fillRect(4, 4, 8, 1); if (cluster) { g.setColor(new Color(235, 193, 51)); g.fillRect(3, 11, 10, 1); } if (nuclear) { g.setColor(new Color(236, 205, 59)); g.fillRect(7, 6, 2, 1); }
        }
        g.dispose(); writeImage(path, image);
    }
    private static String itemDefinition(final String model) {
        return "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + model + "\"}}\n";
    }
    private static String shellModel(final String id) {
        String texture = "\"north\":{\"texture\":\"#all\"},\"south\":{\"texture\":\"#all\"},\"east\":{\"texture\":\"#all\"},\"west\":{\"texture\":\"#all\"},\"up\":{\"texture\":\"#all\"},\"down\":{\"texture\":\"#all\"}";
        return "{\"textures\":{\"all\":\"war_mod:item/" + id + "\"},\"elements\":["
            + element(5,2,5,11,12,11,texture) + ","
            + element(6,12,6,10,14,10,texture) + ","
            + element(7,14,7,9,16,9,texture) + ","
            + element(6,1,6,10,2,10,texture)
            + "],\"display\":{\"gui\":{\"rotation\":[25,45,0],\"scale\":[0.72,0.72,0.72]},"
            + "\"ground\":{\"scale\":[0.38,0.38,0.38]},\"fixed\":{\"rotation\":[0,180,0],\"scale\":[0.65,0.65,0.65]},"
            + "\"firstperson_righthand\":{\"rotation\":[0,-90,20],\"translation\":[1,2,1],\"scale\":[0.58,0.58,0.58]},"
            + "\"thirdperson_righthand\":{\"rotation\":[0,-90,35],\"translation\":[0,2,1],\"scale\":[0.48,0.48,0.48]}}}\n";
    }

    private static void writeTntBlockTextures(final Path blocks, final String id,
        final int rgb, final boolean cluster, final boolean nuclear) throws IOException {
        Color accent = new Color(rgb);
        Color face = shade(accent, 0.55F);
        Color faceLight = shade(accent, 0.78F);
        Color faceDark = shade(accent, 0.30F);
        Color metal = new Color(48, 52, 54);
        Color metalDark = new Color(25, 28, 30);
        Color warning = cluster ? new Color(236, 190, 48)
            : nuclear ? new Color(222, 177, 42) : faceLight;

        BufferedImage side = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = side.createGraphics();
        g.setColor(face); g.fillRect(0, 0, 16, 16);
        g.setColor(faceDark); g.fillRect(0, 0, 16, 2); g.fillRect(0, 14, 16, 2);
        g.fillRect(0, 0, 2, 16); g.fillRect(14, 0, 2, 16);
        g.setColor(metal); g.fillRect(2, 3, 12, 2); g.fillRect(2, 11, 12, 2);
        g.setColor(faceLight); g.fillRect(3, 5, 10, 6);
        g.setColor(warning); g.fillRect(3, 7, 10, 2);
        g.setColor(metalDark);
        for (int x = 3; x < 13; x += 4) g.fillRect(x, 7, 2, 2);
        if (cluster) { g.setColor(warning); g.fillRect(4, 5, 2, 2); g.fillRect(10, 9, 2, 2); }
        if (nuclear) {
            g.setColor(new Color(236, 205, 59)); g.fillRect(7, 5, 2, 2);
            g.fillRect(5, 9, 2, 2); g.fillRect(9, 9, 2, 2);
        }
        g.dispose();

        BufferedImage top = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        g = top.createGraphics();
        g.setColor(faceDark); g.fillRect(0, 0, 16, 16);
        g.setColor(face); g.fillRect(2, 2, 12, 12);
        g.setColor(faceLight); g.fillRect(4, 4, 8, 8);
        g.setColor(metal); g.fillRect(6, 3, 4, 10); g.fillRect(3, 6, 10, 4);
        g.setColor(warning); g.fillRect(6, 6, 4, 4);
        g.setColor(metalDark); g.fillRect(7, 7, 2, 2);
        if (cluster) { g.setColor(warning); g.fillRect(2, 2, 3, 2); g.fillRect(11, 12, 3, 2); }
        g.dispose();

        BufferedImage bottom = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        g = bottom.createGraphics();
        g.setColor(metalDark); g.fillRect(0, 0, 16, 16);
        g.setColor(metal); g.fillRect(2, 2, 12, 12);
        g.setColor(faceDark); g.fillRect(4, 4, 8, 8);
        g.setColor(face); g.fillRect(6, 6, 4, 4);
        g.setColor(warning); g.fillRect(7, 7, 2, 2);
        g.dispose();

        writeImage(blocks.resolve(id + "_side.png"), side);
        writeImage(blocks.resolve(id + "_top.png"), top);
        writeImage(blocks.resolve(id + "_bottom.png"), bottom);
    }

    private static Color shade(final Color source, final float factor) {
        return new Color(Math.min(255, Math.round(source.getRed() * factor)),
            Math.min(255, Math.round(source.getGreen() * factor)),
            Math.min(255, Math.round(source.getBlue() * factor)));
    }

    private static void writeCannonAssets(final Path assets) throws IOException {
        Path blocks = assets.resolve("textures/block");
        writeBlockTexture(blocks.resolve("artillery_base.png"), 0x50585A, 0x2D3234);
        writeBlockTexture(blocks.resolve("artillery_barrel.png"), 0x697174, 0x30383A);
        writeBlockTexture(blocks.resolve("artillery_breech.png"), 0x4A5153, 0xA77A2E);
        String faces = "\"north\":{\"texture\":\"#%s\"},\"south\":{\"texture\":\"#%s\"},\"east\":{\"texture\":\"#%s\"},\"west\":{\"texture\":\"#%s\"},\"up\":{\"texture\":\"#%s\"},\"down\":{\"texture\":\"#%s\"}";
        String model = "{\"textures\":{\"base\":\"war_mod:block/artillery_base\",\"barrel\":\"war_mod:block/artillery_barrel\",\"breech\":\"war_mod:block/artillery_breech\"},\"elements\":["
            + element(1,0,1,15,4,15,String.format(faces,"base","base","base","base","base","base")) + ","
            + element(3,4,4,13,7,13,String.format(faces,"breech","breech","breech","breech","breech","breech")) + ","
            + element(5,5,10,11,8,14,String.format(faces,"breech","breech","breech","breech","breech","breech")) + "]}";
        write(assets.resolve("models/block/artillery_cannon.json"), model);
        write(assets.resolve("blockstates/artillery_cannon.json"), "{\"variants\":{\"facing=north\":{\"model\":\"war_mod:block/artillery_cannon\"},\"facing=east\":{\"model\":\"war_mod:block/artillery_cannon\",\"y\":90},\"facing=south\":{\"model\":\"war_mod:block/artillery_cannon\",\"y\":180},\"facing=west\":{\"model\":\"war_mod:block/artillery_cannon\",\"y\":270}}}");
        String inventory = "{\"textures\":{\"base\":\"war_mod:block/artillery_base\",\"barrel\":\"war_mod:block/artillery_barrel\",\"breech\":\"war_mod:block/artillery_breech\"},\"display\":{\"gui\":{\"rotation\":[25,45,0],\"translation\":[0,-2,0],\"scale\":[0.62,0.62,0.62]}},\"elements\":["
            + element(1,0,1,15,4,15,String.format(faces,"base","base","base","base","base","base")) + ","
            + element(3,4,4,13,9,13,String.format(faces,"breech","breech","breech","breech","breech","breech")) + ","
            + element(5,7,10,11,11,14,String.format(faces,"breech","breech","breech","breech","breech","breech")) + ","
            + element(6,8,-15,10,12,8,String.format(faces,"barrel","barrel","barrel","barrel","barrel","barrel")) + "]}";
        write(assets.resolve("models/item/artillery_cannon_inventory.json"), inventory);
        write(assets.resolve("items/artillery_cannon.json"), "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"war_mod:item/artillery_cannon_inventory\"}}");
    }
    private static String element(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final String faces) { return "{\"from\":["+x1+","+y1+","+z1+"],\"to\":["+x2+","+y2+","+z2+"],\"faces\":{"+faces+"}}"; }
    private static void writeBlockTexture(final Path path, final int a, final int b) throws IOException { BufferedImage image = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB); for(int y=0;y<16;y++) for(int x=0;x<16;x++) image.setRGB(x,y,((x+y)%5==0?b:a)|0xFF000000); writeImage(path,image); }
    private static void write(final Path path, final String text) throws IOException { Files.createDirectories(path.getParent()); Files.writeString(path,text); }
    private static void writeImage(final Path path, final BufferedImage image) throws IOException { Files.createDirectories(path.getParent()); ImageIO.write(image,"png",path.toFile()); }
}
