package com.andye.warmod.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class WarModUiText {
    private static final String ELLIPSIS = "...";
    public static final int BACKGROUND = 0xFF0C1113;
    public static final int SURFACE = 0xFF151D21;
    public static final int SURFACE_RAISED = 0xFF202A2E;
    public static final int BORDER = 0xFF4D5A5E;
    public static final int BORDER_DARK = 0xFF252E31;
    public static final int ACCENT = 0xFFC58B2D;
    public static final int TEXT = 0xFFE2EAED;
    public static final int TEXT_MUTED = 0xFF91A5AC;
    private WarModUiText() { }

    public static void frame(final GuiGraphicsExtractor graphics,
        final int x, final int y, final int width, final int height) {
        graphics.fill(x, y, x + width, y + height, BACKGROUND);
        graphics.fill(x, y, x + width, y + 1, BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
        graphics.fill(x, y, x + 1, y + height, BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 24, SURFACE_RAISED);
        hazardRule(graphics, x + 1, y + 23, width - 2);
        graphics.fill(x + 4, y + 4, x + 6, y + 6, 0xFF8C989A);
        graphics.fill(x + width - 6, y + 4, x + width - 4, y + 6, 0xFF8C989A);
        graphics.fill(x + 4, y + height - 6, x + 6, y + height - 4, 0xFF8C989A);
        graphics.fill(x + width - 6, y + height - 6,
            x + width - 4, y + height - 4, 0xFF8C989A);
    }

    public static void section(final GuiGraphicsExtractor graphics,
        final int x, final int y, final int width, final int height) {
        graphics.fill(x, y, x + width, y + height, SURFACE);
        graphics.fill(x, y, x + width, y + 1, BORDER_DARK);
        graphics.fill(x, y, x + 1, y + height, BORDER_DARK);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF0A0E10);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF0A0E10);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ACCENT);
    }

    private static void hazardRule(final GuiGraphicsExtractor graphics,
        final int x, final int y, final int width) {
        for (int offset = 0; offset < width; offset += 8) {
            int end = Math.min(width, offset + 4);
            graphics.fill(x + offset, y, x + end, y + 2, ACCENT);
            graphics.fill(x + end, y, x + Math.min(width, offset + 8), y + 2,
                0xFF171B1D);
        }
    }

    public static void slot(final GuiGraphicsExtractor graphics,
        final int x, final int y, final boolean hovered, final boolean locked) {
        graphics.fill(x, y, x + 18, y + 18, BORDER);
        graphics.fill(x + 1, y + 1, x + 17, y + 17,
            locked ? 0xFF382326 : BACKGROUND);
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
