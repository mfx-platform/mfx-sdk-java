package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code copy.drift}: the follower's copied positions differ from what the leader's positions and the mandate say; MFX Hub trades nothing to close the gap.
 *
 * @param schemaVersion the event's schema version
 * @param followerId the follower
 * @param leaderId the leader
 * @param accountId the follower account
 * @param venue the follower account's venue: {@code mt5} or {@code ctrader} today
 * @param at when
 * @param driftPct how far the copied exposure is from the expected, as a percentage of the expected
 */
public record CopyDrift(String schemaVersion, UUID followerId, UUID leaderId, UUID accountId, String venue, OffsetDateTime at, BigDecimal driftPct) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "copy.drift";

    @Override
    public String kind() {
        return KIND;
    }

    static CopyDrift read(EventData d) {
        return new CopyDrift(d.text("schema_version"), d.uuid("follower_id"), d.uuid("leader_id"), d.uuid("account_id"), d.text("venue"), d.instant("at"), d.decimal("drift_pct"));
    }
}
