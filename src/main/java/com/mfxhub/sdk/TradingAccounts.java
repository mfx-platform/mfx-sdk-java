package com.mfxhub.sdk;

import com.mfxhub.sdk.models.Account;
import com.mfxhub.sdk.models.CtraderGrant;
import com.mfxhub.sdk.models.CtraderGrantCreate;
import com.mfxhub.sdk.models.CtraderGrantStarted;
import com.mfxhub.sdk.models.Deal;
import com.mfxhub.sdk.models.OrderPlace;
import com.mfxhub.sdk.models.PendingOrderList;
import com.mfxhub.sdk.models.PositionList;
import com.mfxhub.sdk.models.TradingAccount;
import com.mfxhub.sdk.models.TradingAccountList;
import com.mfxhub.sdk.models.TradingSymbolList;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * The trading accounts this key's tenant holds, on every venue: a MetaTrader account or a cTrader one
 * ({@code client.tradingAccounts()}). The reads fail with the typed errors; the commands are
 * {@link Execution}'s, sent to {@code /accounts/{id}/...}, each completing with an
 * {@link ExecutionResult} and never exceptionally (read the outcome with {@code client.execution().waitFor}).
 * The cTrader grant flow connects a trader's cTrader accounts.
 */
public final class TradingAccounts {
    private final MfxClient client;
    private final Execution orders;

    TradingAccounts(MfxClient client) {
        this.client = client;
        this.orders = new Execution(client, "accounts");
    }

    private static String account(UUID accountId) {
        return "/accounts/" + Objects.requireNonNull(accountId, "accountId");
    }

    private static String grant(UUID grantId) {
        return "/accounts/ctrader/grants/" + Objects.requireNonNull(grantId, "grantId");
    }

    /**
     * One trading account, by its own id or by one of its terminals' ids. Another tenant's, or an id that
     * is not an account of yours, is a 404, never a 403.
     *
     * @param accountId the account, or one of its terminals
     * @return the account
     */
    public CompletableFuture<TradingAccount> get(UUID accountId) {
        return client.get(account(accountId), TradingAccount.class);
    }

    /**
     * One page of this tenant's trading accounts, newest first, retired ones included. Every filter
     * narrows the list on the platform before the page is cut.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the venues, environment, states and page size
     * @return the page
     */
    public CompletableFuture<Page<TradingAccount>> page(@Nullable String cursor, ListTradingAccounts filter) {
        return client.page("/accounts", Objects.requireNonNull(filter, "filter").query(cursor), TradingAccount.class);
    }

    /**
     * The first page of this tenant's trading accounts.
     *
     * @return the page
     */
    public CompletableFuture<Page<TradingAccount>> page() {
        return page(null, ListTradingAccounts.all());
    }

