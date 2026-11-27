package com.mfxhub.sdk;

import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

/**
 * Why a command ended as it did: the platform's dictionary of reasons plus the three the SDK itself can
 * conclude. A reason the platform adds later arrives as its own text in
 * {@link ExecutionResult#reasonText()} with {@link ExecutionResult#reason()} null, because bucketing a
 * reason the caller has never seen is how a granular vocabulary quietly becomes "failed".
 */
public enum Reason {
    /** It succeeded. */
    SUCCESS("success"),
    /** The broker requoted. */
    REQUOTE("requote"),
    /** The instrument is disabled. */
    INSTRUMENT_DISABLED("instrument_disabled"),
    /** No liquidity. */
    NO_LIQUIDITY("no_liquidity"),
    /** The market is closed. */
    MARKET_CLOSED("market_closed"),
    /** The stops are invalid. */
    INVALID_STOPS("invalid_stops"),
    /** The volume is invalid. */
    INVALID_VOLUME("invalid_volume"),
    /** Not enough money. */
    INSUFFICIENT_FUNDS("insufficient_funds"),
    /** Trading is disabled. */
    TRADE_DISABLED("trade_disabled"),
    /** Rejected for another reason. */
    REJECTED_OTHER("rejected_other"),
    /** Rate limited: nothing was placed, and it is retryable. */
    RATE_LIMITED("rate_limited"),
    /** It expired before it ran. */
    EXPIRED("expired"),
    /** The outcome could not be read: it may be a position. Never retried. */
    UNKNOWN("unknown"),
    /**
     * A planned maintenance window suspended order placement for this terminal. Nothing reached the
     * broker, so it is retryable once the window ends; {@link ExecutionResult#retryAt()} says when.
     */
    MAINTENANCE("maintenance"),
    /** The venue did not answer, or its connection to the account is down: nothing was placed; retryable. */
    VENUE_UNAVAILABLE("venue_unavailable"),
    /** The account's venue has no equivalent for what was asked; not retryable as it stands. */
    NOT_ON_THIS_VENUE("not_on_this_venue"),
    /** Concluded by the SDK, never sent by the platform: the request never got an answer. */
    TRANSPORT_ERROR("transport_error"),
    /** Concluded by the SDK: no answer in time. Never retried: a timed-out order may have been placed. */
    TIMEOUT("timeout"),
    /** Concluded by the SDK: the platform refused the request itself, a 4xx that is not a rate limit. */
    REFUSED("refused");

    private final String wire;

    Reason(String wire) {
        this.wire = wire;
    }

    /**
     * The reason as the platform writes it.
     *
     * @return such as {@code invalid_stops}
     */
    @JsonValue
    public String wire() {
        return wire;
    }

    /**
     * The reason for the platform's text.
     *
     * @param text the text as it arrived
     * @return the reason; null for one this version does not know, or none
     */
    public static @Nullable Reason of(@Nullable String text) {
        if (text == null) {
            return null;
        }
        for (Reason reason : values()) {
            if (reason.wire.equals(text)) {
                return reason;
            }
        }
        return null;
    }
}
