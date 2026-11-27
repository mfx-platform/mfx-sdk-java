package com.mfxhub.sdk;

import com.mfxhub.sdk.models.BulkAction;
import com.mfxhub.sdk.models.CredentialAccepted;
import com.mfxhub.sdk.models.CredentialSubmission;
import com.mfxhub.sdk.models.ExpertAdvisorAttach;
import com.mfxhub.sdk.models.ExpertAdvisorAttachment;
import com.mfxhub.sdk.models.OperationAccepted;
import com.mfxhub.sdk.models.TenantTerminal;
import com.mfxhub.sdk.models.TerminalJournal;
import com.mfxhub.sdk.models.TerminalOperation;
import com.mfxhub.sdk.models.TerminalQueue;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * A tenant's terminals ({@code client.terminals()}): ask for one, submit the login that makes it real,
 * watch it reach ACTIVE, restart, drain, resume and retire it, and attach an expert advisor to it.
 *
 * <p>Two rules show up in every signature here. <b>Every write takes the caller's idempotency key</b>,
 * with no default: a default would be a value someone did not choose, and the point of the key is that
 * the caller can retry with the same one and get the same answer back rather than a second terminal. And
 * <b>a credential is a {@link Secret}</b>, not a {@code String}: it is sent once and never stored, logged
 * or echoed, and printing it shows {@code ***}.
 */
public final class Terminals {
    /** An operation is over in any of these; every other state is on its way. */
    public static final Set<TerminalOperation.StateEnum> OPERATION_FINAL = Set.of(
            TerminalOperation.StateEnum.SUCCEEDED, TerminalOperation.StateEnum.FAILED, TerminalOperation.StateEnum.NOT_STARTED,
            TerminalOperation.StateEnum.EXPIRED, TerminalOperation.StateEnum.CANCELLED, TerminalOperation.StateEnum.SUPERSEDED);
    /** The states a terminal passes through on its way to ACTIVE; every other state is settled. */
    public static final Set<TenantTerminal.StateEnum> ON_ITS_WAY = Set.of(TenantTerminal.StateEnum.REQUESTED,
            TenantTerminal.StateEnum.QUEUED, TenantTerminal.StateEnum.PROVISIONING, TenantTerminal.StateEnum.LOGGING_IN);
    /**
     * OFFLINE needs nothing from you: the platform brings the terminal back by itself when its host
     * reports again, and moves it to another host of its farm when the host is gone or is
     * still down after fifteen minutes. Retrying a request against an OFFLINE terminal is right;
     * re-creating the terminal is not.
     */
    public static final boolean OFFLINE_IS_TEMPORARY = true;
    /** How long the waits wait unless told: fifteen minutes. */
    public static final Duration DEFAULT_WAIT = Duration.ofMinutes(15);

    static final Duration POLL_FROM = Duration.ofSeconds(2);
    static final Duration POLL_CAP = Duration.ofSeconds(15);
    /** A QUEUED terminal is read again at its expected start, but never less often than this. */
    static final Duration QUEUED_CAP = Duration.ofSeconds(60);
    private static final int BULK_REASON_MAX = 500;
    private static final int BULK_IDS_MAX = 1000;

    private final MfxClient client;

    Terminals(MfxClient client) {
        this.client = client;
    }

    /**
     * Can this terminal be used right now: market data, execution, account reads? True only in ACTIVE.
     * Every other state means not yet (REQUESTED, QUEUED, PROVISIONING, LOGGING_IN), not any more
     * (DRAINING, RETIRED), not until it is resumed (DRAINED), or not at the moment (OFFLINE, FAILED).
     * Checking through this means the next state the platform adds cannot quietly read as usable.
     *
     * @param terminal the terminal as read
     * @return whether it is ACTIVE
     */
    public static boolean isUsable(TenantTerminal terminal) {
        return terminal.getState() == TenantTerminal.StateEnum.ACTIVE;
    }

    private static String path(UUID terminalId) {
        return "/terminals/" + Objects.requireNonNull(terminalId, "terminalId");
    }

