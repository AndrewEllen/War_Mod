package com.andye.warmod.warhead;

/** Why a detached snapshot does or does not contain one chunk section. */
enum WarheadSectionCoverage {
    NOT_CAPTURED((byte)0),
    CAPTURED_PACKED((byte)1),
    PROVEN_ALL_AIR((byte)2),
    PROVEN_IRRELEVANT((byte)3);

    private final byte wireId;

    WarheadSectionCoverage(final byte wireId) {
        this.wireId = wireId;
    }

    byte wireId() { return wireId; }

    static WarheadSectionCoverage fromWireId(final byte id) {
        return switch (id) {
            case 1 -> CAPTURED_PACKED;
            case 2 -> PROVEN_ALL_AIR;
            case 3 -> PROVEN_IRRELEVANT;
            default -> NOT_CAPTURED;
        };
    }
}
