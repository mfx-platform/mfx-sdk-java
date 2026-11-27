package com.mfxhub.sdk;

/** A template field a group's rollover can set on its members ({@link Copy#rolloverGroup}). */
public enum RolloverField implements LowerWire {
    /** The scaling. */
    SCALING,
    /** The volume bounds. */
    BOUNDS,
    /** The copy options. */
    OPTIONS,
    /** The copy limits. */
    RISK_LIMITS,
    /** Whether a stop-out closes the copied volume. */
    CLOSE_ON_STOPOUT
}
