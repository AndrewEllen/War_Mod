package com.andye.warmod.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class WarModUiText {
    private static final String ELLIPSIS = "...";
    public static final int BACKGROUND = 0xFFC6C6C6;
    public static final int SURFACE = 0xFFB8B8B8;
    public static final int SURFACE_RAISED = 0xFFC6C6C6;
    public static final int BORDER = 0xFFFFFFFF;
    public static final int BORDER_DARK = 0xFF555555;
    public static final int ACCENT = 0xFF303030;
    public static final int TEXT = 0xFF404040;
    public static final int TEXT_MUTED = 0xFF505050;
    public static final int SUCCESS = 0xFF246C24;
    public static final int WARNING = 0xFF805000;
    public static final int ERROR = 0xFF9B2525;
    private WarModUiText() { }

    public static void text(final GuiGraphicsExtractor graphics, final Font font,
        final Component text, final int x, final int y, final int color) {
        graphics.text(font, text, x, y, color, false);
    }

    public static void frame(final GuiGraphicsExtractor graphics,
        final int x, final int y, final int width, final int height) {
        // Vanilla container bevel, drawn only inside our own screens.
        graphics.fill(x, y, x + width, y + height, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, BORDER_DARK);
        graphics.fill(x + 1, y + 1, x + width - 2, y + 3, BORDER);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 2, BORDER);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, BACKGROUND);
    }

    public static void section(final GuiGraphicsExtractor graphics,
        final int x, final int y, final int width, final int height) {
        graphics.fill(x, y, x + width, y + height, SURFACE);
        graphics.fill(x, y, x + width, y + 1, 0xFF8B8B8B);
        graphics.fill(x, y, x + 1, y + height, 0xFF8B8B8B);
        graphics.fill(x + width - 1, y, x + width, y + height, BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
    }

    public static void slot(final GuiGraphicsExtractor graphics,
        final int x, final int y, final boolean hovered, final boolean locked) {
        graphics.fill(x, y, x + 18, y + 18, BORDER);
        graphics.fill(x, y, x + 17, y + 1, 0xFF373737);
        graphics.fill(x, y, x + 1, y + 17, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + 17, y + 17,
            locked ? 0xFFAA8888 : 0xFF8B8B8B);
        if (hovered) graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x55FFFFFF);
    }

    public static String ellipsize(final Font font, final String value, final int maximumWidth) {
        if (value == null || value.isEmpty() || maximumWidth <= 0) return "";
        if (font.width(value) <= maximumWidth) return value;
        if (font.width(ELLIPSIS) > maximumWidth) return "";
        int low = 0, high = value.length(), best = 0;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            String candidate = value.substring(0, middle).stripTrailing() + ELLIPSIS;
            if (font.width(candidate) <= maximumWidth) { best = middle; low = middle + 1; }
            else high = middle - 1;
        }
        return value.substring(0, best).stripTrailing() + ELLIPSIS;
    }
    public static Component ellipsize(final Font font, final Component value, final int maximumWidth) {
        return Component.literal(ellipsize(font, value == null ? "" : value.getString(), maximumWidth));
    }
}
