package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code copy.copied}: one follower's copy filled.
 *
 * @param schemaVersion the event's schema version
 * @param followerId the follower
 * @param leaderId the leader
 * @param accountId the follower account
 * @param venue the follower account's venue: {@code mt5} or {@code ctrader} today
 * @param at when
 * @param signalId MFX Hub's id for the signal; the copy log names it
 * @param commandId the copy's command
 * @param deal the leader's deal
 * @param as what the copy became (the contract's {@code as})
 * @param latencyMs MFX Hub's part, in milliseconds
 * @param slippage the slippage
 * @param stages milliseconds by stage, the ones measured
 */
public record CopyCopied(String schemaVersion, UUID followerId, UUID leaderId, UUID accountId, String venue, OffsetDateTime at, UUID signalId, UUID commandId, CopyDeal deal, CopyAs as, @Nullable Double latencyMs, @Nullable BigDecimal slippage, Map<String, Double> stages) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "copy.copied";

    /** The record, its collections copied so they cannot be changed. */
    public CopyCopied {
        stages = Map.copyOf(stages);
    }

    @Override
    public String kind() {
        return KIND;
    }

    static CopyCopied read(EventData d) {
        return new CopyCopied(d.text("schema_version"), d.uuid("follower_id"), d.uuid("leader_id"), d.uuid("account_id"), d.text("venue"), d.instant("at"), d.uuid("signal_id"), d.uuid("command_id"), CopyDeal.read(d.part("deal")), CopyAs.read(d.part("as")), d.numberOrNull("latency_ms"), d.decimalOrNull("slippage"), d.timings("stages"));
    }
}
