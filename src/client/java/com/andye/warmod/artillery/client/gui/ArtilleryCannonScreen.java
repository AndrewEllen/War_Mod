package com.andye.warmod.artillery.client.gui;

import com.andye.warmod.artillery.ArtilleryConstants;
import com.andye.warmod.artillery.network.ServerboundArtilleryFirePayload;
import com.andye.warmod.artillery.network.ServerboundArtilleryTargetPayload;
import com.andye.warmod.block.entity.ArtilleryCannonBlockEntity;
import com.andye.warmod.menu.ArtilleryCannonMenu;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Radar-style ballistic map centred on the cannon's complete 1,000-block envelope. */
public final class ArtilleryCannonScreen extends AbstractContainerScreen<ArtilleryCannonMenu> {
    private static final int MAP_X = 12;
    private static final int MAP_Y = 30;
    private static final int MAP_SIZE = 150;
    private static final int PANEL_X = 176;
    private static final int PANEL_WIDTH = 148;

    private Button fire;
    private Vec3 selectedTarget;

    public ArtilleryCannonScreen(final ArtilleryCannonMenu menu, final Inventory inventory,
        final Component title) {
        super(menu, inventory, title, 336, 272);
        titleLabelX = -10_000;
        inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();
        ArtilleryCannonBlockEntity cannon = menu.cannon();
        selectedTarget = cannon == null || cannon.target() == null
            ? null : cannon.target().position();
        fire = addRenderableWidget(Button.builder(Component.literal("FIRE ARTILLERY"),
            ignored -> send(new ServerboundArtilleryFirePayload(
                menu.containerId, menu.position())))
            .bounds(leftPos + PANEL_X, topPos + 144, PANEL_WIDTH, 22).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ArtilleryCannonBlockEntity cannon = menu.cannon();
        if (cannon != null && cannon.target() != null) {
            selectedTarget = cannon.target().position();
        }
        fire.active = cannon != null && cannon.cooldown() == 0
            && !cannon.ammunition().isEmpty() && selectedTarget != null;
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        if (event.button() == 0 && insideRange(event.x(), event.y())) {
            Vec3 hovered = mapToWorld(event.x(), event.y());
            selectedTarget = hovered;
            send(new ServerboundArtilleryTargetPayload(menu.containerId, menu.position(),
                hovered.x, hovered.y, hovered.z));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX,
        final int mouseY, final float partialTick) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF10171B);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 24, 0xFF26343B);
        drawMap(graphics, mouseX, mouseY);
        graphics.fill(leftPos + PANEL_X - 6, topPos + 30,
            leftPos + PANEL_X + PANEL_WIDTH + 6, topPos + 174, 0xFF172329);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        ArtilleryCannonBlockEntity cannon = menu.cannon();
        graphics.text(font, Component.literal("ARTILLERY FIRE CONTROL"),
            leftPos + 8, topPos + 8, 0xFFFFC45A);
        graphics.text(font, Component.literal("MAX RANGE 1,000 BLOCKS"),
            leftPos + 18, topPos + 184, 0xFF8FAAB4);
        graphics.text(font, Component.literal("AMMUNITION"),
            leftPos + PANEL_X, topPos + 36, 0xFF9DB4BD);
        if (cannon != null) {
            String ammo = cannon.ammunition().isEmpty()
                ? "EMPTY" : cannon.ammunition().getHoverName().getString();
            graphics.text(font, Component.literal(ammo),
                leftPos + PANEL_X, topPos + 72, 0xFFE6EDF0);
            graphics.text(font, Component.literal("COUNT " + cannon.ammunition().getCount() + " / 16"),
                leftPos + PANEL_X, topPos + 84, 0xFFC7D8DD);
            graphics.text(font, Component.literal(cannon.cooldown() > 0
                    ? "CYCLING " + cannon.cooldown() : "READY"),
                leftPos + PANEL_X, topPos + 104,
                cannon.cooldown() > 0 ? 0xFFFFB65C : 0xFF72D69B);
            if (!cannon.lastError().isBlank()) {
                graphics.text(font, Component.literal(cannon.lastError()),
                    leftPos + PANEL_X, topPos + 124, 0xFFFF7568);
            }
        }

