package com.mfxhub.sdk;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code copy.state_changed}: a follower's state changed.
 *
 * @param schemaVersion the event's schema version
 * @param followerId the follower
 * @param leaderId the leader
 * @param accountId the follower account
 * @param venue the follower account's venue: {@code mt5} or {@code ctrader} today
 * @param at when
 * @param fromState the contract's {@code from}: {@code copying}, {@code paused}, {@code stopped_out} or {@code unfollowed}
 * @param toState the contract's {@code to}
 * @param why {@code paused}, {@code resumed}, {@code stopout}, {@code unfollowed}, {@code leader_retired} and others; a word added later reads as written
 */
public record CopyStateChanged(String schemaVersion, UUID followerId, UUID leaderId, UUID accountId, String venue, OffsetDateTime at, String fromState, String toState, String why) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "copy.state_changed";

    @Override
    public String kind() {
        return KIND;
    }

    static CopyStateChanged read(EventData d) {
        return new CopyStateChanged(d.text("schema_version"), d.uuid("follower_id"), d.uuid("leader_id"), d.uuid("account_id"), d.text("venue"), d.instant("at"), d.text("from"), d.text("to"), d.text("why"));
    }
}
