package com.andye.warmod.fire.network;

/**
 * Stable, overlapping distance bands used by the authoritative fire visual
 * representation.  Budgets change the world-cell resolution inside a band;
 * they never decide which occupied region survives.
 */
public enum FireVisualBand {
    NEAR(0, 0.0, 112.0, 1, 320),
    MID(1, 80.0, 288.0, 2, 224),
    FAR(2, 240.0, 704.0, 8, 160),
    HORIZON(3, 640.0, 1_536.0, 32, 64);

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

    /** Complementary overlap weights prevent hard near/mid/far boundaries. */
    public float weight(final double distance) {
        if (!Double.isFinite(distance)) return 0.0F;
        return (float) switch (this) {
            case NEAR -> 1.0 - smoothStep(80.0, 112.0, distance);
            case MID -> smoothStep(80.0, 112.0, distance)
                * (1.0 - smoothStep(240.0, 288.0, distance));
            case FAR -> smoothStep(240.0, 288.0, distance)
                * (1.0 - smoothStep(640.0, 704.0, distance));
            case HORIZON -> smoothStep(640.0, 704.0, distance)
                * (1.0 - smoothStep(1_472.0, 1_536.0, distance));
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