        Vec3 hovered = insideMap(mouseX, mouseY) ? mapToWorld(mouseX, mouseY) : null;
        if (hovered != null) {
            graphics.text(font, Component.literal("HOVER " + coordinates(hovered)),
                leftPos + 18, topPos + 194, insideRange(mouseX, mouseY)
                    ? 0xFFFFFFFF : 0xFFFF7568);
        }
        if (selectedTarget != null) {
            graphics.text(font, Component.literal("TARGET " + coordinates(selectedTarget)),
                leftPos + PANEL_X, topPos + 116, 0xFFFFD879);
        }
        graphics.text(font, Component.literal("Click map to select target"),
            leftPos + 18, topPos + 16 + MAP_Y + MAP_SIZE, 0xFFB7CBD2);
    }

    private void drawMap(final GuiGraphicsExtractor graphics, final int mouseX,
        final int mouseY) {
        int x0 = leftPos + MAP_X;
        int y0 = topPos + MAP_Y;
        int centreX = x0 + MAP_SIZE / 2;
        int centreY = y0 + MAP_SIZE / 2;
        int radius = MAP_SIZE / 2;

        graphics.fill(x0, y0, x0 + MAP_SIZE, y0 + MAP_SIZE, 0xFF071316);
        for (int step = 1; step < 4; step++) {
            int coordinate = step * MAP_SIZE / 4;
            graphics.fill(x0 + coordinate, y0, x0 + coordinate + 1, y0 + MAP_SIZE, 0xFF16383D);
            graphics.fill(x0, y0 + coordinate, x0 + MAP_SIZE, y0 + coordinate + 1, 0xFF16383D);
        }
        graphics.fill(centreX, y0, centreX + 1, y0 + MAP_SIZE, 0xFF2A6268);
        graphics.fill(x0, centreY, x0 + MAP_SIZE, centreY + 1, 0xFF2A6268);

        for (int degree = 0; degree < 360; degree += 2) {
            double angle = Math.toRadians(degree);
            int px = centreX + (int)Math.round(Math.cos(angle) * (radius - 1));
            int py = centreY + (int)Math.round(Math.sin(angle) * (radius - 1));
            graphics.fill(px, py, px + 2, py + 2, 0xFF5BD5C5);
        }
        marker(graphics, centreX, centreY, 0xFFFFC45A);

        if (selectedTarget != null) {
            Point selected = worldToMap(selectedTarget);
            line(graphics, centreX, centreY, selected.x, selected.y, 0xFFE39D42);
            marker(graphics, selected.x, selected.y, 0xFFFFD879);
        }
        if (insideMap(mouseX, mouseY)) {
            marker(graphics, mouseX, mouseY,
                insideRange(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFFF5D55);
        }
    }

    private Vec3 mapToWorld(final double screenX, final double screenY) {
        double centreX = leftPos + MAP_X + MAP_SIZE * 0.5;
        double centreY = topPos + MAP_Y + MAP_SIZE * 0.5;
        double scale = ArtilleryConstants.MAX_RANGE_BLOCKS / (MAP_SIZE * 0.5);
        double worldX = menu.position().getX() + 0.5 + (screenX - centreX) * scale;
        double worldZ = menu.position().getZ() + 0.5 - (screenY - centreY) * scale;
        int blockX = (int)Math.floor(worldX);
        int blockZ = (int)Math.floor(worldZ);
        int worldY = menu.position().getY();
        if (minecraft.level != null) {
            BlockPos probe = new BlockPos(blockX, worldY, blockZ);
            if (minecraft.level.hasChunkAt(probe)) {
                worldY = minecraft.level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
            } else if (selectedTarget != null) {
                worldY = (int)Math.round(selectedTarget.y);
            }
        }
        return new Vec3(Math.floor(worldX) + 0.5, worldY, Math.floor(worldZ) + 0.5);
    }

    private Point worldToMap(final Vec3 world) {
        double scale = (MAP_SIZE * 0.5) / ArtilleryConstants.MAX_RANGE_BLOCKS;
        int x = leftPos + MAP_X + MAP_SIZE / 2
            + (int)Math.round((world.x - menu.position().getX() - 0.5) * scale);
        int y = topPos + MAP_Y + MAP_SIZE / 2
            - (int)Math.round((world.z - menu.position().getZ() - 0.5) * scale);
        return new Point(x, y);
    }

    private boolean insideMap(final double x, final double y) {
        return x >= leftPos + MAP_X && x < leftPos + MAP_X + MAP_SIZE
            && y >= topPos + MAP_Y && y < topPos + MAP_Y + MAP_SIZE;
    }

    private boolean insideRange(final double x, final double y) {
        if (!insideMap(x, y)) return false;
        double dx = x - (leftPos + MAP_X + MAP_SIZE * 0.5);
        double dy = y - (topPos + MAP_Y + MAP_SIZE * 0.5);
        return dx * dx + dy * dy <= MAP_SIZE * MAP_SIZE * 0.25;
    }

    private static void marker(final GuiGraphicsExtractor graphics, final int x,
        final int y, final int colour) {
        graphics.fill(x - 2, y, x + 3, y + 1, colour);
        graphics.fill(x, y - 2, x + 1, y + 3, colour);
    }

    private static void line(final GuiGraphicsExtractor graphics, final int ax,
        final int ay, final int bx, final int by, final int colour) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        if (steps == 0) return;
        for (int step = 0; step <= steps; step += 2) {
            double progress = step / (double)steps;
            int x = (int)Math.round(ax + (bx - ax) * progress);
            int y = (int)Math.round(ay + (by - ay) * progress);
            graphics.fill(x, y, x + 1, y + 1, colour);
        }
    }

    private static String coordinates(final Vec3 value) {
        return String.format(Locale.ROOT, "%.0f, %.0f, %.0f", value.x, value.y, value.z);
    }

    private static void send(
        final net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
    }

    private record Point(int x, int y) { }
}