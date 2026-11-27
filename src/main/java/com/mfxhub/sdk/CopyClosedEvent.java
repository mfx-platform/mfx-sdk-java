package com.mfxhub.sdk;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code copy.closed}: a requested close of copied positions stopped being pending. Sent once.
 *
 * @param schemaVersion the event's schema version
 * @param followerId the follower
 * @param leaderId the leader
 * @param accountId the follower account
 * @param venue the follower account's venue: {@code mt5} or {@code ctrader} today
 * @param at when
 * @param closeId the close
 * @param cause {@code unfollowed}, {@code leader_retired} or {@code group_close}
 * @param state {@code done}, {@code incomplete} or {@code superseded}
 * @param closed what it closed
 * @param notClosed what it did not
 */
public record CopyClosedEvent(String schemaVersion, UUID followerId, UUID leaderId, UUID accountId, String venue, OffsetDateTime at, UUID closeId, String cause, String state, List<CopyClosed> closed, List<CopyNotClosed> notClosed) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "copy.closed";

    /** The record, its collections copied so they cannot be changed. */
    public CopyClosedEvent {
        closed = List.copyOf(closed);
        notClosed = List.copyOf(notClosed);
    }

    @Override
    public String kind() {
        return KIND;
    }

    static CopyClosedEvent read(EventData d) {
        return new CopyClosedEvent(d.text("schema_version"), d.uuid("follower_id"), d.uuid("leader_id"), d.uuid("account_id"), d.text("venue"), d.instant("at"), d.uuid("close_id"), d.text("cause"), d.text("state"), d.parts("closed").stream().map(CopyClosed::read).toList(), d.parts("not_closed").stream().map(CopyNotClosed::read).toList());
    }
}