    /**
     * Asks for a terminal, answered at once in state {@code REQUESTED}, on the farm the platform
     * chooses: your own ACTIVE farms first, then the shared ones, offering the usages this terminal
     * needs, in the server's region, with room. The farm it picked is on the answer.
     *
     * @param brokerId the broker
     * @param serverId the broker's server
     * @param idempotencyKey the caller's key: the same key answers the same terminal, never a second
     * @return the terminal, REQUESTED
     */
    public CompletableFuture<TenantTerminal> create(UUID brokerId, UUID serverId, String idempotencyKey) {
        return create(brokerId, serverId, idempotencyKey, CreateTerminal.defaults());
    }

    /**
     * Asks for a terminal, answered at once in state {@code REQUESTED}. The move to a host is
     * asynchronous, and the credential is submitted separately, so a terminal exists before its login
     * does. {@code instruments} are the broker's own symbol names: MFX never renames an instrument.
     * A farm named in {@code options} is checked rather than trusted: one that is not yours, or cannot
     * take it, is refused; nothing that fits is {@code 409 no_farm}.
     *
     * @param brokerId the broker
     * @param serverId the broker's server
     * @param idempotencyKey the caller's key
     * @param options the farm, usages, instruments, labels and redundancy
     * @return the terminal, REQUESTED
     */
    public CompletableFuture<TenantTerminal> create(UUID brokerId, UUID serverId, String idempotencyKey, CreateTerminal options) {
        Objects.requireNonNull(options, "options");
        Body body = Body.of().put("farm_id", options.farmId())
                .put("broker_id", Objects.requireNonNull(brokerId, "brokerId"))
                .put("server_id", Objects.requireNonNull(serverId, "serverId"))
                .put("streaming", options.streaming())
                .put("execution", options.execution())
                .put("instruments", options.instruments())
                .put("labels", options.labels())
                .put("redundancy", options.redundancy());
        return client.write("POST", "/terminals", body.build(), idempotencyKey, TenantTerminal.class);
    }

    /**
     * One terminal: its state, how long it has been in it, and its observed conditions.
     *
     * @param terminalId the terminal's id
     * @return the terminal; fails with {@link NotFoundException} when there is no such terminal or it
     *     is another tenant's (never a 403)
     */
    public CompletableFuture<TenantTerminal> get(UUID terminalId) {
        return client.get(path(terminalId), TenantTerminal.class);
    }

    /**
     * One page of this tenant's terminals, newest first, retired ones included. Labels and states narrow
     * the list on the platform before the page is cut, so a filtered page is never short for being
     * filtered.
     *
     * @param cursor the {@link Page#nextCursor()} of the page before; the first page when null
     * @param filter the page size, labels and states to list
     * @return the page, with the cursor for the next; no total
     */
    public CompletableFuture<Page<TenantTerminal>> page(@Nullable String cursor, ListTerminals filter) {
        List<Map.Entry<String, String>> query = Objects.requireNonNull(filter, "filter").query(cursor);
        return client.page("/terminals", query, TenantTerminal.class);
    }

    /**
     * The first page of this tenant's terminals, in the platform's default size.
     *
     * @return the page
     */
    public CompletableFuture<Page<TenantTerminal>> page() {
        return page(null, ListTerminals.all());
    }

