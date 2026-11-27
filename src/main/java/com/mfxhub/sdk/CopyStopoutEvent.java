package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code copy.stopout}: a follower reached a copy limit and is stopped out.
 *
 * @param schemaVersion the event's schema version
 * @param followerId the follower
 * @param leaderId the leader
 * @param accountId the follower account
 * @param venue the follower account's venue: {@code mt5} or {@code ctrader} today
 * @param at when
 * @param stopoutId the stop-out
 * @param limit {@code daily}, {@code monthly} or {@code lifetime}
 * @param used in the follower account's currency
 * @param closed the copied volume closed, when the follower closes on a stop-out
 */
public record CopyStopoutEvent(String schemaVersion, UUID followerId, UUID leaderId, UUID accountId, String venue, OffsetDateTime at, UUID stopoutId, String limit, BigDecimal used, List<CopyClosed> closed) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "copy.stopout";

    /** The record, its collections copied so they cannot be changed. */
    public CopyStopoutEvent {
        closed = List.copyOf(closed);
    }

    @Override
    public String kind() {
        return KIND;
    }

    static CopyStopoutEvent read(EventData d) {
        return new CopyStopoutEvent(d.text("schema_version"), d.uuid("follower_id"), d.uuid("leader_id"), d.uuid("account_id"), d.text("venue"), d.instant("at"), d.uuid("stopout_id"), d.text("limit"), d.decimal("used"), d.parts("closed").stream().map(CopyClosed::read).toList());
    }
}
