package com.andye.warmod.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class WarModUiText {
    private static final String ELLIPSIS = "...";
    private WarModUiText() { }
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