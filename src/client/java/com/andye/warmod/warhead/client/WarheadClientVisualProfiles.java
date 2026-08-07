package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.SplittableRandom;

/** Visual timing profiles; geometry is selected separately for conventional and nuclear yields. */
public final class WarheadClientVisualProfiles {
    private static final WarheadClientVisualProfile CONVENTIONAL = new WarheadClientVisualProfile(
        WarheadPayloadType.CONVENTIONAL,
        720, 0, 0,
        0, 22, 78, 250,
        3, 5, 330, 720,
        52, 44, 20,
        12, 22, 52,
        72, 44, 20,
        96, 56, 28,
        1.0, 1.0, 1.0, 1.0
    );

    private WarheadClientVisualProfiles() { }

    public static WarheadClientVisualProfile get(final WarheadPayloadType type, final long seed) {
        if (type != WarheadPayloadType.NUCLEAR) return CONVENTIONAL;
        SplittableRandom random = new SplittableRandom(seed ^ 0x4E55434C454152L);
        return new WarheadClientVisualProfile(
            type,
            6_400, 0, 30,
            0, 180, 420, 1_500,
            10, 24, 5_600, 6_400,
            random.nextDouble(110, 150), random.nextDouble(90, 125), 44,
            random.nextDouble(26, 38), random.nextDouble(105, 150), random.nextDouble(120, 175),
            180, 110, 48,
            220, 132, 58,
            2.1, 1.18, 2.3, 2.8
        );
    }
}
