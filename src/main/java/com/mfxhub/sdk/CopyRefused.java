package com.mfxhub.sdk;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code copy.refused}: one follower's copy did not fill: refused, skipped, or a stop-out's close.
 *
 * @param schemaVersion the event's schema version
 * @param followerId the follower
 * @param leaderId the leader
 * @param accountId the follower account
 * @param venue the follower account's venue: {@code mt5} or {@code ctrader} today
 * @param at when
 * @param signalId MFX Hub's id for the signal
 * @param deal the leader's deal
 * @param outcome {@code refused}, {@code skipped} or {@code stopout}
 * @param reason one of {@link Copy#REFUSAL_REASONS}
 * @param venueCode the venue's own code beside {@code broker}
 * @param why the reason in words, for a person
 * @param risk when Risk Management refused the copy ({@code risk_rule}): the rule, the policy or challenge with its version, and the figures as text; null otherwise
 */
public record CopyRefused(String schemaVersion, UUID followerId, UUID leaderId, UUID accountId, String venue, OffsetDateTime at, UUID signalId, CopyDeal deal, String outcome, String reason, @Nullable String venueCode, @Nullable String why, @Nullable RiskRefusal risk) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "copy.refused";

    @Override
    public String kind() {
        return KIND;
    }

    static CopyRefused read(EventData d) {
        return new CopyRefused(d.text("schema_version"), d.uuid("follower_id"), d.uuid("leader_id"), d.uuid("account_id"), d.text("venue"), d.instant("at"), d.uuid("signal_id"), CopyDeal.read(d.part("deal")), d.text("outcome"), d.text("reason"), d.textOrNull("venue_code"), d.textOrNull("why"), CopyEvents.risk(d));
    }
}
