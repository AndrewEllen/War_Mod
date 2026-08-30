package com.andye.warmod.fire.network;

/**
 * Stable, overlapping distance bands used by the authoritative fire visual
 * representation. Each aggregate band has one fixed world grid: population
 * changes can alter card counts, but can never resize the grid or churn IDs.
 */
public enum FireVisualBand {
    NEAR(0, 0.0, 112.0, 1, 320),
    /* The 8x8 occupancy mask makes this an effective four-block visual grid. */
    MID(1, 80.0, 288.0, 32, 320),
    /* Effective occupied subcells remain 16 and 64 blocks respectively. */
    FAR(2, 240.0, 704.0, 128, 160),
    HORIZON(3, 640.0, 1_536.0, 512, 64);

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
