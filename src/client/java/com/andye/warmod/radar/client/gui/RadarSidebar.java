package com.andye.warmod.radar.client.gui;

import com.andye.warmod.client.gui.WarModUiText;
import com.andye.warmod.radar.RadarTrackKind;
import com.andye.warmod.radar.client.ClientRadarState;
import com.andye.warmod.radar.client.ClientRadarTrack;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class RadarSidebar {
    private RadarSidebar() { }
    public static int contentHeight(final ClientRadarState state) {
        int height = 8 + 14 + state.tracks().size() * 14;
        ClientRadarTrack selected = state.selected();
        if (selected == null) return height + 8;
        int detailRows = selected.snapshot().kind() == RadarTrackKind.INTERCEPTOR ? 12 : 9;
        return height + 5 + detailRows * 20 + 8;
    }
    public static void render(final GuiGraphicsExtractor graphics, final Font font, final ClientRadarState state, final double now, final int left, final int top, final int width, final int height, final int scroll) {
        graphics.fill(left, top, left + width, top + height, 0xee10171b); graphics.enableScissor(left, top, left + width, top + height); int y = top + 8 - scroll;
        graphics.text(font, Component.literal("TRACKS"), left + 8, y, 0xffffffff); y += 14;
        for (ClientRadarTrack track : state.tracks()) { boolean selected = track.id().equals(state.selectedTrackId()); if (selected) graphics.fill(left + 5, y - 2, left + width - 5, y + 11, 0xff34444c); int colour = track.snapshot().kind() == RadarTrackKind.INTERCEPTOR ? 0xff67ddec : 0xffd5b06a; String row = track.id().toString().substring(0, 8) + "  " + track.snapshot().phase().label(); graphics.text(font, Component.literal(WarModUiText.ellipsize(font, row, Math.max(0, width - 16))), left + 8, y, selected ? 0xffffffff : colour); y += 14; }
        ClientRadarTrack track = state.selected(); if (track != null) { y += 5; var snapshot = track.snapshot(); Vec3 position = track.position(now), velocity = track.velocity(now), target = track.target(); int available = Math.max(0, width - 16); line(graphics, font, "TRACK", snapshot.trackId().toString().substring(0, 8), left, y, available); y += 20; line(graphics, font, "TRACK TYPE", snapshot.kind() == RadarTrackKind.INTERCEPTOR ? "Interceptor" : snapshot.kind().name(), left, y, available); y += 20; line(graphics, font, "LAUNCHED BY", snapshot.ownerDisplayName(), left, y, available); y += 20; if (snapshot.kind() == RadarTrackKind.INTERCEPTOR) { var plan = snapshot.interceptorPlan().orElseThrow(); line(graphics, font, "VARIANT", plan.variant() == com.andye.warmod.antiair.AntiAirMissileVariant.MK_I ? "Mk I" : "Mk II", left, y, available); y += 20; line(graphics, font, "TARGET", plan.targetRootTrackId().map(id -> id.toString().substring(0, 8)).orElse("NONE"), left, y, available); y += 20; line(graphics, font, "GUIDANCE", "Tier " + plan.guidanceTier(), left, y, available); y += 20; line(graphics, font, "MISS ALLOWANCE", String.format(Locale.ROOT, "%.0f blocks", plan.maximumMissDistance()), left, y, available); y += 20; } else { line(graphics, font, "PAYLOAD", snapshot.strategicPayloadType().map(Enum::name).orElse("--"), left, y, available); y += 20; } line(graphics, font, "STAGE", snapshot.phase().label(), left, y, available); y += 20; line(graphics, font, "POSITION", format(position), left, y, available); y += 20; line(graphics, font, "SPEED", String.format(Locale.ROOT, "%.1f blocks/s", velocity.length() * 20), left, y, available); y += 20; line(graphics, font, "PLANNED POINT", format(target), left, y, available); y += 20; line(graphics, font, "DISTANCE", String.format(Locale.ROOT, "%.0f blocks", position.distanceTo(target)), left, y, available); }
        graphics.disableScissor();
    }
    private static void line(final GuiGraphicsExtractor graphics, final Font font, final String label, final String value, final int x, final int y, final int availableWidth) { graphics.text(font, Component.literal(WarModUiText.ellipsize(font, label, availableWidth)), x + 8, y, 0xff7e99a5); graphics.text(font, Component.literal(WarModUiText.ellipsize(font, value, availableWidth)), x + 8, y + 9, 0xffffffff); }
    private static String format(final Vec3 position) { return String.format(Locale.ROOT, "%.0f / %.0f / %.0f", position.x, position.y, position.z); }
}