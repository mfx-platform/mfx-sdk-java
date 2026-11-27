package com.mfxhub.sdk;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code terminal.state_changed}: a terminal moved from one state to another.
 *
 * @param schemaVersion the event's schema version
 * @param terminalId the terminal
 * @param fromState the contract's {@code from}: {@code ACTIVE}, {@code OFFLINE}, {@code RETIRED} and the rest, as text
 * @param toState the contract's {@code to}
 * @param reason why
 * @param transitionId the transition
 * @param ts when
 */
public record TerminalStateChanged(String schemaVersion, UUID terminalId, String fromState, String toState, String reason, UUID transitionId, OffsetDateTime ts) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "terminal.state_changed";

    @Override
    public String kind() {
        return KIND;
    }

    static TerminalStateChanged read(EventData d) {
        return new TerminalStateChanged(d.text("schema_version"), d.uuid("terminal_id"), d.text("from"), d.text("to"), d.text("reason"), d.uuid("transition_id"), d.instant("ts"));
    }
}
