package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * {@code risk.period_closed}: one period of a challenge account closed, with its statistics.
 *
 * @param schemaVersion the event's schema version
 * @param challengeId the challenge
 * @param accountId the account
 * @param period the contract's {@code RiskPeriod}, as the platform wrote it: figures as text
 * @param at when
 */
public record RiskPeriodClosed(String schemaVersion, UUID challengeId, UUID accountId, Map<String, JsonNode> period, OffsetDateTime at) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "risk.period_closed";

    /** The record, its collections copied so they cannot be changed. */
    public RiskPeriodClosed {
        period = Map.copyOf(period);
    }

    @Override
    public String kind() {
        return KIND;
    }

    static RiskPeriodClosed read(EventData d) {
        return new RiskPeriodClosed(d.text("schema_version"), d.uuid("challenge_id"), d.uuid("account_id"), d.object("period"), d.instant("at"));
    }
}
