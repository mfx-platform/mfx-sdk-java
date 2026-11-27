package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The whole answer to one execution call. Never thrown: always the value the future completes with.
 *
 * <p><b>Execution never fails across this boundary.</b> A transport fault, a timeout, a rate limit and a
 * broker rejection are all results, because a trading loop that has to catch exceptions to find out
 * whether it has a position is a trading loop that will one day place two.
 *
 * <p>Read {@link #state()} first: SUCCEEDED is done; FAILED, EXPIRED and REJECTED did not happen; UNKNOWN
 * may already be a position and must never be sent again under a new key. {@link #isFinal()} says whether
 * the platform can still change the answer; {@link Execution#waitFor} waits until it cannot.
 * {@link #retryable()} is false while a command is UNKNOWN, and it is the flag to act on.
 *
 * @param ok true when the command succeeded
 * @param idempotencyKey the key this call was made with, so a retry can reuse it without bookkeeping
 * @param clientRttMs how long the SDK waited, end to end, in milliseconds; always known
 * @param commandId the platform's id for the command; give it to waitFor or getCommand
 * @param state where the command is; null when the platform said nothing readable
 * @param isFinal true once the platform will not change this answer again
 * @param retryable whether sending the same order again under a NEW key can succeed; false while UNKNOWN
 * @param duplicate the same key with the same body: this is the original command, not a second one
 * @param reason why it ended as it did, for a failure or a refusal; null when the platform gives none,
 *     which is what a success looks like, or for a reason this version does not know
 * @param reasonText the reason exactly as it arrived, a new one included
 * @param mt5Retcode the trading platform's own return code
 * @param retcodeText that code in words
 * @param commentRaw the broker's own comment, verbatim
 * @param terminalState the terminal's state when the command was answered
 * @param tickets the order, deal and position the broker gave this fill
 * @param execPrice the price the broker filled at, exactly
 * @param execVolume the volume filled, in lots, exactly
 * @param brokerLatencyMs time at the broker
 * @param agentLatencyMs time inside the platform's terminal
 * @param attempts how many times the platform sent it to the broker
 * @param reroutedFrom the terminal a failover moved this command from, when one did
 * @param detail what the platform said when it refused the request outright
 * @param retryAt when sending it again can work: a maintenance window's end, where the platform said
 * @param venue the venue that answered ({@code mt5} or {@code ctrader}); null until there is a result
 * @param venueCode the venue's own code for a refusal, verbatim (cTrader); null on a MetaTrader account
 * @param deals every deal the command made, in order (cTrader); empty on a MetaTrader account
 * @param volumeUnits the volume filled in units of the instrument (cTrader); null on a MetaTrader account
 * @param closeByEmulated a close-by the venue has no equivalent for, done as two closes (cTrader)
 * @param protectionNotSet a market order filled but its stop loss or take profit could not be set: the
 *     position is open without them
 */
public record ExecutionResult(
        boolean ok, String idempotencyKey, double clientRttMs, @Nullable UUID commandId, @Nullable CommandState state,
        boolean isFinal, boolean retryable, boolean duplicate, @Nullable Reason reason, @Nullable String reasonText,
        @Nullable Integer mt5Retcode, @Nullable String retcodeText, @Nullable String commentRaw, @Nullable String terminalState,
        Tickets tickets, @Nullable BigDecimal execPrice, @Nullable BigDecimal execVolume, @Nullable Integer brokerLatencyMs,
        @Nullable Integer agentLatencyMs, int attempts, @Nullable String reroutedFrom, @Nullable String detail,
        @Nullable OffsetDateTime retryAt, @Nullable String venue, @Nullable String venueCode, List<VenueDeal> deals,
        @Nullable BigDecimal volumeUnits, boolean closeByEmulated, boolean protectionNotSet) {

    private static final Set<Reason> RETRYABLE_REFUSALS = EnumSet.of(Reason.TRANSPORT_ERROR, Reason.RATE_LIMITED, Reason.MAINTENANCE);

    /** The result, its deals copied so they cannot be changed. */
    public ExecutionResult {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(tickets, "tickets");
        deals = List.copyOf(deals);
    }

    /** Built from a Command or a CommandAccepted body, whichever the call answered with. */
    static ExecutionResult fromCommand(JsonNode body, String idempotencyKey, double clientRttMs) {
        JsonNode raw = body.isObject() ? body : MissingNode.getInstance();
        CommandState state = CommandState.of(Results.raw(raw.get("state")));
        JsonNode answer = raw.get("result");
        JsonNode result = answer != null && answer.isObject() ? answer : MissingNode.getInstance();
        String reasonText = Results.text(result.get("reason"));
        // The result says, once there is one. A CommandAccepted has none: a repeat of a command that
        // already succeeded says so by its state alone.
        boolean ok = (answer != null && answer.isObject()) ? truthy(result.get("ok")) : state == CommandState.SUCCEEDED;
        JsonNode finalField = raw.get("final");
        boolean isFinal = finalField != null ? truthy(finalField) : (state != null && state.isFinal());
        // Never retryable while the outcome is unread: that is how one fill becomes two. A maintenance
        // refusal is, whatever the flag says: the consumer refused it before it ran.
        boolean retryable = (truthy(result.get("retryable")) || Reason.MAINTENANCE.wire().equals(reasonText))
                && state != CommandState.UNKNOWN;
        Integer attempts = Results.integer(result.get("attempts"));
        return new ExecutionResult(ok, idempotencyKey, clientRttMs, Results.uuid(raw.get("command_id")), state, isFinal, retryable,
                truthy(raw.get("duplicate")), Reason.of(reasonText), reasonText, Results.integer(result.get("mt5_retcode")),
                Results.raw(result.get("retcode_text")), Results.raw(result.get("comment_raw")), Results.raw(result.get("terminal_state")),
                Tickets.of(result.get("tickets")), Results.decimal(result.get("exec_price")), Results.decimal(result.get("exec_volume")),
                Results.integer(result.get("broker_latency_ms")), Results.integer(result.get("agent_latency_ms")),
                attempts == null ? 0 : attempts, Results.raw(result.get("rerouted_from")), null, null,
                Results.text(result.get("venue")), Results.text(result.get("venue_code")), VenueDeal.list(result.get("deals")),
                Results.decimal(result.get("volume_units")), isTrue(result.get("close_by_emulated")),
                isTrue(result.get("protection_not_set")));
    }

    /**
     * An answer the SDK concluded itself: no transport, no time, or a refusal. {@code mayHaveReached} is
     * for a transport error after the request was sent: the order may be a position, so the result reads
     * UNKNOWN and is not retryable under a new key. Sending it again under the same key is safe.
     */
    static ExecutionResult refused(Reason reason, String idempotencyKey, double clientRttMs, String detail,
            @Nullable OffsetDateTime retryAt, boolean mayHaveReached) {
        boolean reached = reason == Reason.TRANSPORT_ERROR && mayHaveReached;
        // A timeout is never retryable: the order may have been placed. A transport error before the
        // request left is, a rate limit is, and so is a maintenance window's refusal, once it ends.
        boolean retryable = !reached && RETRYABLE_REFUSALS.contains(reason);
        return new ExecutionResult(false, idempotencyKey, clientRttMs, null, reached ? CommandState.UNKNOWN : null, true, retryable,
                false, reason, reason.wire(), null, null, null, null, Tickets.NONE, null, null, null, null, 0, null, detail, retryAt,
                null, null, List.of(), null, false, false);
    }

    /** This result marked as a duplicate, with the call's own time. */
    ExecutionResult asDuplicate(double rttMs) {
        return new ExecutionResult(ok, idempotencyKey, rttMs, commandId, state, isFinal, retryable, true, reason, reasonText, mt5Retcode,
                retcodeText, commentRaw, terminalState, tickets, execPrice, execVolume, brokerLatencyMs, agentLatencyMs, attempts,
                reroutedFrom, detail, retryAt, venue, venueCode, deals, volumeUnits, closeByEmulated, protectionNotSet);
    }

    /** This result with the call's own time and a detail. */
    ExecutionResult withDetail(double rttMs, String why) {
        return new ExecutionResult(ok, idempotencyKey, rttMs, commandId, state, isFinal, retryable, duplicate, reason, reasonText,
                mt5Retcode, retcodeText, commentRaw, terminalState, tickets, execPrice, execVolume, brokerLatencyMs, agentLatencyMs,
                attempts, reroutedFrom, why, retryAt, venue, venueCode, deals, volumeUnits, closeByEmulated, protectionNotSet);
    }

    /** Whether a JSON field is set and not empty: absent, null, false, 0 and empty are false. */
    private static boolean truthy(@Nullable JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isNumber()) {
            return value.doubleValue() != 0;
        }
        if (value.isTextual()) {
            return !value.textValue().isEmpty();
        }
        return value.size() > 0;
    }

    private static boolean isTrue(@Nullable JsonNode value) {
        return value != null && value.isBoolean() && value.booleanValue();
    }
}
