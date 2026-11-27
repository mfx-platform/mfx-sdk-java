package com.mfxhub.sdk;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Where a command is. Final: SUCCEEDED, FAILED, EXPIRED and REJECTED. UNKNOWN is visible and not final:
 * the order may already be a position.
 */
public enum CommandState {
    /** Persisted by the platform. */
    ACCEPTED,
    /** Queued for the terminal. */
    ENQUEUED,
    /** Delivered to the terminal. */
    DELIVERED,
    /** At the broker. */
    EXECUTING,
    /** Done. */
    SUCCEEDED,
    /** Did not happen. */
    FAILED,
    /** Expired before it ran. */
    EXPIRED,
    /** Refused. */
    REJECTED,
    /** The outcome could not be read; reconciliation resolves it. Never re-send under a new key. */
    UNKNOWN;

    private static final Set<CommandState> FINAL = Set.of(SUCCEEDED, FAILED, EXPIRED, REJECTED);

    /**
     * Whether the platform can no longer change this.
     *
     * @return true for SUCCEEDED, FAILED, EXPIRED and REJECTED
     */
    public boolean isFinal() {
        return FINAL.contains(this);
    }

    /**
     * The state for the platform's word.
     *
     * @param text the word
     * @return the state; null for one this version does not know, or none
     */
    public static @Nullable CommandState of(@Nullable String text) {
        if (text == null) {
            return null;
        }
        for (CommandState state : values()) {
            if (state.name().equals(text)) {
                return state;
            }
        }
        return null;
    }
}
