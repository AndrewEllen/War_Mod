package com.andye.warmod.radar.client.gui;

import net.minecraft.util.Mth;

public final class RadarMapTransform {
    private static final double MIN_BLOCKS_PER_PIXEL = 0.20;

    private double centerWorldX;
    private double centerWorldZ;
    private double blocksPerPixel = 8.0;
    private double maximumVisibleRadius = 25_000.0;
    private double boundCenterX;
    private double boundCenterZ;
    private double boundRadius = Double.NaN;

    public double screenX(final double worldX, final int left, final int width) {
        return left + width * 0.5 + (worldX - centerWorldX) / blocksPerPixel;
    }

    public double screenY(final double worldZ, final int top, final int height) {
        return top + height * 0.5 + (worldZ - centerWorldZ) / blocksPerPixel;
    }

    public double worldX(final double screenX, final int left, final int width) {
        return centerWorldX + (screenX - left - width * 0.5) * blocksPerPixel;
    }

    public double worldZ(final double screenY, final int top, final int height) {
        return centerWorldZ + (screenY - top - height * 0.5) * blocksPerPixel;
    }

    public void panPixels(final double deltaX, final double deltaY) {
        centerWorldX -= deltaX * blocksPerPixel;
        centerWorldZ -= deltaY * blocksPerPixel;
        clampCenter();
    }

    public void zoomAt(
        final double scroll,
        final double mouseX,
        final double mouseY,
        final int left,
        final int top,
        final int width,
        final int height
    ) {
        double worldX = worldX(mouseX, left, width);
        double worldZ = worldZ(mouseY, top, height);
        blocksPerPixel = Mth.clamp(
            blocksPerPixel * Math.pow(1.18, -scroll),
            MIN_BLOCKS_PER_PIXEL,
            maximumBlocksPerPixel(width, height)
        );
        centerWorldX = worldX - (mouseX - left - width * 0.5) * blocksPerPixel;
        centerWorldZ = worldZ - (mouseY - top - height * 0.5) * blocksPerPixel;
        constrain(width, height);
    }

    public void center(final double x, final double z) {
        centerWorldX = x;
        centerWorldZ = z;
        clampCenter();
    }

    public void fit(
        final double minimumX,
        final double minimumZ,
        final double maximumX,
        final double maximumZ,
        final int width,
        final int height
    ) {
        double spanX = Math.max(16.0, maximumX - minimumX);
        double spanZ = Math.max(16.0, maximumZ - minimumZ);
        center((minimumX + maximumX) * 0.5, (minimumZ + maximumZ) * 0.5);
        blocksPerPixel = Mth.clamp(
            Math.max(spanX / (width * 0.78), spanZ / (height * 0.78)),
            MIN_BLOCKS_PER_PIXEL,
            maximumBlocksPerPixel(width, height)
        );
        constrain(width, height);
    }

    /** Fits an exact bounded square without the global-map margin. */
    public void fitBoundedArea(
        final double centreX,
        final double centreZ,
        final double radius,
        final int width,
        final int height
    ) {
        centerWorldX = centreX;
        centerWorldZ = centreZ;
        blocksPerPixel = Mth.clamp(
            Math.max(radius * 2.0 / Math.max(1, width),
                radius * 2.0 / Math.max(1, height)),
            MIN_BLOCKS_PER_PIXEL,
            maximumBlocksPerPixel(width, height)
        );
        constrain(width, height);
    }

    public void setMaximumVisibleRadius(final double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("Map radius must be positive and finite");
        }
        maximumVisibleRadius = radius;
    }

    public void setBounds(
        final double centreX,
        final double centreZ,
        final double radius
    ) {
        if (!Double.isFinite(centreX)
            || !Double.isFinite(centreZ)
            || !Double.isFinite(radius)
            || radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radar map bounds");
        }
        boundCenterX = centreX;
        boundCenterZ = centreZ;
        boundRadius = radius;
        maximumVisibleRadius = radius;
        clampCenter();
    }

    public void clearBounds() {
        boundRadius = Double.NaN;
    }

    public void constrain(final int width, final int height) {
        blocksPerPixel = Math.min(blocksPerPixel, maximumBlocksPerPixel(width, height));
        clampCenter();

        if (!Double.isFinite(boundRadius)) return;
        double halfX = Math.min(boundRadius, Math.max(0.0, width * blocksPerPixel * 0.5));
        double halfZ = Math.min(boundRadius, Math.max(0.0, height * blocksPerPixel * 0.5));
        double xAllowance = Math.max(0.0, boundRadius - halfX);
        double zAllowance = Math.max(0.0, boundRadius - halfZ);
        centerWorldX = Mth.clamp(
            centerWorldX,
            boundCenterX - xAllowance,
            boundCenterX + xAllowance
        );
        centerWorldZ = Mth.clamp(
            centerWorldZ,
            boundCenterZ - zAllowance,
            boundCenterZ + zAllowance
        );
    }

    private double maximumBlocksPerPixel(final int width, final int height) {
        return Math.max(
            MIN_BLOCKS_PER_PIXEL,
            maximumVisibleRadius * 2.0 / Math.max(1, Math.min(width, height))
        );
    }

    private void clampCenter() {
        if (!Double.isFinite(boundRadius)) return;
        centerWorldX = Mth.clamp(
            centerWorldX,
            boundCenterX - boundRadius,
            boundCenterX + boundRadius
        );
        centerWorldZ = Mth.clamp(
            centerWorldZ,
            boundCenterZ - boundRadius,
            boundCenterZ + boundRadius
        );
    }

    public double centerWorldX() {
        return centerWorldX;
    }

    public double centerWorldZ() {
        return centerWorldZ;
    }

    public double blocksPerPixel() {
        return blocksPerPixel;
    }
}
