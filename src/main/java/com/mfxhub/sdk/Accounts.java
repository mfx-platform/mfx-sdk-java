package com.mfxhub.sdk;

import com.mfxhub.sdk.models.Account;
import com.mfxhub.sdk.models.Deal;
import com.mfxhub.sdk.models.PendingOrderList;
import com.mfxhub.sdk.models.PositionList;
import com.mfxhub.sdk.models.SpreadList;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * What a terminal's account looks like right now ({@code client.accounts()}): reads, failing with the
 * typed errors. Live lists are snapshots: positions and pending orders come back whole with their
 * {@code as_of}, never behind a cursor. History (deals) is paged, newest first, inside a window.
 * Everything carries when it was observed, and a half never observed is null rather than zero.
 */
public final class Accounts {
    private final MfxClient client;

    Accounts(MfxClient client) {
        this.client = client;
    }

    private static String terminal(UUID terminalId) {
        return "/terminals/" + Objects.requireNonNull(terminalId, "terminalId");
    }

    /**
     * Balance, equity and margin, plus the slow profile facts, each half with its own {@code ts}; a half
     * never observed is null, not zero.
     *
     * @param terminalId the terminal
     * @return the account
     */
    public CompletableFuture<Account> account(UUID terminalId) {
        return client.get(terminal(terminalId) + "/account", Account.class);
    }

    /**
     * Every open position, as one snapshot with its {@code as_of}.
     *
     * @param terminalId the terminal
     * @return the positions
     */
    public CompletableFuture<PositionList> positions(UUID terminalId) {
        return client.get(terminal(terminalId) + "/positions", PositionList.class);
    }

    /**
     * Every pending order, as one snapshot with its {@code as_of}.
     *
     * @param terminalId the terminal
     * @return the pending orders
     */
    public CompletableFuture<PendingOrderList> pendingOrders(UUID terminalId) {
        return client.get(terminal(terminalId) + "/orders/pending", PendingOrderList.class);
    }

    /**
     * Closed deals, newest first, inside a window. The window is required, by the API and so by this: an
     * unbounded history request over a partitioned table is the query that takes a database down.
     *
     * @param terminalId the terminal
     * @param from the window's start
     * @param to the window's end
     * @param cursor the cursor of the page before; the first page when null
     * @param limit the page size; the platform's default when null
     * @return the page
     */
    public CompletableFuture<Page<Deal>> deals(UUID terminalId, OffsetDateTime from, OffsetDateTime to, @Nullable String cursor,
            @Nullable Integer limit) {
        Query query = Query.of().add("from", Objects.requireNonNull(from, "from")).add("to", Objects.requireNonNull(to, "to"))
                .add("cursor", cursor).add("limit", limit);
        return client.page(terminal(terminalId) + "/history/deals", query.build(), Deal.class);
    }

    /**
     * The first page of closed deals inside a window.
     *
     * @param terminalId the terminal
     * @param from the window's start
     * @param to the window's end
     * @return the page
     */
    public CompletableFuture<Page<Deal>> deals(UUID terminalId, OffsetDateTime from, OffsetDateTime to) {
        return deals(terminalId, from, to, null, null);
    }

    /**
     * The spread this terminal's own account is quoted, with each reading's age: the spread a user trades
     * against is the one their broker gives their account, so it is read from their terminal.
     *
     * @param terminalId the terminal
     * @return the spreads
     */
    public CompletableFuture<SpreadList> spread(UUID terminalId) {
        return client.get(terminal(terminalId) + "/spread", SpreadList.class);
    }
}
