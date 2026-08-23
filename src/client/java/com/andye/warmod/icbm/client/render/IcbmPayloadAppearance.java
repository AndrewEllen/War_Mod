package com.andye.warmod.icbm.client.render;

import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadYield;

/** Shared paint/marking identity used by silo and in-flight missile meshes. */
public record IcbmPayloadAppearance(int bodyRed, int bodyGreen, int bodyBlue,
    int red, int green, int blue, int stripeCount, boolean cluster) {
    public static final IcbmPayloadAppearance CONVENTIONAL = from(
        WarheadYield.CONVENTIONAL, WarheadDeliveryMode.SINGLE);
    public static final IcbmPayloadAppearance NUCLEAR = from(
        WarheadYield.STRATEGIC_NUCLEAR, WarheadDeliveryMode.SINGLE);

    public static IcbmPayloadAppearance from(final WarheadPayloadType type) {
        return type == WarheadPayloadType.NUCLEAR ? NUCLEAR : CONVENTIONAL;
    }

    public static IcbmPayloadAppearance from(final WarheadYield yield,
        final WarheadDeliveryMode deliveryMode) {
        WarheadYield resolved = yield == null ? WarheadYield.CONVENTIONAL : yield;
        boolean cluster = deliveryMode == WarheadDeliveryMode.CLUSTER_FOUR;
        return switch (resolved) {
            case HIGH_EXPLOSIVE -> new IcbmPayloadAppearance(
                187, 184, 164, 194, 76, 32, 1, cluster);
            case HIGH_CAPACITY_HE -> new IcbmPayloadAppearance(
                192, 188, 166, 210, 128, 36, 1, cluster);
            case CONVENTIONAL -> new IcbmPayloadAppearance(
                190, 190, 170, 111, 132, 75, 2, cluster);
            case HEAVY_CONVENTIONAL -> new IcbmPayloadAppearance(
                174, 177, 164, 83, 99, 77, 2, cluster);
            case TACTICAL_NUCLEAR -> new IcbmPayloadAppearance(
                207, 202, 170, 198, 151, 40, 2, cluster);
            case STRATEGIC_NUCLEAR -> new IcbmPayloadAppearance(
                214, 208, 176, 220, 181, 44, 3, cluster);
            case HEAVY_NUCLEAR -> new IcbmPayloadAppearance(
                204, 196, 171, 139, 83, 72, 4, cluster);
        };
    }
}
