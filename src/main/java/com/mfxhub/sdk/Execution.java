package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mfxhub.sdk.models.OrderPlace;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.jspecify.annotations.Nullable;

/**
 * Placing and changing orders ({@code client.execution()} on a terminal; the same commands on a trading
 * account through {@code client.tradingAccounts()}).
 *
 * <p>Every method completes with an {@link ExecutionResult} and <b>never completes exceptionally</b>. A
 * transport fault, a timeout, a rate limit, a refusal and a broker rejection are all outcomes of trying to
 * trade, and a caller that must catch each one to learn whether it has a position will eventually place
 * two. Execution is asynchronous by design: the platform answers 202 with a command id once the command
 * is persisted, and the outcome arrives later on the stream or from {@link #getCommand(UUID)};
 * {@link #waitFor(UUID)} polls until it is final.
 *
 * <p>Three rules show up in every signature. <b>The idempotency key is the caller's</b>: a retry reuses
 * it and gets the original command back with {@code duplicate} true, never a second order. <b>Nothing is
 * retried automatically</b>: not a timeout, not a rate limit, not a 5xx. And <b>a decimal is a
 * {@link BigDecimal}</b>, never a double, written plainly ({@code 1E+3} goes as {@code "1000"}).
 * Malformed arguments (a negative volume, a blank key) are refused before anything is sent, by throwing.
 */
public final class Execution {
    /** The longest comment the terminal keeps whole, in characters (the contract's {@code maxLength}). */
    public static final int COMMENT_MAX = 31;

    /**
     * An order's or a close's comment as sent, refused here when the terminal would cut it: counted in Unicode code points, as
     * the contract's {@code maxLength} counts characters, so an emoji is one, not two {@code char}s.
     */
    static @Nullable String comment(@Nullable String comment) {
        if (comment == null) {
            return null;
        }
        int given = comment.codePointCount(0, comment.length());
        if (given > COMMENT_MAX) {
            throw new IllegalArgumentException("comment is at most " + COMMENT_MAX + " characters; the terminal would cut a longer one ("
                    + given + " given)");
        }
        return comment;
    }

