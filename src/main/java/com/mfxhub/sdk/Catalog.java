package com.mfxhub.sdk;

import com.mfxhub.sdk.models.BrokerCatalogue;
import com.mfxhub.sdk.models.BrokerCatalogueList;
import com.mfxhub.sdk.models.Symbol;
import com.mfxhub.sdk.models.SymbolQuote;
import com.mfxhub.sdk.models.TenantBroker;
import com.mfxhub.sdk.models.TenantBrokerServer;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * What MFX supports, and what a broker actually lists ({@code client.catalog()}): the brokers, the servers
 * each one runs, and the symbols a server lists with the facts a caller needs before trading them, in the
 * broker's own names. Reads fail with the typed errors; nothing here is an execution call.
 */
public final class Catalog {
    private final MfxClient client;

    Catalog(MfxClient client) {
        this.client = client;
    }

    private static String broker(UUID brokerId) {
        return "/brokers/" + Objects.requireNonNull(brokerId, "brokerId");
    }

    /**
     * One page of the brokers MFX supports, with each one's status: {@code active} is usable,
     * {@code suspended} and {@code retired} are not. A terminal requested on a broker that is not usable
     * is refused with {@link UnsupportedBrokerException}.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param limit the page size; the platform's default when null
     * @return the page
     */
    public CompletableFuture<Page<TenantBroker>> brokers(@Nullable String cursor, @Nullable Integer limit) {
        return client.page("/brokers", Query.page(cursor, limit).build(), TenantBroker.class);
    }

    /**
     * The first page of the brokers, in the platform's default size.
     *
     * @return the page
     */
    public CompletableFuture<Page<TenantBroker>> brokers() {
        return brokers(null, null);
    }

    /**
     * Every broker, following the cursor. Iterating blocks on each page's request.
     *
     * @return every broker
     */
    public Iterable<TenantBroker> allBrokers() {
        return Walk.of(cursor -> brokers(cursor, null));
    }

    /**
     * The trading servers this broker runs, and whether MFX can use each one now: {@code enabled} is the
     * per-server switch and {@code disabled_reason} the operator's sentence for it, the same words the
     * refusal carries. A terminal on a disabled server is refused as {@link UnsupportedBrokerException}
     * naming {@code server_disabled}: try another server of this broker. One page.
     *
     * @param brokerId the broker
     * @param cursor the cursor of the page before; the first page when null
     * @param limit the page size; the platform's default when null
     * @return the page
     */
    public CompletableFuture<Page<TenantBrokerServer>> servers(UUID brokerId, @Nullable String cursor, @Nullable Integer limit) {
        return client.page(broker(brokerId) + "/servers", Query.page(cursor, limit).build(), TenantBrokerServer.class);
    }

    /**
     * The first page of this broker's servers.
     *
     * @param brokerId the broker
     * @return the page
     */
    public CompletableFuture<Page<TenantBrokerServer>> servers(UUID brokerId) {
        return servers(brokerId, null, null);
    }

    /**
     * One row per server and account type you hold a symbol catalogue for on this broker. Each carries
     * {@code catalogue_hash}, a digest of the whole symbol list that changes when a symbol is added,
     * removed or comes back or its spec, description or identity changes, never on a quote: read
     * {@link #symbols(UUID)} again only when it differs. A broker you hold no catalogue for answers an
     * empty list.
     *
     * @param brokerId the broker
     * @return the rows
     */
    public CompletableFuture<List<BrokerCatalogue>> catalogues(UUID brokerId) {
        return client.get(broker(brokerId) + "/catalogues", BrokerCatalogueList.class)
                .thenApply(list -> list.getItems() == null ? List.of() : List.copyOf(list.getItems()));
    }

    /**
     * The first page of the symbols this broker lists, on every server and account type of yours.
     *
     * @param brokerId the broker
     * @return the page
     */
    public CompletableFuture<Page<Symbol>> symbols(UUID brokerId) {
        return symbols(brokerId, WhichServer.any(), null, null);
    }

    /**
     * The symbols this broker lists, in its own names: the names to pass to {@code terminals().create}
     * and to {@code execution().placeOrder}, because MFX never renames an instrument. The page's
     * {@link Page#source()} is {@code live} when a terminal of yours runs on the server and account type,
     * {@code last_known} when none does any more.
     *
     * @param brokerId the broker
     * @param which the server and account type, for a tenant with more than one on this broker
     * @param cursor the cursor of the page before; the first page when null
     * @param limit the page size; the platform's default when null
     * @return the page
     */
    public CompletableFuture<Page<Symbol>> symbols(UUID brokerId, WhichServer which, @Nullable String cursor, @Nullable Integer limit) {
        Query query = which.query().add("cursor", cursor).add("limit", limit);
        return client.page(broker(brokerId) + "/symbols", query.build(), Symbol.class);
    }

    /**
     * One symbol's specification, including {@code pip_value_per_lot}.
     *
     * @param brokerId the broker
     * @param symbol the broker's own name for it
     * @return the symbol; a symbol listed on more than one of your servers or account types is a 409
     *     {@code ambiguous_symbol}: name them with {@link #symbol(UUID, String, WhichServer)}
     */
    public CompletableFuture<Symbol> symbol(UUID brokerId, String symbol) {
        return symbol(brokerId, symbol, WhichServer.any());
    }

    /**
     * One symbol's specification on the named server and account type.
     *
     * @param brokerId the broker
     * @param symbol the broker's own name for it
     * @param which the server and account type
     * @return the symbol
     */
    public CompletableFuture<Symbol> symbol(UUID brokerId, String symbol, WhichServer which) {
        String path = broker(brokerId) + "/symbols/" + MfxClient.segment(Objects.requireNonNull(symbol, "symbol"));
        return client.get(path, which.query().build(), Symbol.class);
    }

    /**
     * The current quote for one symbol, with its age.
     *
     * @param brokerId the broker
     * @param symbol the broker's own name for it
     * @return the quote
     */
    public CompletableFuture<SymbolQuote> quote(UUID brokerId, String symbol) {
        return quote(brokerId, symbol, WhichServer.any());
    }

    /**
     * The current quote for one symbol, with its age: a quote is never returned without saying how old
     * it is, because a stale quote reads exactly like a live one and gets acted on. It is read from one of
     * your terminals on that broker's server (any farm), from its latest tick or asked of it on demand (up
     * to 5 s). {@code state} says whether the market can be traded: {@code open},
     * {@code closed_session}, {@code paused}, {@code close_only}, {@code long_only}, {@code short_only},
     * {@code trade_disabled}, or {@code unknown}. {@code broker_time} is the broker's clock, not UTC. The
     * prices are exact decimal text.
     *
     * @param brokerId the broker
     * @param symbol the broker's own name for it
     * @param which the server and account type
     * @return the quote
     */
    public CompletableFuture<SymbolQuote> quote(UUID brokerId, String symbol, WhichServer which) {
        String path = broker(brokerId) + "/symbols/" + MfxClient.segment(Objects.requireNonNull(symbol, "symbol")) + "/quote";
        return client.get(path, which.query().build(), SymbolQuote.class);
    }
}
