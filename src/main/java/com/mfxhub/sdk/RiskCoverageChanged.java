package com.mfxhub.sdk;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code risk.coverage_changed}: one kind of an account's coverage changed state.
 *
 * @param schemaVersion the event's schema version
 * @param accountId the account
 * @param coverage {@code monitoring}, {@code pre_trade}, {@code broker_held} or {@code managed_actions} (the contract's {@code kind})
 * @param fromState the contract's {@code from}: {@code healthy}, {@code degraded}, {@code disconnected}, {@code unknown}
 * @param toState the contract's {@code to}
 * @param reason why; null when the platform gave none
 * @param at when
 */
public record RiskCoverageChanged(String schemaVersion, UUID accountId, String coverage, String fromState, String toState, @Nullable String reason, OffsetDateTime at) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "risk.coverage_changed";

    @Override
    public String kind() {
        return KIND;
    }

    static RiskCoverageChanged read(EventData d) {
        return new RiskCoverageChanged(d.text("schema_version"), d.uuid("account_id"), d.text("kind"), d.text("from"), d.text("to"), d.blankNull("reason"), d.instant("at"));
    }
}
