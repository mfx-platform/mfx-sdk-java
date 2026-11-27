package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.mfxhub.sdk.models.CopyClose;
import com.mfxhub.sdk.models.CopyClosePageStats;
import com.mfxhub.sdk.models.CopyExternalClose;
import com.mfxhub.sdk.models.CopyExternalOpen;
import com.mfxhub.sdk.models.CopyFollower;
import com.mfxhub.sdk.models.CopyFollowerCreate;
import com.mfxhub.sdk.models.CopyFollowerPageStats;
import com.mfxhub.sdk.models.CopyFollowerUpdate;
import com.mfxhub.sdk.models.CopyGroup;
import com.mfxhub.sdk.models.CopyGroupClose;
import com.mfxhub.sdk.models.CopyGroupCloseResult;
import com.mfxhub.sdk.models.CopyGroupCreate;
import com.mfxhub.sdk.models.CopyGroupMembers;
import com.mfxhub.sdk.models.CopyGroupMembersResult;
import com.mfxhub.sdk.models.CopyGroupPageStats;
import com.mfxhub.sdk.models.CopyGroupRollover;
import com.mfxhub.sdk.models.CopyGroupRolloverResult;
import com.mfxhub.sdk.models.CopyGroupUpdate;
import com.mfxhub.sdk.models.CopyLeader;
import com.mfxhub.sdk.models.CopyLeaderCreate;
import com.mfxhub.sdk.models.CopyLeaderPageStats;
import com.mfxhub.sdk.models.CopyLeaderRetire;
import com.mfxhub.sdk.models.CopyLeaderSource;
import com.mfxhub.sdk.models.CopyLeaderUpdate;
import com.mfxhub.sdk.models.CopyLimitKind;
import com.mfxhub.sdk.models.CopyLogEntry;
import com.mfxhub.sdk.models.CopyLogPageStats;
import com.mfxhub.sdk.models.CopyPreview;
import com.mfxhub.sdk.models.CopyPreviewRequest;
import com.mfxhub.sdk.models.CopySignalAccepted;
import com.mfxhub.sdk.models.CopySignalEntry;
import com.mfxhub.sdk.models.CopySignalPageStats;
import com.mfxhub.sdk.models.CopyStopout;
import com.mfxhub.sdk.models.CopyStopoutReset;
import com.mfxhub.sdk.models.CopyTenantStopoutPageStats;
import com.mfxhub.sdk.models.CopyUnfollow;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Copy Trading for this key's tenant ({@code client.copy()}; {@code copy:read} and {@code copy:write}):
 * leaders, followers, groups, stop-outs, requested closes, the leaders' signals and the copy log.
 *
 * <p>Every write takes the caller's idempotency key and is never retried. Nested settings are maps in the
 * contract's own names; each body is checked against the contract before anything is sent, so a name the
 * contract does not have, a value it cannot hold, or a double anywhere is an
 * {@link IllegalArgumentException} at the call. Every list page carries its {@code stats} row.
 */
public final class Copy {
    /** Why a copy did not fill: the contract's closed list, in its order. {@code broker} comes with the venue's code. */
    public static final List<String> REFUSAL_REASONS = List.of("mapping", "min_volume", "no_stop", "risk_rule", "plan_limit", "stopped_out",
            "signal_expired", "skip_pending", "close_only", "paused", "account_not_connected", "market_closed", "no_conversion",
            "not_on_this_venue", "venue_unavailable", "broker");
    private static final int MEMBERS_MAX = 500;

    private final MfxClient client;

    Copy(MfxClient client) {
        this.client = client;
    }

    private static String id(UUID value, String name) {
        return Objects.requireNonNull(value, name).toString();
    }

    private <T> CompletableFuture<T> send(String method, String path, String what, Class<?> model, Body body, String idempotencyKey,
            Class<T> type) {
        return client.write(method, path, Contract.body(what, model, body), idempotencyKey, type);
    }

    private <T> CompletableFuture<T> post(String path, String idempotencyKey, Class<T> type) {
        return client.write("POST", path, null, idempotencyKey, type);
    }

    private <T, S> CompletableFuture<CopyPage<T, S>> page(String path, java.util.List<Map.Entry<String, String>> query, Class<T> item,
            Class<S> stats) {
        return client.read(MfxRequest.get(path, query)).thenApply(answer -> {
            Page<T> rows = MfxClient.pageOf(answer, item, "GET " + path);
            JsonNode raw = MfxClient.body(answer, JsonNode.class, "GET " + path).get("stats");
            S read = null;
            if (raw != null && raw.isObject()) {
                try {
                    read = MfxJson.MAPPER.treeToValue(raw, stats);
                } catch (IOException unreadable) {
                    throw new MfxException("GET " + path + " answered stats that are not a " + stats.getSimpleName(), unreadable);
                }
            }
            return new CopyPage<>(rows.items(), rows.nextCursor(), rows.returned(), read);
        });
    }

