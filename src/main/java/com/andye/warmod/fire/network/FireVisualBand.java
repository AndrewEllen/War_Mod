package com.andye.warmod.fire.network;

/**
 * Stable levels in the fire representation hierarchy.  PATCH and HOST keep
 * the source topology; the three aggregate levels use the original known-good
 * fixed grids.  Population and packet pressure may reduce representatives but
 * can never resize a grid or change a surviving cell's identity.
 */
public enum FireVisualBand {
    PATCH(0, 0.0, 80.0, 1, 192),
    HOST(1, 48.0, 192.0, 1, 224),
    LOCAL(2, 160.0, 384.0, 2, 256),
    FAR(3, 320.0, 800.0, 8, 224),
    HORIZON(4, 704.0, 1_536.0, 32, 128);

    /** Source-compatibility aliases for diagnostics and the untouched simulator. */
    public static final FireVisualBand NEAR = PATCH;
    public static final FireVisualBand MID = LOCAL;

    public static final int COMPLETE_MASK = (1 << values().length) - 1;

    private final int wireId;
    private final double minimumDistance;
    private final double maximumDistance;
    private final int preferredCellSize;
    private final int cellBudget;

    FireVisualBand(final int wireId, final double minimumDistance,
        final double maximumDistance, final int preferredCellSize,
        final int cellBudget) {
        this.wireId = wireId;
        this.minimumDistance = minimumDistance;
        this.maximumDistance = maximumDistance;
        this.preferredCellSize = preferredCellSize;
        this.cellBudget = cellBudget;
    }

    public int wireId() { return wireId; }
    public int mask() { return 1 << wireId; }
    public double minimumDistance() { return minimumDistance; }
    public double maximumDistance() { return maximumDistance; }
    public int preferredCellSize() { return preferredCellSize; }
    public int cellBudget() { return cellBudget; }

    public boolean contains(final double distance) {
        return Double.isFinite(distance) && distance >= minimumDistance
            && distance <= maximumDistance;
    }

    /** Complementary overlap weights prevent hard hierarchy boundaries. */
    public float weight(final double distance) {
        if (!Double.isFinite(distance)) return 0.0F;
        return (float) switch (this) {
            case PATCH -> 1.0 - smoothStep(48.0, 80.0, distance);
            case HOST -> smoothStep(48.0, 80.0, distance)
                * (1.0 - smoothStep(160.0, 192.0, distance));
            case LOCAL -> smoothStep(160.0, 192.0, distance)
                * (1.0 - smoothStep(320.0, 384.0, distance));
            case FAR -> smoothStep(320.0, 384.0, distance)
                * (1.0 - smoothStep(704.0, 800.0, distance));
            case HORIZON -> smoothStep(704.0, 800.0, distance)
                * (1.0 - smoothStep(1_472.0, 1_536.0, distance));
        };
    }

    public boolean exactPatch() { return this == PATCH; }

    public FireVisualBand parent() {
        return switch (this) {
            case PATCH -> HOST;
            case HOST -> LOCAL;
            case LOCAL -> FAR;
            case FAR -> HORIZON;
            case HORIZON -> null;
        };
    }

    public static FireVisualBand fromWireId(final int wireId) {
        for (FireVisualBand band : values())
            if (band.wireId == wireId) return band;
        throw new IllegalArgumentException("Unknown fire visual band " + wireId);
    }

    private static double smoothStep(final double edge0, final double edge1,
        final double value) {
        double t = Math.max(0.0, Math.min(1.0,
            (value - edge0) / Math.max(1.0E-6, edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }
}