    /**
     * Every terminal, following the cursor until it ends, narrowed as {@link #page(String, ListTerminals)}
     * is. There is no total and no page number anywhere in the API, so this follows the cursor rather
     * than counting.
     *
     * <p>Each page is asked for when the one before has been read, one request a page. Iterating blocks
     * on each page's request: run it where blocking is fine, such as a virtual thread. A failure is
     * thrown from {@code hasNext()} as the {@link MfxException} it is, not wrapped.
     *
     * @param filter the page size, labels and states to list
     * @return every terminal, page by page; each iteration starts again from the first page
     */
    public Iterable<TenantTerminal> list(ListTerminals filter) {
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> page(cursor, filter));
    }

    /**
     * Every terminal, in pages of the platform's default size.
     *
     * @return every terminal, page by page
     */
    public Iterable<TenantTerminal> list() {
        return list(ListTerminals.all());
    }

    /**
     * The terminal's MetaTrader journal, newest line first, in the platform's default count (100).
     *
     * @param terminalId the terminal
     * @return the journal
     */
    public CompletableFuture<TerminalJournal> journal(UUID terminalId) {
        return client.get(path(terminalId) + "/journal", TerminalJournal.class);
    }

    /**
     * The terminal's MetaTrader journal, newest line first. While the terminal runs the lines are read
     * from its host now ({@code live}); once it has stopped, failed or been retired they are the last
     * lines its host kept ({@code last_words}) for 24 hours, and {@code stopped} says why. A terminal
     * that is not running and holds no last lines is a {@code 409 state_error}; a host that does not
     * answer within 10 s is a {@code 503 upstream_error}.
     *
     * @param terminalId the terminal
     * @param lines 1 to 200
     * @return the journal
     * @throws IllegalArgumentException {@code lines} is outside 1 to 200, refused here rather than as a 400
     */
    public CompletableFuture<TerminalJournal> journal(UUID terminalId, int lines) {
        if (lines < 1 || lines > 200) {
            throw new IllegalArgumentException("lines must be 1 to 200, not " + lines);
        }
        return client.get(path(terminalId) + "/journal", Query.of().add("lines", lines).build(), TerminalJournal.class);
    }

    /**
     * Puts a broker login on a terminal, as a master credential. The only request in MFX that carries a
     * secret: it is sent once and never stored, logged or echoed; the answer is a reference.
     *
     * @param terminalId the terminal
     * @param login the broker login
     * @param password the login's password
     * @param server the broker server's name, as the credential names it
     * @param idempotencyKey the caller's key
     * @return the reference to the stored credential
     */
    public CompletableFuture<CredentialAccepted> submitCredentials(UUID terminalId, long login, Secret password, String server,
            String idempotencyKey) {
        return submitCredentials(terminalId, login, password, server, idempotencyKey, CredentialSubmission.CredTypeEnum.MASTER);
    }

    /**
     * Puts a broker login on a terminal. An {@code investor} credential turns execution off, and
     * {@code execution_disabled} on the answer says so here rather than at the first refused order.
     *
     * @param terminalId the terminal
     * @param login the broker login
     * @param password the login's password
     * @param server the broker server's name
     * @param idempotencyKey the caller's key
     * @param credType master or investor
     * @return the reference to the stored credential
     */
    public CompletableFuture<CredentialAccepted> submitCredentials(UUID terminalId, long login, Secret password, String server,
            String idempotencyKey, CredentialSubmission.CredTypeEnum credType) {
        Body body = Body.of().put("login", login)
                .put("password", Objects.requireNonNull(password, "password").reveal())
                .put("server", Objects.requireNonNull(server, "server"))
                .put("cred_type", Objects.requireNonNull(credType, "credType"));
        return client.write("POST", path(terminalId) + "/credentials", body.build(), idempotencyKey, CredentialAccepted.class);
    }

    /**
     * Restarts a stuck terminal, queued when the platform is busy and refused when positions are open.
     *
     * @param terminalId the terminal
     * @param reason shown to operators and read back by this API: written for a person
     * @param idempotencyKey the caller's key
     * @return the operation to follow ({@link #waitForOperation(UUID, UUID)})
     */
    public CompletableFuture<OperationAccepted> restart(UUID terminalId, String reason, String idempotencyKey) {
        return restart(terminalId, reason, idempotencyKey, Relaunch.defaults());
    }

    /**
     * Restarts a stuck terminal: the terminal is closed and started again on the same host, answered 202 with the
     * operation to follow. A restart drops the broker session for a few seconds, so on an account with
     * positions open it is refused unless {@code confirmOpenPositions} says so. {@code queue} is true
     * unless set: a valid restart refused only because the platform is busy is then answered 202 with the
     * operation {@code QUEUED}; false keeps the refusal, a 409 or 429 with {@code Retry-After}.
     *
     * @param terminalId the terminal
     * @param reason shown to operators
     * @param idempotencyKey the caller's key
     * @param options whether to queue, and whether open positions are confirmed
     * @return the operation to follow
     */
    public CompletableFuture<OperationAccepted> restart(UUID terminalId, String reason, String idempotencyKey, Relaunch options) {
        Body body = Body.of().put("reason", Objects.requireNonNull(reason, "reason"))
                .put("queue", options.queueOr(true))
                .put("confirm_open_positions", options.confirmOpenPositions());
        return client.write("POST", path(terminalId) + "/restart", body.build(), idempotencyKey, OperationAccepted.class);
    }

    /**
     * Replaces the terminal's whole instrument list, in the broker's own names; the terminal keeps the
     * depth symbols still in {@code instruments}.
     *
     * @param terminalId the terminal
     * @param instruments the whole list
     * @param idempotencyKey the caller's key
     * @return the terminal
     */
    public CompletableFuture<TenantTerminal> replaceInstruments(UUID terminalId, List<String> instruments, String idempotencyKey) {
        return instruments(terminalId, instruments, null, idempotencyKey);
    }

    /**
     * Replaces the terminal's whole instrument list, and the symbols of it whose order book it reads and
     * streams on {@link Channel#book}: the whole list, an empty one stopping depth. A farm that offers no
     * depth answers 422 {@code depth_not_offered}; more than the farm allows, or a symbol not in
     * {@code instruments}, is 422 {@code unprocessable}.
     *
     * @param terminalId the terminal
     * @param instruments the whole list
     * @param depthInstruments the symbols of {@code instruments} to read the book of
     * @param idempotencyKey the caller's key
     * @return the terminal
     */
    public CompletableFuture<TenantTerminal> replaceInstruments(UUID terminalId, List<String> instruments, List<String> depthInstruments,
            String idempotencyKey) {
        return instruments(terminalId, instruments, Objects.requireNonNull(depthInstruments, "depthInstruments"), idempotencyKey);
    }

    private CompletableFuture<TenantTerminal> instruments(UUID terminalId, List<String> instruments,
            @Nullable List<String> depthInstruments, String idempotencyKey) {
        Body body = Body.of().put("instruments", List.copyOf(Objects.requireNonNull(instruments, "instruments")))
                .put("depth_instruments", depthInstruments == null ? null : List.copyOf(depthInstruments));
        return client.write("PUT", path(terminalId) + "/instruments", body.build(), idempotencyKey, TenantTerminal.class);
    }

    /**
     * Attaches an approved expert advisor version to this terminal, not queued, refused while positions
     * are open.
     *
     * @param terminalId the terminal
     * @param expertAdvisorId the approved version
     * @param symbol the chart's symbol
     * @param timeframe the chart's timeframe
     * @param reason why, for the audit log
     * @param idempotencyKey the caller's key
     * @return the operation to follow
     */
    public CompletableFuture<OperationAccepted> attachExpertAdvisor(UUID terminalId, UUID expertAdvisorId, String symbol,
            ExpertAdvisorAttach.TimeframeEnum timeframe, String reason, String idempotencyKey) {
        return attachExpertAdvisor(terminalId, expertAdvisorId, symbol, timeframe, reason, idempotencyKey, Relaunch.defaults());
    }

    /**
     * Attaches an approved version to this terminal, on the symbol's chart and timeframe: a governed
     * relaunch, answered 202 with the operation to follow ({@link #waitForOperation(UUID, UUID)}). The
     * expert then runs its trial with trading off; it trades only after a green trial, and on a
     * real-money account only after {@link #confirmExpertAdvisor(UUID, String)}. {@code queue} is false
     * unless set.
     *
     * @param terminalId the terminal
     * @param expertAdvisorId the approved version
     * @param symbol the chart's symbol
     * @param timeframe the chart's timeframe
     * @param reason why, for the audit log
     * @param idempotencyKey the caller's key
     * @param options whether to queue, and whether open positions are confirmed
     * @return the operation to follow
     */
    public CompletableFuture<OperationAccepted> attachExpertAdvisor(UUID terminalId, UUID expertAdvisorId, String symbol,
            ExpertAdvisorAttach.TimeframeEnum timeframe, String reason, String idempotencyKey, Relaunch options) {
        Body body = Body.of().put("expert_advisor_id", Objects.requireNonNull(expertAdvisorId, "expertAdvisorId"))
                .put("symbol", Objects.requireNonNull(symbol, "symbol"))
                .put("timeframe", Objects.requireNonNull(timeframe, "timeframe"))
                .put("reason", Objects.requireNonNull(reason, "reason"))
                .put("queue", options.queueOr(false))
                .put("confirm_open_positions", options.confirmOpenPositions());
        return client.write("PUT", path(terminalId) + "/expert-advisor", body.build(), idempotencyKey, OperationAccepted.class);
    }

    /**
     * Takes the expert off this terminal, not queued, refused while positions are open.
     *
     * @param terminalId the terminal
     * @param reason why, for the audit log
     * @param idempotencyKey the caller's key
     * @return the operation to follow
     */
    public CompletableFuture<OperationAccepted> detachExpertAdvisor(UUID terminalId, String reason, String idempotencyKey) {
        return detachExpertAdvisor(terminalId, reason, idempotencyKey, Relaunch.defaults());
    }

    /**
     * Takes the expert off this terminal: a governed relaunch that rebuilds the folder, answered 202.
     * {@code queue} is false unless set.
     *
     * @param terminalId the terminal
     * @param reason why, for the audit log
     * @param idempotencyKey the caller's key
     * @param options whether to queue, and whether open positions are confirmed
     * @return the operation to follow
     */
    public CompletableFuture<OperationAccepted> detachExpertAdvisor(UUID terminalId, String reason, String idempotencyKey, Relaunch options) {
        Query query = Query.of().add("reason", Objects.requireNonNull(reason, "reason"))
                .add("queue", options.queueOr(false))
                .add("confirm_open_positions", options.confirmOpenPositions());
        return client.write("DELETE", path(terminalId) + "/expert-advisor", query.build(), null, MfxRequest.key(idempotencyKey),
                OperationAccepted.class);
    }

    /**
     * Turns on live trading on a real-money account after a green trial, with the one-time code sent to
     * the account's representative. A wrong, used or expired code is 403 {@code confirmation_refused},
     * and the answer does not say which; five in a row lock it. The API takes no idempotency key here:
     * the code is good once.
     *
     * @param terminalId the terminal
     * @param code the one-time code
     * @return the attachment, trading
     */
    public CompletableFuture<ExpertAdvisorAttachment> confirmExpertAdvisor(UUID terminalId, String code) {
        Body body = Body.of().put("code", Objects.requireNonNull(code, "code"));
        return client.write("POST", path(terminalId) + "/expert-advisor/confirm", List.of(), body.build(), null,
                ExpertAdvisorAttachment.class);
    }

    /**
     * One operation on the terminal (restart, attach, detach, drain): its state, step and timeline.
     *
     * @param terminalId the terminal
     * @param operationId the operation
     * @return the operation
     */
    public CompletableFuture<TerminalOperation> getOperation(UUID terminalId, UUID operationId) {
        return client.get(path(terminalId) + "/operations/" + Objects.requireNonNull(operationId, "operationId"), TerminalOperation.class);
    }

    /**
     * Reads the operation until it is over ({@link #OPERATION_FINAL}), for up to fifteen minutes.
     *
     * @param terminalId the terminal
     * @param operationId the operation
     * @return the last read
     */
    public CompletableFuture<TerminalOperation> waitForOperation(UUID terminalId, UUID operationId) {
        return waitForOperation(terminalId, operationId, DEFAULT_WAIT);
    }

    /**
     * Reads the operation until it is over ({@link #OPERATION_FINAL}), or until {@code timeout} has passed
     * on the client's clock, waiting 2 s, then doubling to 15 s, between reads. Completes with the last
     * read either way: an operation still running when the time runs out is reported as it is. Serves
     * restart, attach, detach and drain alike.
     *
     * @param terminalId the terminal
     * @param operationId the operation
     * @param timeout how long to keep reading
     * @return the last read
     */
    public CompletableFuture<TerminalOperation> waitForOperation(UUID terminalId, UUID operationId, Duration timeout) {
        Instant deadline = client.clock().instant().plus(Objects.requireNonNull(timeout, "timeout"));
        return getOperation(terminalId, operationId).thenCompose(first -> pollOperation(terminalId, operationId, first, POLL_FROM, deadline));
    }

    private CompletableFuture<TerminalOperation> pollOperation(UUID terminalId, UUID operationId, TerminalOperation operation, Duration wait,
            Instant deadline) {
        if (OPERATION_FINAL.contains(operation.getState()) || !client.clock().instant().isBefore(deadline)) {
            return CompletableFuture.completedFuture(operation);
        }
        return client.pause(min(wait, POLL_CAP))
                .thenCompose(done -> getOperation(terminalId, operationId))
                .thenCompose(next -> pollOperation(terminalId, operationId, next, wait.multipliedBy(2), deadline));
    }

    /**
     * Reads the terminal until it is no longer on its way to ACTIVE, for up to fifteen minutes.
     *
     * @param terminalId the terminal
     * @return the last read
     */
    public CompletableFuture<TenantTerminal> waitUntilActive(UUID terminalId) {
        return waitUntilActive(terminalId, DEFAULT_WAIT);
    }

    /**
     * Reads the terminal until it is no longer on its way to ACTIVE ({@link #ON_ITS_WAY}), or until
     * {@code timeout} has been waited. Completes with the last read either way: ACTIVE, a state it settled
     * in instead (FAILED, for one), or still on its way when the time ran out. A QUEUED terminal is read
     * again at its {@code queue.expected_start_at} (between 2 s and a minute away), not on a fixed beat;
     * any other state is read again after 2 s, doubling to 15 s.
     *
     * @param terminalId the terminal
     * @param timeout how long to wait in all; zero reads once
     * @return the last read
     * @throws IllegalArgumentException the timeout is below zero
     */
    public CompletableFuture<TenantTerminal> waitUntilActive(UUID terminalId, Duration timeout) {
        if (Objects.requireNonNull(timeout, "timeout").isNegative()) {
            throw new IllegalArgumentException("timeout is 0 or more");
        }
        return get(terminalId).thenCompose(first -> pollActive(terminalId, first, POLL_FROM, Duration.ZERO, timeout));
    }

    private CompletableFuture<TenantTerminal> pollActive(UUID terminalId, TenantTerminal terminal, Duration backoff, Duration waited,
            Duration timeout) {
        if (!ON_ITS_WAY.contains(terminal.getState()) || waited.compareTo(timeout) >= 0) {
            return CompletableFuture.completedFuture(terminal);
        }
        Duration wait = min(nextWait(terminal, backoff, client.clock().instant()), timeout.minus(waited));
        return client.pause(wait)
                .thenCompose(done -> get(terminalId))
                .thenCompose(next -> pollActive(terminalId, next, backoff.multipliedBy(2), waited.plus(wait), timeout));
    }

    /**
     * How long to wait before the next read of a terminal still on its way to ACTIVE. A QUEUED terminal
     * with an expected start is read again then, but no sooner than 2 s and no later than a minute away;
     * anything else is the backoff, capped at 15 s.
     */
    static Duration nextWait(TenantTerminal terminal, Duration backoff, Instant now) {
        TerminalQueue queue = terminal.getState() == TenantTerminal.StateEnum.QUEUED ? terminal.getQueue() : null;
        OffsetDateTime expected = queue == null ? null : queue.getExpectedStartAt();
        if (expected != null) {
            Duration until = Duration.between(now, expected.toInstant());
            return min(until.compareTo(POLL_FROM) < 0 ? POLL_FROM : until, QUEUED_CAP);
        }
        return min(backoff, POLL_CAP);
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /**
     * Restarts many of your terminals at once: those on a farm, at a broker, or listed (at most 1000),
     * and at least one of the three. Each is judged by the rules of a single restart; a refused one is
     * listed in {@code refused} with its rule's code, and the rest are taken. Without
     * {@code confirmOpenPositions} a terminal with positions open is refused ({@code positions_open}) and
     * the rest go ahead.
     *
     * @param reason 1 to 500 characters, for the audit log
     * @param idempotencyKey the caller's key
     * @param which the farm, broker or terminals, and whether open positions are confirmed
     * @return the action, with its expected finish
     * @throws IllegalArgumentException the reason is blank or too long, nothing is named, or more than
     *     1000 terminals are; checked here so a bad one is ours, not a 422
     */
    public CompletableFuture<BulkAction> bulkRestart(String reason, String idempotencyKey, BulkRestart which) {
        Objects.requireNonNull(which, "which");
        if (reason == null || reason.isBlank() || reason.length() > BULK_REASON_MAX) {
            throw new IllegalArgumentException("reason is 1 to " + BULK_REASON_MAX + " characters");
        }
        if (which.farmId() == null && which.brokerId() == null && which.terminalIds() == null) {
            throw new IllegalArgumentException("a bulk action names farmId, brokerId or terminalIds");
        }
        List<UUID> ids = which.terminalIds();
        if (ids != null && ids.size() > BULK_IDS_MAX) {
            throw new IllegalArgumentException("terminalIds is at most " + BULK_IDS_MAX);
        }
        Body body = Body.of().put("reason", reason).put("farm_id", which.farmId()).put("broker_id", which.brokerId())
                .put("terminal_ids", ids).put("confirm_open_positions", which.confirmOpenPositions());
        return client.write("POST", "/terminals:restart", body.build(), idempotencyKey, BulkAction.class);
    }

    /**
     * A bulk action's progress: counts per state, what was refused, and its expected finish.
     *
     * @param bulkActionId the action
     * @return the action
     */
    public CompletableFuture<BulkAction> getBulkAction(UUID bulkActionId) {
        return client.get("/bulk-actions/" + Objects.requireNonNull(bulkActionId, "bulkActionId"), BulkAction.class);
    }

    /**
     * Cancels a bulk action: its items still QUEUED settle CANCELLED, running ones finish.
     *
     * @param bulkActionId the action
     * @param idempotencyKey the caller's key
     * @return the action
     */
    public CompletableFuture<BulkAction> cancelBulkAction(UUID bulkActionId, String idempotencyKey) {
        return client.write("POST", "/bulk-actions/" + Objects.requireNonNull(bulkActionId, "bulkActionId") + "/cancel", null,
                idempotencyKey, BulkAction.class);
    }

    /**
     * Closes the terminal gracefully and keeps it, with no reason given and open positions not confirmed.
     *
     * @param terminalId the terminal
     * @param idempotencyKey the caller's key
     * @return the terminal, DRAINING
     */
    public CompletableFuture<TenantTerminal> drain(UUID terminalId, String idempotencyKey) {
        return drain(terminalId, idempotencyKey, DrainTerminal.defaults());
    }

    /**
     * Closes the terminal gracefully and keeps it: DRAINED holds no host and is not billed. Answered with
     * the terminal {@code DRAINING}; it is {@code DRAINED} once its host has stopped it. A drained
     * terminal keeps its id, account, credential, instruments, labels and expert-advisor record and runs
     * nothing until {@link #resume(UUID, String)}; it still counts toward the terminal quota. Governed as
     * a restart is; {@code confirmOpenPositions} is required when the account has positions open (409
     * {@code positions_open}).
     *
     * @param terminalId the terminal
     * @param idempotencyKey the caller's key
     * @param options the reason for the audit log, and whether open positions are confirmed
     * @return the terminal, DRAINING
     */
    public CompletableFuture<TenantTerminal> drain(UUID terminalId, String idempotencyKey, DrainTerminal options) {
        Body body = Body.of().put("reason", options.reason()).put("confirm_open_positions", options.confirmOpenPositions());
        return client.write("POST", path(terminalId) + "/drain", body.build(), idempotencyKey, TenantTerminal.class);
    }

    /**
     * Places a DRAINED terminal again: back to {@code REQUESTED}, then placed and logged in as new. What
     * can never work is refused at once; a terminal already on its way back is answered as it is, so a
     * resume sent twice is one resume; a DRAINING one is 409 {@code state_error} until it is DRAINED.
     *
     * @param terminalId the terminal
     * @param idempotencyKey the caller's key
     * @return the terminal
     */
    public CompletableFuture<TenantTerminal> resume(UUID terminalId, String idempotencyKey) {
        return client.write("POST", path(terminalId) + "/resume", null, idempotencyKey, TenantTerminal.class);
    }

    /**
     * Retires a terminal: it drains, then retires.
     *
     * @param terminalId the terminal
     * @param idempotencyKey the caller's key
     * @return the terminal as it is now, DRAINING
     */
    public CompletableFuture<TenantTerminal> retire(UUID terminalId, String idempotencyKey) {
        return client.write("DELETE", path(terminalId), null, idempotencyKey, TenantTerminal.class);
    }
}