    /** A decimal that is never below zero, as plain text. */
    private static String size(String name, BigDecimal value) {
        return Execution.decimal(name, value);
    }

    // --- leaders ----------------------------------------------------------------------------------

    /**
     * Makes one of your accounts ({@code account} with an account), or an external signal source
     * ({@code external}), a leader.
     *
     * @param source account or external
     * @param signalLifetimeS how long a signal may still be copied after MFX Hub makes it, 1 to 3600
     *     seconds; a follower that cannot act in time logs {@code signal_expired}
     * @param idempotencyKey the caller's key
     * @return the leader
     */
    public CompletableFuture<CopyLeader> createLeader(CopyLeaderSource source, int signalLifetimeS, String idempotencyKey) {
        return createLeader(source, signalLifetimeS, idempotencyKey, CreateLeader.none());
    }

    /**
     * Makes an account, or an external source, a leader, with its settings. An account leads once: a second
     * live leader on it is a {@link ConflictException} ({@code account_already_leads}); over the plan's
     * leaders is a {@link ConflictException} with code {@code quota_exceeded}.
     *
     * @param source account or external
     * @param signalLifetimeS 1 to 3600 seconds
     * @param idempotencyKey the caller's key
     * @param options the account, filters, copies, limits and who may join
     * @return the leader
     */
    public CompletableFuture<CopyLeader> createLeader(CopyLeaderSource source, int signalLifetimeS, String idempotencyKey,
            CreateLeader options) {
        Body body = Body.of().put("source", Objects.requireNonNull(source, "source")).put("signal_lifetime_s", signalLifetimeS)
                .put("account_id", options.accountId()).put("filters", options.filters()).put("copies", options.copies())
                .put("limits", options.limits()).put("who_may_join", options.whoMayJoin());
        return send("POST", "/copy/leaders", "createLeader", CopyLeaderCreate.class, body, idempotencyKey, CopyLeader.class);
    }

    /**
     * One leader; another tenant's id is a {@link NotFoundException}.
     *
     * @param leaderId the leader
     * @return the leader
     */
    public CompletableFuture<CopyLeader> getLeader(UUID leaderId) {
        return client.get("/copy/leaders/" + id(leaderId, "leaderId"), CopyLeader.class);
    }

    /**
     * One page of your leaders, newest first, with the count of each state in {@code stats}.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the states, source, account and page size
     * @return the page
     */
    public CompletableFuture<CopyPage<CopyLeader, CopyLeaderPageStats>> leaders(@Nullable String cursor, ListCopyLeaders filter) {
        return page("/copy/leaders", Objects.requireNonNull(filter, "filter").query(cursor), CopyLeader.class, CopyLeaderPageStats.class);
    }