    /**
     * Every trading account, following the cursor, narrowed as {@link #page(String, ListTradingAccounts)}
     * is. Iterating blocks on each page's request.
     *
     * @param filter the venues, environment, states and page size
     * @return every account
     */
    public Iterable<TradingAccount> list(ListTradingAccounts filter) {
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> page(cursor, filter));
    }

    /**
     * Every trading account.
     *
     * @return every account
     */
    public Iterable<TradingAccount> list() {
        return list(ListTradingAccounts.all());
    }

    /**
     * Balance, equity, margin and profile, answered by the account's lead runner. On cTrader the equity is
     * computed by the platform and {@code stale} says when it could not be computed recently.
     *
     * @param accountId the account
     * @return the funds
     */
    public CompletableFuture<Account> funds(UUID accountId) {
        return client.get(account(accountId) + "/account", Account.class);
    }

    /**
     * Every open position, as one snapshot with its {@code as_of}. On cTrader {@code volume} is in lots
     * and {@code ticket} is the venue's position id.
     *
     * @param accountId the account
     * @return the positions
     */
    public CompletableFuture<PositionList> positions(UUID accountId) {
        return client.get(account(accountId) + "/positions", PositionList.class);
    }

    /**
     * Every pending order, as one snapshot with its {@code as_of}.
     *
     * @param accountId the account
     * @return the pending orders
     */
    public CompletableFuture<PendingOrderList> pendingOrders(UUID accountId) {
        return client.get(account(accountId) + "/orders/pending", PendingOrderList.class);
    }

    /**
     * The first page of deals inside a window of at most 31 days.
     *
     * @param accountId the account
     * @param from the window's start
     * @param to the window's end
     * @return the page
     */
    public CompletableFuture<Page<Deal>> deals(UUID accountId, OffsetDateTime from, OffsetDateTime to) {
        return deals(accountId, from, to, null, null, null);
    }

    /**
     * Deals newest first inside a window of at most 31 days, optionally of one position. Read from the
     * venue when asked: a venue that does not answer in time is an {@link UpstreamException}, and a window
     * wider than 31 days a {@link ValidationException}. On cTrader every time is UTC and {@code magic} is
     * the origin marker the order was placed with.
     *
     * @param accountId the account
     * @param from the window's start
     * @param to the window's end
     * @param position one position's deals only; every deal when null
     * @param cursor the cursor of the page before; the first page when null
     * @param limit the page size; the platform's default when null
     * @return the page
     */
    public CompletableFuture<Page<Deal>> deals(UUID accountId, OffsetDateTime from, OffsetDateTime to, @Nullable Long position,
            @Nullable String cursor, @Nullable Integer limit) {
        Query query = Query.of().add("from", Objects.requireNonNull(from, "from")).add("to", Objects.requireNonNull(to, "to"))
                .add("position", position).add("cursor", cursor).add("limit", limit);
        return client.page(account(accountId) + "/history/deals", query.build(), Deal.class);
    }

    /**
     * The symbols a cTrader account can trade, with their volume rules in lots and leverage: a volume a
     * command sends must be a whole {@code volume_step} between {@code volume_min} and {@code volume_max}.
     * A MetaTrader account is refused, and a cTrader account not connected now is an {@link UpstreamException}.
     *
     * @param accountId the account
     * @return the symbols
     */
    public CompletableFuture<TradingSymbolList> symbols(UUID accountId) {
        return client.get(account(accountId) + "/symbols", TradingSymbolList.class);
    }

    /**
     * Places a market order on the account; as {@link Execution#placeOrder}. {@code volume} is in lots on
     * every venue, and on cTrader a whole step of the symbol ({@link #symbols(UUID)}).
     *
     * @param accountId the account
     * @param symbol the venue's own name for it
     * @param side buy or sell
     * @param volume in lots, exactly
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> placeOrder(UUID accountId, String symbol, OrderPlace.SideEnum side, BigDecimal volume,
            String idempotencyKey) {
        return orders.placeOrder(accountId, symbol, side, volume, idempotencyKey);
    }

    /**
     * Places a market or pending order on the account; as {@link Execution#placeOrder}. On cTrader
     * {@code magic} stays on the order as its label and comes back on the deals, and a market order's stop
     * loss and take profit are set just after the fill ({@code protectionNotSet} says when that failed).
     *
     * @param accountId the account
     * @param symbol the venue's own name for it
     * @param side buy or sell
     * @param volume in lots, exactly
     * @param idempotencyKey the caller's key
     * @param options the order type, prices, stops, deviation, expiry, magic number and comment
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> placeOrder(UUID accountId, String symbol, OrderPlace.SideEnum side, BigDecimal volume,
            String idempotencyKey, PlaceOrder options) {
        return orders.placeOrder(accountId, symbol, side, volume, idempotencyKey, options);
    }

    /**
     * Cancels a pending order; as {@link Execution#cancelOrder}.
     *
     * @param accountId the account
     * @param ticket the order
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> cancelOrder(UUID accountId, long ticket, String idempotencyKey) {
        return orders.cancelOrder(accountId, ticket, idempotencyKey);
    }

    /**
     * Amends a pending order; as {@link Execution#modifyOrder}.
     *
     * @param accountId the account
     * @param ticket the order
     * @param idempotencyKey the caller's key
     * @param change what changes
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> modifyOrder(UUID accountId, long ticket, String idempotencyKey, ModifyOrder change) {
        return orders.modifyOrder(accountId, ticket, idempotencyKey, change);
    }

    /**
     * Moves a position's stop loss, take profit or both; as {@link Execution#modifyPosition}.
     *
     * @param accountId the account
     * @param ticket the position
     * @param idempotencyKey the caller's key
     * @param change the new levels
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> modifyPosition(UUID accountId, long ticket, String idempotencyKey, ModifyPosition change) {
        return orders.modifyPosition(accountId, ticket, idempotencyKey, change);
    }

    /**
     * Closes a position in full; as {@link Execution#closePosition(UUID, long, String)}.
     *
     * @param accountId the account
     * @param ticket the position
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> closePosition(UUID accountId, long ticket, String idempotencyKey) {
        return orders.closePosition(accountId, ticket, idempotencyKey);
    }

    /**
     * Closes a position in full with a deviation; as {@link Execution#closePosition(UUID, long, int, String)}.
     *
     * @param accountId the account
     * @param ticket the position
     * @param deviationPoints the deviation allowed, in points
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> closePosition(UUID accountId, long ticket, int deviationPoints, String idempotencyKey) {
        return orders.closePosition(accountId, ticket, deviationPoints, idempotencyKey);
    }

    /**
     * Closes a position in full with a comment for the closing deal; as
     * {@link Execution#closePosition(UUID, long, Integer, String, String)}, the same limit checked before
     * anything is sent.
     *
     * @param accountId the account
     * @param ticket the position
     * @param deviationPoints the deviation allowed, in points; null for the platform's default
     * @param comment the closing deal's comment; null for none
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     * @throws IllegalArgumentException a blank key, or a comment longer than {@link Execution#COMMENT_MAX} characters
     */
    public CompletableFuture<ExecutionResult> closePosition(UUID accountId, long ticket, @Nullable Integer deviationPoints,
            @Nullable String comment, String idempotencyKey) {
        return orders.closePosition(accountId, ticket, deviationPoints, comment, idempotencyKey);
    }

    /**
     * Closes a position against an opposite one; as {@link Execution#closePositionBy}. cTrader has no
     * close-by: it is done as two closes, the result says {@code closeByEmulated} and {@code deals} holds both.
     *
     * @param accountId the account
     * @param ticket the position being closed
     * @param byTicket the opposite one
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> closePositionBy(UUID accountId, long ticket, long byTicket, String idempotencyKey) {
        return orders.closePositionBy(accountId, ticket, byTicket, idempotencyKey);
    }

    /**
     * Closes part of a position, in lots; as {@link Execution#closePositionPartial(UUID, long, BigDecimal, String)}.
     *
     * @param accountId the account
     * @param ticket the position
     * @param volume the volume to close, exactly
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> closePositionPartial(UUID accountId, long ticket, BigDecimal volume, String idempotencyKey) {
        return orders.closePositionPartial(accountId, ticket, volume, idempotencyKey);
    }

    /**
     * Closes part of a position with a deviation.
     *
     * @param accountId the account
     * @param ticket the position
     * @param volume the volume to close, exactly
     * @param deviationPoints the deviation allowed, in points
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> closePositionPartial(UUID accountId, long ticket, BigDecimal volume, int deviationPoints,
            String idempotencyKey) {
        return orders.closePositionPartial(accountId, ticket, volume, deviationPoints, idempotencyKey);
    }

    /**
     * Closes part of a position with a comment for the closing deal; as
     * {@link Execution#closePositionPartial(UUID, long, BigDecimal, Integer, String, String)}.
     *
     * @param accountId the account
     * @param ticket the position
     * @param volume the volume to close, exactly
     * @param deviationPoints the deviation allowed, in points; null for the platform's default
     * @param comment the closing deal's comment; null for none
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     * @throws IllegalArgumentException a decimal below zero, a blank key, or a comment longer than
     *     {@link Execution#COMMENT_MAX} characters
     */
    public CompletableFuture<ExecutionResult> closePositionPartial(UUID accountId, long ticket, BigDecimal volume,
            @Nullable Integer deviationPoints, @Nullable String comment, String idempotencyKey) {
        return orders.closePositionPartial(accountId, ticket, volume, deviationPoints, comment, idempotencyKey);
    }

    /**
     * Trails a position's stop loss; as {@link Execution#setTrailingStop}. A venue that has no trailing
     * stop refuses it, as the result.
     *
     * @param accountId the account
     * @param ticket the position
     * @param distance a price difference above zero
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> setTrailingStop(UUID accountId, long ticket, BigDecimal distance, String idempotencyKey) {
        return orders.setTrailingStop(accountId, ticket, distance, idempotencyKey);
    }

    /**
     * Removes a position's trailing stop; as {@link Execution#clearTrailingStop}.
     *
     * @param accountId the account
     * @param ticket the position
     * @param idempotencyKey the caller's key
     * @return the result; never a failed future
     */
    public CompletableFuture<ExecutionResult> clearTrailingStop(UUID accountId, long ticket, String idempotencyKey) {
        return orders.clearTrailingStop(accountId, ticket, idempotencyKey);
    }

    /**
     * Starts connecting a trader's cTrader accounts, with no state of the tenant's carried back.
     *
     * @param environment live or demo: one grant covers one
     * @param scope {@code accounts} reads them only, {@code trading} reads and trades them
     * @param returnUrl where the trader comes back to (https)
     * @param idempotencyKey the caller's key
     * @return the consent link to send the trader to
     */
    public CompletableFuture<CtraderGrantStarted> startCtraderGrant(CtraderGrantCreate.EnvironmentEnum environment,
            CtraderGrantCreate.ScopeEnum scope, String returnUrl, String idempotencyKey) {
        return grantStart(environment, scope, returnUrl, null, idempotencyKey);
    }

    /**
     * Starts connecting a trader's cTrader accounts; answers the consent link to send them to. The trader
     * signs in at cTrader with their cTrader ID (MFX Hub never sees the password), chooses the accounts to
     * share, and comes back to {@code returnUrl} with {@code grant_id}, {@code result} and your
     * {@code tenantState}. The link lives 15 minutes. A plan without cTrader is a {@link ForbiddenException}.
     *
     * @param environment live or demo
     * @param scope accounts or trading
     * @param returnUrl where the trader comes back to (https)
     * @param tenantState your own state, handed back on the return
     * @param idempotencyKey the caller's key
     * @return the consent link
     */
    public CompletableFuture<CtraderGrantStarted> startCtraderGrant(CtraderGrantCreate.EnvironmentEnum environment,
            CtraderGrantCreate.ScopeEnum scope, String returnUrl, String tenantState, String idempotencyKey) {
        return grantStart(environment, scope, returnUrl, Objects.requireNonNull(tenantState, "tenantState"), idempotencyKey);
    }

    private CompletableFuture<CtraderGrantStarted> grantStart(CtraderGrantCreate.EnvironmentEnum environment,
            CtraderGrantCreate.ScopeEnum scope, String returnUrl, @Nullable String tenantState, String idempotencyKey) {
        Body body = Body.of().put("environment", Objects.requireNonNull(environment, "environment"))
                .put("scope", Objects.requireNonNull(scope, "scope"))
                .put("return_url", Objects.requireNonNull(returnUrl, "returnUrl"))
                .put("tenant_state", tenantState);
        return client.write("POST", "/accounts/ctrader/grants", body.build(), idempotencyKey, CtraderGrantStarted.class);
    }

    /**
     * One grant: its state (PENDING, ACTIVE, REVOKED or EXPIRED) and, once ACTIVE, the cTrader accounts the
     * trader shared.
     *
     * @param grantId the grant
     * @return the grant
     */
    public CompletableFuture<CtraderGrant> ctraderGrant(UUID grantId) {
        return client.get(grant(grantId), CtraderGrant.class);
    }

    /**
     * Makes some of an ACTIVE grant's accounts trading accounts of yours, by cTrader's ids. Each starts
     * PENDING and is CONNECTED once authorised at cTrader (an {@code account.connected} event says so).
     * Choosing one already connected returns it unchanged. 1 to 50 ids.
     *
     * @param grantId the grant
     * @param ctidTraderAccountIds cTrader's account ids
     * @param idempotencyKey the caller's key
     * @return the trading accounts
     * @throws IllegalArgumentException no id is given
     */
    public CompletableFuture<List<TradingAccount>> connectCtraderAccounts(UUID grantId, List<String> ctidTraderAccountIds,
            String idempotencyKey) {
        List<String> ids = List.copyOf(Objects.requireNonNull(ctidTraderAccountIds, "ctidTraderAccountIds"));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("connectCtraderAccounts needs at least one account id");
        }
        return client.write("POST", grant(grantId) + "/accounts", Body.of().put("ctid_trader_account_ids", ids).build(), idempotencyKey,
                TradingAccountList.class).thenApply(list -> list.getItems() == null ? List.of() : List.copyOf(list.getItems()));
    }

    /**
     * Revokes a grant: its accounts move to REAUTH_NEEDED (an {@code account.reauth_needed} event each) and
     * a new grant reconnects them. Revoking a revoked grant answers it unchanged.
     *
     * @param grantId the grant
     * @param idempotencyKey the caller's key
     * @return the grant
     */
    public CompletableFuture<CtraderGrant> revokeCtraderGrant(UUID grantId, String idempotencyKey) {
        return client.write("DELETE", grant(grantId), null, idempotencyKey, CtraderGrant.class);
    }
}