    static final Duration POLL_FROM = Duration.ofMillis(200);
    static final Duration POLL_CAP = Duration.ofSeconds(2);
    /** How long {@link #waitFor(UUID)} waits unless told: 30 s. */
    public static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);

    private final MfxClient client;
    private final String noun;

    Execution(MfxClient client, String noun) {
        this.client = client;
        this.noun = noun;
    }

    /**
     * One decimal as the contract wants it: plain notation, no exponent, never below zero. A double never
     * reaches this: the type takes only a {@link BigDecimal}, exactly as the caller computed it.
     */
    static String decimal(String name, BigDecimal value) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be a non-negative decimal, not " + value.toPlainString());
        }
        return value.toPlainString();
    }

    private String on(UUID id) {
        return "/" + noun + "/" + Objects.requireNonNull(id, noun.equals("terminals") ? "terminalId" : "accountId");
    }

    /**
     * Places a market order.
     *
     * @param terminalId the terminal (or the trading account, on {@code tradingAccounts()})
     * @param symbol the broker's own name ({@code EURUSD.sd})
     * @param side buy or sell
     * @param volume in lots, exactly
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> placeOrder(UUID terminalId, String symbol, OrderPlace.SideEnum side, BigDecimal volume,
            String idempotencyKey) {
        return placeOrder(terminalId, symbol, side, volume, idempotencyKey, PlaceOrder.market());
    }

    /**
     * Places a market or pending order. MFX never renames an instrument. A comment longer than
     * {@link #COMMENT_MAX} characters (Unicode code points, as the contract's {@code maxLength} counts them)
     * throws {@link IllegalArgumentException} here, before anything is sent, rather than being truncated,
     * because the terminal cuts it without saying so. A
     * {@code stop_limit} order has two prices: {@code price} is the stop, and {@code stopLimitPrice} the
     * limit placed when it wakes; the platform's refusal of a wrong combination comes back as the result.
     *
     * @param terminalId the terminal (or the trading account)
     * @param symbol the broker's own name
     * @param side buy or sell
     * @param volume in lots, exactly
     * @param idempotencyKey the caller's key
     * @param options the order type, prices, stops, deviation, expiry, magic number and comment
     * @return the result; never a failed future
     * @throws IllegalArgumentException a decimal below zero, a blank key, or a comment longer than
     *     {@link #COMMENT_MAX} characters
     */
    public CompletableFuture<ExecutionResult> placeOrder(UUID terminalId, String symbol, OrderPlace.SideEnum side, BigDecimal volume,
            String idempotencyKey, PlaceOrder options) {
        Objects.requireNonNull(options, "options");
        String comment = comment(options.comment());
        Body body = Body.of().put("symbol", Objects.requireNonNull(symbol, "symbol"))
                .put("side", Objects.requireNonNull(side, "side"))
                .put("volume", decimal("volume", volume))
                .put("order_type", options.orderType())
                .put("price", options.price() == null ? null : decimal("price", options.price()))
                .put("stop_limit_price", options.stopLimitPrice() == null ? null : decimal("stopLimitPrice", options.stopLimitPrice()))
                .put("sl", options.sl() == null ? null : decimal("sl", options.sl()))
                .put("tp", options.tp() == null ? null : decimal("tp", options.tp()))
                .put("deviation_points", options.deviationPoints())
                .put("expires_at", options.expiresAt())
                .put("magic", options.magic())
                .put("comment", comment);
        return write("POST", on(terminalId) + "/orders", body.build(), idempotencyKey);
    }

    /**
     * Cancels a pending order. First class, not a modify with a special case.
     *
     * @param terminalId the terminal (or the trading account)
     * @param ticket the order's ticket
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> cancelOrder(UUID terminalId, long ticket, String idempotencyKey) {
        return write("POST", on(terminalId) + "/orders/" + ticket + "/cancel", null, idempotencyKey);
    }

    /**
     * Moves the stop loss, the take profit, or both; at least one is required.
     *
     * @param terminalId the terminal (or the trading account)
     * @param ticket the position's ticket
     * @param idempotencyKey the caller's key
     * @param change the new stop loss, take profit, or both
     * @return the result; never a failed future
     * @throws IllegalArgumentException neither is set
     */
    public CompletableFuture<ExecutionResult> modifyPosition(UUID terminalId, long ticket, String idempotencyKey, ModifyPosition change) {
        Objects.requireNonNull(change, "change");
        if (change.sl() == null && change.tp() == null) {
            throw new IllegalArgumentException("modifyPosition needs sl, tp, or both");
        }
        Body body = Body.of().put("sl", change.sl() == null ? null : decimal("sl", change.sl()))
                .put("tp", change.tp() == null ? null : decimal("tp", change.tp()));
        return write("POST", on(terminalId) + "/positions/" + ticket + "/modify", body.build(), idempotencyKey);
    }

    /**
     * Amends a pending order's price, stops or expiry; at least one must change. What is not set keeps
     * the order's present value, because MetaTrader's amend takes the whole order. A level of {@code 0}
     * clears a stop or a take profit. The expiry has three states: not set keeps the order's own,
     * {@link ModifyOrder.Builder#goodTillCancelled()} clears it, and a time sets it. Volume, side, type and
     * symbol cannot be amended: cancel and place again.
     *
     * @param terminalId the terminal (or the trading account)
     * @param ticket the order's ticket
     * @param idempotencyKey the caller's key
     * @param change what changes
     * @return the result; never a failed future
     * @throws IllegalArgumentException nothing changes
     */
    public CompletableFuture<ExecutionResult> modifyOrder(UUID terminalId, long ticket, String idempotencyKey, ModifyOrder change) {
        Objects.requireNonNull(change, "change");
        if (change.price() == null && change.stopLimitPrice() == null && change.sl() == null && change.tp() == null && !change.expiryChanges()) {
            throw new IllegalArgumentException("modifyOrder needs price, stopLimitPrice, sl, tp, or an expiry");
        }
        Body body = Body.of().put("price", change.price() == null ? null : decimal("price", change.price()))
                .put("stop_limit_price", change.stopLimitPrice() == null ? null : decimal("stopLimitPrice", change.stopLimitPrice()))
                .put("sl", change.sl() == null ? null : decimal("sl", change.sl()))
                .put("tp", change.tp() == null ? null : decimal("tp", change.tp()));
        java.util.Map<String, Object> fields = body.build();
        if (change.expiryChanges()) {
            // Good-till-cancelled is an explicit null on the wire, which the body's own writer leaves out.
            fields.put("expires_at", change.expiresAt() == null ? JsonNodeFactory.instance.nullNode() : Times.iso(change.expiresAt()));
        }
        return write("POST", on(terminalId) + "/orders/" + ticket + "/modify", fields, idempotencyKey);
    }

    /**
     * Closes a position in full, at the platform's default deviation.
     *
     * @param terminalId the terminal (or the trading account)
     * @param ticket the position's ticket
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> closePosition(UUID terminalId, long ticket, String idempotencyKey) {
        return closePosition(terminalId, ticket, null, null, idempotencyKey);
    }

    /**
     * Closes a position in full, with a deviation.
     *
     * @param terminalId the terminal (or the trading account)
     * @param ticket the position's ticket
     * @param deviationPoints the price deviation allowed, in points
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> closePosition(UUID terminalId, long ticket, int deviationPoints, String idempotencyKey) {
        return closePosition(terminalId, ticket, Integer.valueOf(deviationPoints), null, idempotencyKey);
    }

    /**
     * Closes a position in full. {@code comment} is written on the closing deal ({@code Deal.comment}), so a
     * caller can tell its own closes apart; without one nothing is sent and the deal takes the position's own
     * comment, and the position keeps its comment either way. A comment longer than {@link #COMMENT_MAX}
     * characters (Unicode code points) throws {@link IllegalArgumentException} here, before anything is sent,
     * as {@link #placeOrder} does.
     *
     * @param terminalId the terminal (or the trading account)
     * @param ticket the position's ticket
     * @param deviationPoints the price deviation allowed, in points; null for the platform's default
     * @param comment the closing deal's comment; null for none
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     * @throws IllegalArgumentException a blank key, or a comment longer than {@link #COMMENT_MAX} characters
     */
    public CompletableFuture<ExecutionResult> closePosition(UUID terminalId, long ticket, @Nullable Integer deviationPoints,
            @Nullable String comment, String idempotencyKey) {
        Body body = Body.of().put("deviation_points", deviationPoints).put("comment", comment(comment));
        return write("POST", on(terminalId) + "/positions/" + ticket + "/close", body.build(), idempotencyKey);
    }

    /**
     * Closes a position against an opposite one in the same symbol (hedging accounts): the smaller of the
     * two disappears, the larger is reduced by its volume, and the spread is paid once. The result's
     * {@code execVolume} is the volume closed; it has no {@code execPrice}. Two tickets that are not open
     * positions of the account, in one symbol, on opposite sides are refused, as a result.
     *
     * @param terminalId the terminal (or the trading account)
     * @param ticket the position being closed
     * @param byTicket the opposite one
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> closePositionBy(UUID terminalId, long ticket, long byTicket, String idempotencyKey) {
        return write("POST", on(terminalId) + "/positions/" + ticket + "/close-by", Body.of().put("by_ticket", byTicket).build(),
                idempotencyKey);
    }

    /**
     * Closes part of a position, at the platform's default deviation.
     *
     * @param terminalId the terminal (or the trading account)
     * @param ticket the position's ticket
     * @param volume the volume to close, inside the symbol's step, exactly
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> closePositionPartial(UUID terminalId, long ticket, BigDecimal volume, String idempotencyKey) {
        return closePositionPartial(terminalId, ticket, volume, null, null, idempotencyKey);
    }

    /**
     * Closes part of a position.
     *
     * @param terminalId the terminal (or the trading account)
     * @param ticket the position's ticket
     * @param volume the volume to close, exactly
     * @param deviationPoints the price deviation allowed, in points
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> closePositionPartial(UUID terminalId, long ticket, BigDecimal volume, int deviationPoints,
            String idempotencyKey) {
        return closePositionPartial(terminalId, ticket, volume, Integer.valueOf(deviationPoints), null, idempotencyKey);
    }

    /**
     * Closes part of a position. {@code comment} is written on the closing deal, as for
     * {@link #closePosition(UUID, long, Integer, String, String)}, and the same {@link #COMMENT_MAX} limit
     * holds, checked before anything is sent; without one nothing is sent.
     *
     * @param terminalId the terminal (or the trading account)
     * @param ticket the position's ticket
     * @param volume the volume to close, exactly
     * @param deviationPoints the price deviation allowed, in points; null for the platform's default
     * @param comment the closing deal's comment; null for none
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     * @throws IllegalArgumentException a decimal below zero, a blank key, or a comment longer than
     *     {@link #COMMENT_MAX} characters
     */
    public CompletableFuture<ExecutionResult> closePositionPartial(UUID terminalId, long ticket, BigDecimal volume,
            @Nullable Integer deviationPoints, @Nullable String comment, String idempotencyKey) {
        Body body = Body.of().put("volume", decimal("volume", volume)).put("deviation_points", deviationPoints)
                .put("comment", comment(comment));
        return write("POST", on(terminalId) + "/positions/" + ticket + "/close-partial", body.build(), idempotencyKey);
    }

    /**
     * Keeps the position's stop loss {@code distance} behind the best price it reaches. The platform moves
     * the broker's stop, only ever tighter, at most once a second and by at least a tenth of the
     * distance. Setting again replaces the distance. Refused, as a result, with {@code invalid_stops} when
     * the distance is inside the symbol's stops level.
     *
     * @param terminalId the terminal (or the trading account)
     * @param ticket the position's ticket
     * @param distance a price difference above zero ({@code 0.0020} on EURUSD is twenty pips)
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> setTrailingStop(UUID terminalId, long ticket, BigDecimal distance, String idempotencyKey) {
        return write("PUT", on(terminalId) + "/positions/" + ticket + "/trailing-stop",
                Body.of().put("distance", decimal("distance", distance)).build(), idempotencyKey);
    }

    /**
     * Removes the position's trailing stop. The stop loss stays where the trail left it.
     *
     * @param terminalId the terminal (or the trading account)
     * @param ticket the position's ticket
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> clearTrailingStop(UUID terminalId, long ticket, String idempotencyKey) {
        return write("DELETE", on(terminalId) + "/positions/" + ticket + "/trailing-stop", null, idempotencyKey);
    }

    /**
     * One command and its result. A command of another tenant's is a 404, as a refused result.
     *
     * @param commandId the command
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> getCommand(UUID commandId) {
        return getCommand(commandId, "");
    }

    /**
     * One command and its result, carrying the key it was sent with. Reading is on the execution boundary
     * too: a caller checking what happened to an order need not catch an exception to learn it cannot tell.
     *
     * @param commandId the command
     * @param idempotencyKey the key it was sent with, carried on the result; empty when not known
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> getCommand(UUID commandId, String idempotencyKey) {
        MfxRequest request = MfxRequest.get("/commands/" + Objects.requireNonNull(commandId, "commandId"));
        return command(request, Objects.requireNonNull(idempotencyKey, "idempotencyKey"), false);
    }

    /**
     * Polls until the command is final, for up to 30 s.
     *
     * @param commandId the command
     * @return the last answer
     */
    public CompletableFuture<ExecutionResult> waitFor(UUID commandId) {
        return waitFor(commandId, DEFAULT_WAIT, "");
    }

    /**
     * Polls until the command is final, or until {@code timeout} has passed on the client's clock, asking
     * after 200 ms and doubling to 2 s. Completes with the last answer either way: a command still
     * EXECUTING when the time runs out is reported as it is. <b>A non-final result is never a licence to
     * re-send</b>: an UNKNOWN command may already be a position.
     *
     * @param commandId the command
     * @param timeout how long to keep asking
     * @param idempotencyKey the key it was sent with, carried on the result; empty when not known
     * @return the last answer
     */
    public CompletableFuture<ExecutionResult> waitFor(UUID commandId, Duration timeout, String idempotencyKey) {
        Instant deadline = client.clock().instant().plus(Objects.requireNonNull(timeout, "timeout"));
        return getCommand(commandId, idempotencyKey).thenCompose(first -> poll(commandId, idempotencyKey, first, POLL_FROM, deadline));
    }

    private CompletableFuture<ExecutionResult> poll(UUID commandId, String key, ExecutionResult result, Duration wait, Instant deadline) {
        if (result.isFinal() || !client.clock().instant().isBefore(deadline)) {
            return CompletableFuture.completedFuture(result);
        }
        Duration capped = wait.compareTo(POLL_CAP) <= 0 ? wait : POLL_CAP;
        return client.pause(capped)
                .thenCompose(done -> getCommand(commandId, key))
                .thenCompose(next -> poll(commandId, key, next, wait.multipliedBy(2), deadline));
    }

    private CompletableFuture<ExecutionResult> write(String method, String path, java.util.@Nullable Map<String, Object> body,
            String idempotencyKey) {
        String key = MfxRequest.key(idempotencyKey);
        return command(MfxRequest.write(method, path, List.of(), body, key), key, true);
    }

    /** Runs one execution call and turns everything it can do into a result. */
    private CompletableFuture<ExecutionResult> command(MfxRequest request, String key, boolean write) {
        long started = System.nanoTime();
        String what = request.method() + " " + request.path();
        CompletableFuture<MfxAnswer> sent;
        try {
            sent = client.send(request);
        } catch (RuntimeException refused) {
            sent = CompletableFuture.failedFuture(refused);
        }
        CompletableFuture<ExecutionResult> first = sent.handle((answer, failure) -> {
            double rtt = elapsed(started);
            if (failure != null) {
                Throwable cause = unwrap(failure);
                if (cause instanceof MfxTimeoutException timeout) {
                    // Never retried, by the SDK or by anyone: the order may have reached the broker.
                    return ExecutionResult.refused(Reason.TIMEOUT, key, rtt, what + ": " + timeout.getMessage(), null, false);
                }
                if (cause instanceof MfxTransportException transport) {
                    return ExecutionResult.refused(Reason.TRANSPORT_ERROR, key, rtt, what + ": " + transport.getMessage(), null,
                            transport.mayHaveReached());
                }
                return ExecutionResult.refused(Reason.REFUSED, key, rtt, what + ": " + cause, null, false);
            }
            MfxApiException refused = ApiErrors.of(answer, what, MfxClient.retryAfter(answer.headers()));
            if (refused instanceof MaintenanceException paused) {
                return ExecutionResult.refused(Reason.MAINTENANCE, key, rtt, paused.toString(), paused.endsAt(), false);
            }
            if (refused instanceof RateLimitException limited) {
                return ExecutionResult.refused(Reason.RATE_LIMITED, key, rtt, limited.toString(), null, false);
            }
            if (refused != null) {
                return ExecutionResult.refused(Reason.REFUSED, key, rtt, refused.toString(), null, false);
            }
            return ExecutionResult.fromCommand(bodyOf(answer), key, rtt);
        });
        return first.thenCompose(answer -> {
            UUID id = answer.commandId();
            if (!(write && answer.duplicate() && answer.isFinal() && id != null)) {
                return CompletableFuture.completedFuture(answer);
            }
            // A repeat of a command that is already final is answered with its state only: read the
            // command once, so the caller gets the original result (price, tickets, reason), not an empty one.
            return getCommand(id, key).thenApply(original -> {
                if (original.commandId() != null && original.state() != null) {
                    return original.asDuplicate(elapsed(started));
                }
                // DECISION: the read failed. Report what the platform did say (the state,
                // ok from it, duplicate) and how to read the rest. Never UNKNOWN: the outcome is known.
                return answer.withDetail(elapsed(started), "the original result could not be read (" + original.detail()
                        + "); getCommand(" + id + ") reads it");
            });
        });
    }

    private static double elapsed(long started) {
        return (System.nanoTime() - started) / 1e6;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** The answer as a JSON object: 202 gives a CommandAccepted, 200 a whole Command; anything else is empty. */
    private static JsonNode bodyOf(MfxAnswer answer) {
        try {
            JsonNode body = MfxJson.MAPPER.readTree(answer.body());
            return body != null && body.isObject() ? body : JsonNodeFactory.instance.objectNode();
        } catch (IOException unreadable) {
            return JsonNodeFactory.instance.objectNode();
        }
    }
}