    /**
     * Every leader, following the cursor. Iterating blocks on each page.
     *
     * @param filter the states, source, account and page size
     * @return every leader
     */
    public Iterable<CopyLeader> allLeaders(ListCopyLeaders filter) {
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> leaders(cursor, filter).thenApply(CopyPage::page));
    }

    /**
     * Changes what a leader's followers receive, from its next deal; only what is set is sent. A retired
     * leader is a {@link StateException}.
     *
     * @param leaderId the leader
     * @param idempotencyKey the caller's key
     * @param change what changes
     * @return the leader
     */
    public CompletableFuture<CopyLeader> updateLeader(UUID leaderId, String idempotencyKey, UpdateLeader change) {
        Body body = Body.of().put("filters", change.filters()).put("copies", change.copies()).put("signal_lifetime_s", change.signalLifetimeS())
                .put("limits", change.limits()).put("who_may_join", change.whoMayJoin());
        return send("PATCH", "/copy/leaders/" + id(leaderId, "leaderId"), "updateLeader", CopyLeaderUpdate.class, body, idempotencyKey,
                CopyLeader.class);
    }

    /**
     * Stops copying the leader's new entries; its closes and stop changes are still copied.
     *
     * @param leaderId the leader
     * @param idempotencyKey the caller's key
     * @return the leader
     */
    public CompletableFuture<CopyLeader> pauseLeader(UUID leaderId, String idempotencyKey) {
        return post("/copy/leaders/" + id(leaderId, "leaderId") + "/pause", idempotencyKey, CopyLeader.class);
    }

    /**
     * Copies the leader's entries again, from its next deal.
     *
     * @param leaderId the leader
     * @param idempotencyKey the caller's key
     * @return the leader
     */
    public CompletableFuture<CopyLeader> resumeLeader(UUID leaderId, String idempotencyKey) {
        return post("/copy/leaders/" + id(leaderId, "leaderId") + "/resume", idempotencyKey, CopyLeader.class);
    }

    /**
     * Retires a leader: every follow of it ends. With {@code closeCopied} each follower's volume copied from
     * it is closed (a requested close each, under one {@code batch_id}), never the account's own positions;
     * without it the copied positions stay open. {@code closeCopied} has no default. Retired is final.
     *
     * @param leaderId the leader
     * @param closeCopied whether to close what was copied from it
     * @param reason why
     * @param idempotencyKey the caller's key
     * @return the leader, retired
     */
    public CompletableFuture<CopyLeader> retireLeader(UUID leaderId, boolean closeCopied, String reason, String idempotencyKey) {
        Body body = Body.of().put("close_copied", closeCopied).put("reason", Objects.requireNonNull(reason, "reason"));
        return send("POST", "/copy/leaders/" + id(leaderId, "leaderId") + "/retire", "retireLeader", CopyLeaderRetire.class, body,
                idempotencyKey, CopyLeader.class);
    }

    /**
     * An external leader opens a position (a market entry), copied to its followers. An {@code id} sent
     * before is answered with the original signal and {@code duplicate} true, whatever the key; nothing is
     * copied twice. A leader that is not external is a {@link StateException} ({@code leader_not_external}).
     *
     * @param leaderId the leader
     * @param id your id for the signal, unique for the leader (the contract's {@code id}); a close names it
     * @param symbol the leader's symbol
     * @param side buy or sell
     * @param idempotencyKey the caller's key
     * @param open its size, exactly one of a volume or units, and its optional fields
     * @return the signal
     * @throws IllegalArgumentException the size is below zero, or a field does not fit the contract
     */
    public CompletableFuture<CopySignalAccepted> sendOpenSignal(UUID leaderId, String id, String symbol, CopyExternalOpen.SideEnum side,
            String idempotencyKey, OpenSignal open) {
        Objects.requireNonNull(open, "open");
        Body body = Body.of().put("id", Objects.requireNonNull(id, "id")).put("symbol", Objects.requireNonNull(symbol, "symbol"))
                .put("side", Objects.requireNonNull(side, "side"))
                .put("volume", open.volume() == null ? null : size("volume", open.volume()))
                .put("units", open.units() == null ? null : size("units", open.units()))
                .put("instrument_key", open.instrumentKey())
                .put("sl", open.sl() == null ? null : open.sl().toPlainString())
                .put("tp", open.tp() == null ? null : open.tp().toPlainString())
                .put("time", open.time()).put("account", open.account());
        return send("POST", "/copy/leaders/" + id(leaderId, "leaderId") + "/signals", "sendOpenSignal", CopyExternalOpen.class, body,
                idempotencyKey, CopySignalAccepted.class);
    }

    /**
     * An external leader closes the position an earlier open made; every follower closes what it copied.
     *
     * @param leaderId the leader
     * @param id this signal's own id
     * @param close the open's id
     * @param idempotencyKey the caller's key
     * @return the signal
     */
    public CompletableFuture<CopySignalAccepted> sendCloseSignal(UUID leaderId, String id, String close, String idempotencyKey) {
        return closeSignal(leaderId, id, close, null, idempotencyKey);
    }

    /**
     * An external leader closes the position an earlier open made, at a moment it names.
     *
     * @param leaderId the leader
     * @param id this signal's own id
     * @param close the open's id
     * @param time when the leader closed it
     * @param idempotencyKey the caller's key
     * @return the signal
     */
    public CompletableFuture<CopySignalAccepted> sendCloseSignal(UUID leaderId, String id, String close, OffsetDateTime time,
            String idempotencyKey) {
        return closeSignal(leaderId, id, close, Objects.requireNonNull(time, "time"), idempotencyKey);
    }

    private CompletableFuture<CopySignalAccepted> closeSignal(UUID leaderId, String id, String close, @Nullable OffsetDateTime time,
            String idempotencyKey) {
        Body body = Body.of().put("id", Objects.requireNonNull(id, "id")).put("close", Objects.requireNonNull(close, "close")).put("time", time);
        return send("POST", "/copy/leaders/" + id(leaderId, "leaderId") + "/signals", "sendCloseSignal", CopyExternalClose.class, body,
                idempotencyKey, CopySignalAccepted.class);
    }

    // --- followers --------------------------------------------------------------------------------

    /**
     * Makes one of your accounts follow a leader, with its own mandate. {@code scaling} is required unless
     * the group named has it in its template. The follow is checked once, here, against the account's
     * venue: an option the venue cannot honour is a {@link ValidationException}
     * ({@code not_on_this_venue}). {@link #previewFollower} shows each symbol's match and volume first.
     *
     * @param accountId the follower account
     * @param leaderId the leader
     * @param idempotencyKey the caller's key
     * @param mandate the scaling, bounds, symbol map, options, limits, stop-out close and group
     * @return the follower
     */
    public CompletableFuture<CopyFollower> createFollower(UUID accountId, UUID leaderId, String idempotencyKey, FollowerMandate mandate) {
        Body body = Body.of().put("account_id", id(accountId, "accountId")).put("leader_id", id(leaderId, "leaderId"))
                .put("scaling", mandate.scaling()).put("bounds", mandate.bounds()).put("symbol_map", mandate.symbolMap())
                .put("options", mandate.options()).put("risk_limits", mandate.riskLimits()).put("close_on_stopout", mandate.closeOnStopout())
                .put("group_id", mandate.groupId());
        return send("POST", "/copy/followers", "createFollower", CopyFollowerCreate.class, body, idempotencyKey, CopyFollower.class);
    }

    /**
     * What a follow would do before it is made: for each row (a leader {@code symbol} and {@code volume}),
     * the follower's symbol, how it matched and its volume, or why it would be refused. Read-only: nothing
     * is created and no key is taken, so it is retried after a 429 as every read is. 1 to 200 rows.
     *
     * @param accountId the follower account
     * @param leaderId the leader
     * @param scaling the scaling to try
     * @param rows the rows, in the contract's names
     * @return the preview
     */
    public CompletableFuture<CopyPreview> previewFollower(UUID accountId, UUID leaderId, Map<String, ?> scaling,
            List<? extends Map<String, ?>> rows) {
        return previewFollower(accountId, leaderId, scaling, rows, PreviewFollower.none());
    }

    /**
     * What a follow would do, with its bounds, symbol map and options.
     *
     * @param accountId the follower account
     * @param leaderId the leader
     * @param scaling the scaling to try
     * @param rows the rows, in the contract's names
     * @param options the bounds, symbol map and options
     * @return the preview
     * @throws IllegalArgumentException there is no row
     */
    public CompletableFuture<CopyPreview> previewFollower(UUID accountId, UUID leaderId, Map<String, ?> scaling,
            List<? extends Map<String, ?>> rows, PreviewFollower options) {
        if (Objects.requireNonNull(rows, "rows").isEmpty()) {
            throw new IllegalArgumentException("previewFollower needs at least one row");
        }
        Body body = Body.of().put("account_id", id(accountId, "accountId")).put("leader_id", id(leaderId, "leaderId"))
                .put("scaling", Objects.requireNonNull(scaling, "scaling")).put("rows", List.copyOf(rows)).put("bounds", options.bounds())
                .put("symbol_map", options.symbolMap()).put("options", options.options());
        Map<String, @Nullable Object> sent = Contract.body("previewFollower", CopyPreviewRequest.class, body);
        // DECISION: a preview creates nothing, so it is a read sent as POST.
        return client.read(MfxRequest.write("POST", "/copy/followers/preview", List.of(), sent, null))
                .thenApply(answer -> MfxClient.body(answer, CopyPreview.class, "POST /copy/followers/preview"));
    }

    /**
     * One follower: its mandate and version, its state and the stop-outs in force.
     *
     * @param followerId the follower
     * @return the follower
     */
    public CompletableFuture<CopyFollower> getFollower(UUID followerId) {
        return client.get("/copy/followers/" + id(followerId, "followerId"), CopyFollower.class);
    }

    /**
     * One page of your followers, newest first unless sorted otherwise, with the count of each state, and of
     * those out of step, in {@code stats}. A group's followers are the filter's {@code groupId}.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the states, leader, account, group, out of step, order and page size
     * @return the page
     */
    public CompletableFuture<CopyPage<CopyFollower, CopyFollowerPageStats>> followers(@Nullable String cursor, ListCopyFollowers filter) {
        return page("/copy/followers", Objects.requireNonNull(filter, "filter").query(cursor), CopyFollower.class, CopyFollowerPageStats.class);
    }

    /**
     * Every follower, following the cursor. Iterating blocks on each page.
     *
     * @param filter the states, leader, account, group, out of step, order and page size
     * @return every follower
     */
    public Iterable<CopyFollower> allFollowers(ListCopyFollowers filter) {
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> followers(cursor, filter).thenApply(CopyPage::page));
    }

    /**
     * Changes a follower's mandate; only what is set is sent, and the mandate's version moves. Checked as a
     * follow is; an unfollowed follower is a {@link StateException}.
     *
     * @param followerId the follower
     * @param idempotencyKey the caller's key
     * @param change what changes; a group is named only when a follower is made
     * @return the follower
     * @throws IllegalArgumentException the change names a group
     */
    public CompletableFuture<CopyFollower> updateFollower(UUID followerId, String idempotencyKey, FollowerMandate change) {
        if (change.groupId() != null) {
            throw new IllegalArgumentException("updateFollower changes a mandate: a follower's group is changed with addGroupFollowers");
        }
        Body body = Body.of().put("scaling", change.scaling()).put("bounds", change.bounds()).put("symbol_map", change.symbolMap())
                .put("options", change.options()).put("risk_limits", change.riskLimits()).put("close_on_stopout", change.closeOnStopout());
        return send("PATCH", "/copy/followers/" + id(followerId, "followerId"), "updateFollower", CopyFollowerUpdate.class, body,
                idempotencyKey, CopyFollower.class);
    }

    /**
     * Stops copying to this follower; it keeps its positions.
     *
     * @param followerId the follower
     * @param idempotencyKey the caller's key
     * @return the follower
     */
    public CompletableFuture<CopyFollower> pauseFollower(UUID followerId, String idempotencyKey) {
        return post("/copy/followers/" + id(followerId, "followerId") + "/pause", idempotencyKey, CopyFollower.class);
    }

    /**
     * Copies to this follower again; refused while one of its stop-outs is active.
     *
     * @param followerId the follower
     * @param idempotencyKey the caller's key
     * @return the follower
     */
    public CompletableFuture<CopyFollower> resumeFollower(UUID followerId, String idempotencyKey) {
        return post("/copy/followers/" + id(followerId, "followerId") + "/resume", idempotencyKey, CopyFollower.class);
    }

    /**
     * Heals the follower's copied positions from the account. Nothing is traded; drift found is sent as
     * {@code copy.drift}.
     *
     * @param followerId the follower
     * @param idempotencyKey the caller's key
     * @return the follower
     */
    public CompletableFuture<CopyFollower> resyncFollower(UUID followerId, String idempotencyKey) {
        return post("/copy/followers/" + id(followerId, "followerId") + "/resync", idempotencyKey, CopyFollower.class);
    }

    /**
     * Ends the follow. With {@code closeCopied} the volume copied to it is closed (a requested close),
     * never the account's own positions. {@code closeCopied} has no default.
     *
     * @param followerId the follower
     * @param closeCopied whether to close what was copied
     * @param idempotencyKey the caller's key
     * @return the follower, unfollowed
     */
    public CompletableFuture<CopyFollower> unfollow(UUID followerId, boolean closeCopied, String idempotencyKey) {
        return send("POST", "/copy/followers/" + id(followerId, "followerId") + "/unfollow", "unfollow", CopyUnfollow.class,
                Body.of().put("close_copied", closeCopied), idempotencyKey, CopyFollower.class);
    }

    // --- stop-outs --------------------------------------------------------------------------------

    /**
     * One page of a follower's stop-outs, newest first: each with its limit, its period, what it closed
     * and, once reset, who reset it and why.
     *
     * @param followerId the follower
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the states and page size
     * @return the page
     */
    public CompletableFuture<Page<CopyStopout>> stopouts(UUID followerId, @Nullable String cursor, ListCopyStopouts filter) {
        return client.page("/copy/followers/" + id(followerId, "followerId") + "/stopouts", Objects.requireNonNull(filter, "filter").query(cursor),
                CopyStopout.class);
    }

    /**
     * Every stop-out of a follower, following the cursor. Iterating blocks on each page.
     *
     * @param followerId the follower
     * @param filter the states and page size
     * @return every stop-out
     */
    public Iterable<CopyStopout> allStopouts(UUID followerId, ListCopyStopouts filter) {
        Objects.requireNonNull(followerId, "followerId");
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> stopouts(followerId, cursor, filter));
    }

    /**
     * Resets the follower's active stop-outs, every limit. Positions a stop-out closed stay closed. No
     * active stop-out is a {@link StateException} ({@code no_active_stopout}).
     *
     * @param followerId the follower
     * @param reason why, for the audit
     * @param idempotencyKey the caller's key
     * @return the follower
     */
    public CompletableFuture<CopyFollower> resetStopouts(UUID followerId, String reason, String idempotencyKey) {
        return reset(followerId, reason, null, idempotencyKey);
    }

    /**
     * Resets the follower's active stop-out of one limit.
     *
     * @param followerId the follower
     * @param reason why, for the audit
     * @param limit daily, monthly or lifetime
     * @param idempotencyKey the caller's key
     * @return the follower
     */
    public CompletableFuture<CopyFollower> resetStopouts(UUID followerId, String reason, CopyLimitKind limit, String idempotencyKey) {
        return reset(followerId, reason, Objects.requireNonNull(limit, "limit"), idempotencyKey);
    }

    private CompletableFuture<CopyFollower> reset(UUID followerId, String reason, @Nullable CopyLimitKind limit, String idempotencyKey) {
        return send("POST", "/copy/followers/" + id(followerId, "followerId") + "/stopouts/reset", "resetStopouts", CopyStopoutReset.class,
                Body.of().put("reason", Objects.requireNonNull(reason, "reason")).put("limit", limit), idempotencyKey, CopyFollower.class);
    }

    // --- groups -----------------------------------------------------------------------------------

    /**
     * A group of followers, with no template, cap or policy.
     *
     * @param name the group's name
     * @param idempotencyKey the caller's key
     * @return the group
     */
    public CompletableFuture<CopyGroup> createGroup(String name, String idempotencyKey) {
        return createGroup(name, idempotencyKey, CreateGroup.none());
    }

    /**
     * A group of followers: a template their mandates start from, and a gross exposure cap.
     *
     * @param name the group's name
     * @param idempotencyKey the caller's key
     * @param options the template, cap and policy
     * @return the group
     * @throws IllegalArgumentException the cap is below zero, or the template does not fit the contract
     */
    public CompletableFuture<CopyGroup> createGroup(String name, String idempotencyKey, CreateGroup options) {
        Body body = Body.of().put("name", Objects.requireNonNull(name, "name")).put("template", options.template())
                .put("gross_exposure_cap", options.grossExposureCap() == null ? null : size("grossExposureCap", options.grossExposureCap()))
                .put("placement_policy", options.placementPolicy());
        return send("POST", "/copy/groups", "createGroup", CopyGroupCreate.class, body, idempotencyKey, CopyGroup.class);
    }

    /**
     * One group, with its exposure against its cap.
     *
     * @param groupId the group
     * @return the group
     */
    public CompletableFuture<CopyGroup> getGroup(UUID groupId) {
        return client.get("/copy/groups/" + id(groupId, "groupId"), CopyGroup.class);
    }

    /**
     * One page of your groups, with {@code stats}.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the state and page size
     * @return the page
     */
    public CompletableFuture<CopyPage<CopyGroup, CopyGroupPageStats>> groups(@Nullable String cursor, ListCopyGroups filter) {
        return page("/copy/groups", Objects.requireNonNull(filter, "filter").query(cursor), CopyGroup.class, CopyGroupPageStats.class);
    }

    /**
     * Every group, following the cursor. Iterating blocks on each page.
     *
     * @param filter the state and page size
     * @return every group
     */
    public Iterable<CopyGroup> allGroups(ListCopyGroups filter) {
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> groups(cursor, filter).thenApply(CopyPage::page));
    }

    /**
     * Changes a group's name, template, exposure cap or placement policy; only what is set is sent, and the
     * cap and policy can be cleared.
     *
     * @param groupId the group
     * @param idempotencyKey the caller's key
     * @param change what changes
     * @return the group
     */
    public CompletableFuture<CopyGroup> updateGroup(UUID groupId, String idempotencyKey, UpdateGroup change) {
        Body body = Body.of().put("name", change.name()).put("template", change.template());
        if (change.capChanges()) {
            BigDecimal cap = change.grossExposureCap();
            body.put("gross_exposure_cap", cap == null ? NullNode.getInstance() : size("grossExposureCap", cap));
        }
        if (change.policyChanges()) {
            String policy = change.placementPolicy();
            body.put("placement_policy", policy == null ? NullNode.getInstance() : policy);
        }
        return send("PATCH", "/copy/groups/" + id(groupId, "groupId"), "updateGroup", CopyGroupUpdate.class, body, idempotencyKey,
                CopyGroup.class);
    }

    /**
     * Adds followers to the group, each answered on its own (one in another group is refused
     * {@code in_another_group}). Mandates are not changed.
     *
     * @param groupId the group
     * @param followerIds 1 to 500 followers
     * @param idempotencyKey the caller's key
     * @return each follower's outcome
     */
    public CompletableFuture<CopyGroupMembersResult> addGroupFollowers(UUID groupId, List<UUID> followerIds, String idempotencyKey) {
        return members(groupId, "/followers", "addGroupFollowers", followerIds, idempotencyKey);
    }

    /**
     * Takes followers out of the group, each answered on its own.
     *
     * @param groupId the group
     * @param followerIds 1 to 500 followers
     * @param idempotencyKey the caller's key
     * @return each follower's outcome
     */
    public CompletableFuture<CopyGroupMembersResult> removeGroupFollowers(UUID groupId, List<UUID> followerIds, String idempotencyKey) {
        return members(groupId, "/followers/remove", "removeGroupFollowers", followerIds, idempotencyKey);
    }

    private CompletableFuture<CopyGroupMembersResult> members(UUID groupId, String tail, String name, List<UUID> followerIds,
            String idempotencyKey) {
        List<String> ids = Objects.requireNonNull(followerIds, "followerIds").stream().map(f -> id(f, "followerIds")).toList();
        if (ids.isEmpty() || ids.size() > MEMBERS_MAX) {
            throw new IllegalArgumentException(name + " takes 1 to " + MEMBERS_MAX + " follower ids, not " + ids.size());
        }
        return send("POST", "/copy/groups/" + id(groupId, "groupId") + tail, name, CopyGroupMembers.class, Body.of().put("follower_ids", ids),
                idempotencyKey, CopyGroupMembersResult.class);
    }

    /**
     * Pauses every member; each keeps its own state, so resuming restores each one as it was.
     *
     * @param groupId the group
     * @param idempotencyKey the caller's key
     * @return the group
     */
    public CompletableFuture<CopyGroup> pauseGroup(UUID groupId, String idempotencyKey) {
        return post("/copy/groups/" + id(groupId, "groupId") + "/pause", idempotencyKey, CopyGroup.class);
    }

    /**
     * Resumes the group.
     *
     * @param groupId the group
     * @param idempotencyKey the caller's key
     * @return the group
     */
    public CompletableFuture<CopyGroup> resumeGroup(UUID groupId, String idempotencyKey) {
        return post("/copy/groups/" + id(groupId, "groupId") + "/resume", idempotencyKey, CopyGroup.class);
    }

    /**
     * Closes the copied positions of every live member under one {@code batch_id}, each a requested close.
     *
     * @param groupId the group
     * @param reason why
     * @param pause whether to pause the group too; no default
     * @param idempotencyKey the caller's key
     * @return the batch and its closes
     */
    public CompletableFuture<CopyGroupCloseResult> closeGroup(UUID groupId, String reason, boolean pause, String idempotencyKey) {
        return send("POST", "/copy/groups/" + id(groupId, "groupId") + "/close", "closeGroup", CopyGroupClose.class,
                Body.of().put("reason", Objects.requireNonNull(reason, "reason")).put("pause", pause), idempotencyKey,
                CopyGroupCloseResult.class);
    }

    /**
     * Sets the group's template on its members, every field it sets, from the first member.
     *
     * @param groupId the group
     * @param dryRun true to see what it would change; a dry run takes no key
     * @param idempotencyKey the caller's key for an applied run; null for a dry run
     * @return each member's outcome
     */
    public CompletableFuture<CopyGroupRolloverResult> rolloverGroup(UUID groupId, boolean dryRun, @Nullable String idempotencyKey) {
        return rolloverGroup(groupId, dryRun, RolloverGroup.none(), idempotencyKey);
    }

    /**
     * Sets the group's template on its members, or with {@code dryRun} shows what it would change.
     *
     * @param groupId the group
     * @param dryRun true to see what it would change
     * @param options the fields, the member to continue after, and how many
     * @param idempotencyKey the caller's key for an applied run; null for a dry run
     * @return each member's outcome, and where to continue
     * @throws IllegalArgumentException an applied run has no key, or a dry run has one
     */
    public CompletableFuture<CopyGroupRolloverResult> rolloverGroup(UUID groupId, boolean dryRun, RolloverGroup options,
            @Nullable String idempotencyKey) {
        // DECISION: the contract requires the key unless dry_run is true, and a dry run needs none.
        if (!dryRun && idempotencyKey == null) {
            throw new IllegalArgumentException("an applied rolloverGroup (dryRun false) needs an idempotency key");
        }
        if (dryRun && idempotencyKey != null) {
            throw new IllegalArgumentException("a dry run of rolloverGroup changes nothing and takes no key");
        }
        Body body = Body.of().put("dry_run", dryRun).put("fields", options.fields()).put("after", options.after()).put("limit", options.limit());
        Map<String, @Nullable Object> sent = Contract.body("rolloverGroup", CopyGroupRollover.class, body);
        return client.write("POST", "/copy/groups/" + id(groupId, "groupId") + "/rollover", List.of(), sent,
                idempotencyKey == null ? null : MfxRequest.key(idempotencyKey), CopyGroupRolloverResult.class);
    }

    // --- requested closes -------------------------------------------------------------------------

    /**
     * One page of requested closes of copied positions, newest first, with {@code stats}.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the follower, leader, group, batch, states, window and page size
     * @return the page
     */
    public CompletableFuture<CopyPage<CopyClose, CopyClosePageStats>> closes(@Nullable String cursor, ListCopyCloses filter) {
        return page("/copy/closes", Objects.requireNonNull(filter, "filter").query(cursor), CopyClose.class, CopyClosePageStats.class);
    }

    /**
     * Every requested close, following the cursor. Iterating blocks on each page.
     *
     * @param filter the follower, leader, group, batch, states, window and page size
     * @return every close
     */
    public Iterable<CopyClose> allCloses(ListCopyCloses filter) {
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> closes(cursor, filter).thenApply(CopyPage::page));
    }

    /**
     * One requested close: what it covers, each position's attempts, and its state. Another tenant's is a
     * {@link NotFoundException}.
     *
     * @param closeId the close
     * @return the close
     */
    public CompletableFuture<CopyClose> getClose(UUID closeId) {
        return client.get("/copy/closes/" + id(closeId, "closeId"), CopyClose.class);
    }

    // --- the log, the signals and every stop-out --------------------------------------------------

    /**
     * One page of the copy log, newest first unless sorted otherwise, inside a window of at most 31 days
     * (wider is a {@link ValidationException}, {@code window_too_wide}), with {@code stats}: the outcomes and
     * the p50, p95 and p99 of MFX Hub's part.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the leader, follower, account, group, signal, outcomes, reasons, venues, window and order
     * @return the page
     */
    public CompletableFuture<CopyPage<CopyLogEntry, CopyLogPageStats>> log(@Nullable String cursor, ListCopyLog filter) {
        return page("/copy/log", Objects.requireNonNull(filter, "filter").query(cursor), CopyLogEntry.class, CopyLogPageStats.class);
    }

    /**
     * Every row of the copy log the filter matches, following the cursor. Iterating blocks on each page.
     *
     * @param filter the filters, window and order
     * @return every row
     */
    public Iterable<CopyLogEntry> allLog(ListCopyLog filter) {
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> log(cursor, filter).thenApply(CopyPage::page));
    }

    /**
     * One page of your leaders' signals, newest first, each with how many of its copies were filled,
     * refused and skipped; {@code stats} counts every signal the filter matches, by kind.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the leader, kinds, actions, window and page size
     * @return the page
     */
    public CompletableFuture<CopyPage<CopySignalEntry, CopySignalPageStats>> signals(@Nullable String cursor, ListCopySignals filter) {
        return page("/copy/signals", Objects.requireNonNull(filter, "filter").query(cursor), CopySignalEntry.class, CopySignalPageStats.class);
    }

    /**
     * Every signal the filter matches, following the cursor. Iterating blocks on each page.
     *
     * @param filter the leader, kinds, actions, window and page size
     * @return every signal
     */
    public Iterable<CopySignalEntry> allSignals(ListCopySignals filter) {
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> signals(cursor, filter).thenApply(CopyPage::page));
    }

    /**
     * One page of every stop-out of your followers, newest first, with a count of each state.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the states, follower, group, window and page size
     * @return the page
     */
    public CompletableFuture<CopyPage<CopyStopout, CopyTenantStopoutPageStats>> tenantStopouts(@Nullable String cursor,
            ListTenantStopouts filter) {
        return page("/copy/stopouts", Objects.requireNonNull(filter, "filter").query(cursor), CopyStopout.class,
                CopyTenantStopoutPageStats.class);
    }

    /**
     * Every stop-out the filter matches, following the cursor. Iterating blocks on each page.
     *
     * @param filter the states, follower, group, window and page size
     * @return every stop-out
     */
    public Iterable<CopyStopout> allTenantStopouts(ListTenantStopouts filter) {
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> tenantStopouts(cursor, filter).thenApply(CopyPage::page));
    }
}
