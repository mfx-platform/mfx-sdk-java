package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.mfxhub.sdk.models.AccountMdSubscription;
import com.mfxhub.sdk.models.CandleList;
import com.mfxhub.sdk.models.MdSubscription;
import com.mfxhub.sdk.models.Tick;
import com.mfxhub.sdk.models.TickList;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;

/**
 * What this tenant has asked to be streamed, and the history it can read ({@code client.md()}): server
 * subscriptions, a cTrader account's subscriptions, candles and ticks; and the book and subscription-health
 * messages read as what they are.
 */
public final class MarketData {
    /** The message on {@code events} that says a subscription stopped delivering. */
    public static final String DEGRADED = "md.subscription_degraded";
    /** The message on {@code events} that says it delivers again. */
    public static final String RECOVERED = "md.subscription_recovered";
    /** The message on a {@code book:} channel. */
    public static final String BOOK = "md.book";

    private final MfxClient client;

    MarketData(MfxClient client) {
        this.client = client;
    }

    /**
     * Asks for a symbol's ticks on one broker server, ticks only.
     *
     * @param serverId the server
     * @param symbol the broker's own name, exactly as the catalogue lists it ({@code EURUSD.sd})
     * @param idempotencyKey the caller's key: the same key returns the same subscription
     * @return the subscription
     */
    public CompletableFuture<MdSubscription> subscribe(UUID serverId, String symbol, String idempotencyKey) {
        return subscribe(serverId, symbol, idempotencyKey, List.of());
    }

    /**
     * Asks for a symbol's ticks on one broker server, and its candles. The candles arrive on their own
     * channel ({@link Channel#candles}). The broker's suffix is part of the name: the platform matches what
     * the terminal reports rather than guessing.
     *
     * @param serverId the server
     * @param symbol the broker's own name
     * @param idempotencyKey the caller's key
     * @param timeframes the candles to build; none means ticks only
     * @return the subscription
     */
    public CompletableFuture<MdSubscription> subscribe(UUID serverId, String symbol, String idempotencyKey, List<Timeframe> timeframes) {
        Body body = Body.of().put("server_id", Objects.requireNonNull(serverId, "serverId"))
                .put("symbol", Objects.requireNonNull(symbol, "symbol"))
                .put("timeframes", timeframes.isEmpty() ? null : timeframes.stream().map(Timeframe::name).toList())
                .put("delivery", "ws");
        return client.write("POST", "/md/subscriptions", body.build(), idempotencyKey, MdSubscription.class);
    }

    /**
     * One page of what this tenant has subscribed, newest first.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param limit the page size; the platform's default when null
     * @return the page
     */
    public CompletableFuture<Page<MdSubscription>> subscriptions(@Nullable String cursor, @Nullable Integer limit) {
        return client.page("/md/subscriptions", Query.page(cursor, limit).build(), MdSubscription.class);
    }

    /**
     * The first page of this tenant's subscriptions.
     *
     * @return the page
     */
    public CompletableFuture<Page<MdSubscription>> subscriptions() {
        return subscriptions(null, null);
    }

    /**
     * Every subscription, following the cursor. Iterating blocks on each page's request.
     *
     * @return every subscription
     */
    public Iterable<MdSubscription> allSubscriptions() {
        return Walk.of(cursor -> subscriptions(cursor, null));
    }

    /**
     * Asks for a symbol's spots on one of the tenant's cTrader accounts: the counterpart of
     * {@link #subscribe(UUID, String, String)}, keyed on the account. The spots stream on
     * {@code md:{account_id}:{symbol}} as {@code md.tick}. The subscription is a resource, not a command:
     * it exists at once and is {@code pending} until the first spot. One per account and symbol: asking
     * again answers the live one. A MetaTrader account is a {@link StateException} ({@code not_on_this_venue}).
     *
     * @param accountId the cTrader account
     * @param symbol the account's own name for it
     * @param idempotencyKey the caller's key
     * @return the subscription
     */
    public CompletableFuture<AccountMdSubscription> subscribeAccount(UUID accountId, String symbol, String idempotencyKey) {
        return client.write("POST", "/accounts/" + Objects.requireNonNull(accountId, "accountId") + "/md/subscriptions",
                Body.of().put("symbol", Objects.requireNonNull(symbol, "symbol")).build(), idempotencyKey, AccountMdSubscription.class);
    }

    /**
     * One page of an account's market-data subscriptions, newest first.
     *
     * @param accountId the account
     * @param cursor the cursor of the page before; the first page when null
     * @param limit the page size; the platform's default when null
     * @return the page
     */
    public CompletableFuture<Page<AccountMdSubscription>> accountSubscriptions(UUID accountId, @Nullable String cursor,
            @Nullable Integer limit) {
        return client.page("/accounts/" + Objects.requireNonNull(accountId, "accountId") + "/md/subscriptions",
                Query.page(cursor, limit).build(), AccountMdSubscription.class);
    }

    /**
     * The first page of an account's subscriptions.
     *
     * @param accountId the account
     * @return the page
     */
    public CompletableFuture<Page<AccountMdSubscription>> accountSubscriptions(UUID accountId) {
        return accountSubscriptions(accountId, null, null);
    }

    /**
     * Every subscription of an account, following the cursor. Iterating blocks on each page's request.
     *
     * @param accountId the account
     * @return every subscription
     */
    public Iterable<AccountMdSubscription> allAccountSubscriptions(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        return Walk.of(cursor -> accountSubscriptions(accountId, cursor, null));
    }

    /**
     * Stops streaming this symbol. The channel stops; nothing else is affected.
     *
     * @param subscriptionId the subscription
     * @param idempotencyKey the caller's key
     * @return completes when the platform has stopped it
     */
    public CompletableFuture<Void> unsubscribe(UUID subscriptionId, String idempotencyKey) {
        return client.writeNothing("DELETE", "/md/subscriptions/" + Objects.requireNonNull(subscriptionId, "subscriptionId"), List.of(),
                null, MfxRequest.key(idempotencyKey));
    }

    /**
     * A symbol's bars for a window, oldest first, at most 1,000 a call, on the tenant's only server of
     * this broker.
     *
     * @param broker the broker
     * @param symbol the broker's own name
     * @param timeframe the bar's length
     * @param from the window's start
     * @param to the window's end
     * @return the bars
     */
    public CompletableFuture<CandleList> candles(UUID broker, String symbol, Timeframe timeframe, OffsetDateTime from, OffsetDateTime to) {
        return candles(broker, symbol, timeframe, from, to, null, null);
    }

    /**
     * A symbol's bars for a window, oldest first, at most 1,000 a call: the ones the broker's server shows
     * the tenant's own account, read through one of its ACTIVE terminals there. When the window holds more
     * than {@code limit} bars the answer is the newest of them; ask again with an earlier {@code to} for the
     * ones before. The bar still forming is included when the window reaches it. Each bar's {@code ts} is
     * its open time as a true instant. No bar in the window is an empty answer, not an error.
     *
     * @param broker the broker
     * @param symbol the broker's own name
     * @param timeframe the bar's length
     * @param from the window's start
     * @param to the window's end
     * @param limit at most this many; the platform's default when null
     * @param serverId the server, with terminals on more than one of the broker's; null otherwise
     * @return the bars
     */
    public CompletableFuture<CandleList> candles(UUID broker, String symbol, Timeframe timeframe, OffsetDateTime from, OffsetDateTime to,
            @Nullable Integer limit, @Nullable UUID serverId) {
        Query query = Query.of().add("broker", Objects.requireNonNull(broker, "broker")).add("server_id", serverId)
                .add("symbol", Objects.requireNonNull(symbol, "symbol")).add("tf", Objects.requireNonNull(timeframe, "timeframe").name())
                .add("from", Objects.requireNonNull(from, "from")).add("to", Objects.requireNonNull(to, "to")).add("limit", limit);
        return client.get("/md/candles", query.build(), CandleList.class);
    }

    /**
     * A cTrader account's bars for a window, oldest first, at most 1,000 a call.
     *
     * @param accountId the account
     * @param symbol the account's own name for it
     * @param timeframe the bar's length
     * @param from the window's start
     * @param to the window's end
     * @return the bars
     */
    public CompletableFuture<CandleList> accountCandles(UUID accountId, String symbol, Timeframe timeframe, OffsetDateTime from,
            OffsetDateTime to) {
        return accountCandles(accountId, symbol, timeframe, from, to, null);
    }

    /**
     * A cTrader account's bars for a window, oldest first, at most 1,000 a call: Spotware's trendbars for
     * the account's own symbol name, in UTC, {@code v} the bar's tick volume. When the window holds more
     * than {@code limit} bars the answer is the newest of them. Each read counts against the same
     * per-terminal limit as {@link #candles}. A MetaTrader account is a {@link StateException}
     * ({@code not_on_this_venue}); an account whose runner is not connected is an {@link UpstreamException}.
     *
     * @param accountId the account
     * @param symbol the account's own name for it
     * @param timeframe the bar's length
     * @param from the window's start
     * @param to the window's end
     * @param limit at most this many; the platform's default when null
     * @return the bars
     */
    public CompletableFuture<CandleList> accountCandles(UUID accountId, String symbol, Timeframe timeframe, OffsetDateTime from,
            OffsetDateTime to, @Nullable Integer limit) {
        Query query = Query.of().add("symbol", Objects.requireNonNull(symbol, "symbol")).add("tf", Objects.requireNonNull(timeframe, "timeframe").name())
                .add("from", Objects.requireNonNull(from, "from")).add("to", Objects.requireNonNull(to, "to")).add("limit", limit);
        return client.get("/accounts/" + Objects.requireNonNull(accountId, "accountId") + "/md/candles", query.build(), CandleList.class);
    }

    /**
     * One read of tick history: the first ticks at or after {@code from}, oldest first, at most 1,000.
     *
     * @param broker the broker
     * @param symbol the broker's own name
     * @param from the first moment
     * @return the ticks, with {@code more} when the window holds more
     */
    public CompletableFuture<TickList> ticks(UUID broker, String symbol, OffsetDateTime from) {
        return ticks(broker, symbol, from, null, null, null);
    }

    /**
     * One read of tick history: the first {@code limit} ticks at or after {@code from}, oldest first, read
     * through one of the tenant's ACTIVE terminals on the broker's server. MFX keeps no tick store of its
     * own, so how far back they go is the broker's to say. {@code more} on the answer is true when the window
     * holds ticks beyond the ones returned: {@link #allTicks} follows it.
     *
     * @param broker the broker
     * @param symbol the broker's own name
     * @param from the first moment
     * @param to leaves out ticks after it; none when null
     * @param limit at most this many (1,000 by default)
     * @param serverId the server, with terminals on more than one of the broker's; null otherwise
     * @return the ticks
     */
    public CompletableFuture<TickList> ticks(UUID broker, String symbol, OffsetDateTime from, @Nullable OffsetDateTime to,
            @Nullable Integer limit, @Nullable UUID serverId) {
        Query query = Query.of().add("broker", Objects.requireNonNull(broker, "broker")).add("server_id", serverId)
                .add("symbol", Objects.requireNonNull(symbol, "symbol")).add("from", Objects.requireNonNull(from, "from")).add("to", to)
                .add("limit", limit);
        return client.get("/md/ticks", query.build(), TickList.class);
    }

    /**
     * Every tick of a window from {@code from}, in order, each once.
     *
     * @param broker the broker
     * @param symbol the broker's own name
     * @param from the first moment
     * @return every tick
     */
    public Iterable<Tick> allTicks(UUID broker, String symbol, OffsetDateTime from) {
        return allTicks(broker, symbol, from, null, null, null);
    }

    /**
     * Every tick of a window, in order, each once, a call at a time. The contract's way to continue is to
     * ask again from the last tick's time, and several ticks can share one millisecond, so the next answer
     * begins with ticks already held: this drops exactly those and yields the rest. Every call is a round
     * trip to the terminal: this is for reading history; the stream is the way to follow ticks as they happen.
     * Iterating blocks on each call.
     *
     * @param broker the broker
     * @param symbol the broker's own name
     * @param from the first moment
     * @param to leaves out ticks after it; none when null
     * @param pageSize at most this many a call; the platform's default when null
     * @param serverId the server, with terminals on more than one of the broker's; null otherwise
     * @return every tick
     */
    public Iterable<Tick> allTicks(UUID broker, String symbol, OffsetDateTime from, @Nullable OffsetDateTime to, @Nullable Integer pageSize,
            @Nullable UUID serverId) {
        Objects.requireNonNull(from, "from");
        return () -> new TickWalk(broker, symbol, from, to, pageSize, serverId);
    }

    private record TickKey(@Nullable OffsetDateTime ts, @Nullable String bid, @Nullable String ask, @Nullable String last,
            @Nullable String volume) {
        static TickKey of(Tick tick) {
            return new TickKey(tick.getTs(), tick.getBid(), tick.getAsk(), tick.getLast(), tick.getVolume());
        }
    }

    private final class TickWalk implements Iterator<Tick> {
        private final UUID broker;
        private final String symbol;
        private final @Nullable OffsetDateTime to;
        private final @Nullable Integer pageSize;
        private final @Nullable UUID serverId;
        private OffsetDateTime start;
        private List<TickKey> held = new ArrayList<>();
        private @Nullable Iterator<Tick> current;
        private boolean done;

        TickWalk(UUID broker, String symbol, OffsetDateTime from, @Nullable OffsetDateTime to, @Nullable Integer pageSize,
                @Nullable UUID serverId) {
            this.broker = broker;
            this.symbol = symbol;
            this.start = from;
            this.to = to;
            this.pageSize = pageSize;
            this.serverId = serverId;
        }

        @Override
        public boolean hasNext() {
            while (current == null || !current.hasNext()) {
                if (done) {
                    return false;
                }
                TickList answer;
                try {
                    answer = ticks(broker, symbol, start, to, pageSize, serverId).join();
                } catch (CompletionException failed) {
                    if (failed.getCause() instanceof RuntimeException cause) {
                        throw cause;
                    }
                    throw failed;
                }
                List<Tick> items = answer.getItems() == null ? List.of() : answer.getItems();
                List<Tick> fresh = new ArrayList<>();
                for (Tick tick : items) {
                    TickKey key = TickKey.of(tick);
                    if (tick.getTs() != null && start.isEqual(tick.getTs()) && held.remove(key)) {
                        continue;
                    }
                    fresh.add(tick);
                }
                current = fresh.iterator();
                // Nothing new with more promised is one millisecond holding more ticks than a call
                // returns; asking again would return the same ones for ever.
                if (!Boolean.TRUE.equals(answer.getMore()) || fresh.isEmpty()) {
                    done = true;
                } else {
                    OffsetDateTime last = items.get(items.size() - 1).getTs();
                    start = Objects.requireNonNull(last, "a tick carries its ts");
                    held = new ArrayList<>(items.stream().filter(t -> t.getTs() != null && last.isEqual(t.getTs())).map(TickKey::of).toList());
                }
            }
            return true;
        }

        @Override
        public Tick next() {
            if (!hasNext()) {
                throw new NoSuchElementException("every tick of the window has been read");
            }
            return Objects.requireNonNull(current).next();
        }
    }

    /**
     * A message from a {@code book:} channel as the {@link Book} it is, or null for any other. The first
     * message after a subscribe is the newest book the platform holds; each later one is a change. A gap in
     * {@code seq} is a book superseded before it reached you, not a lost fact.
     *
     * @param received what the stream delivered
     * @return the book; null for any other message
     * @throws IllegalArgumentException a side is not a list of [price, volume], or a field is missing
     */
    public static @Nullable Book bookOf(StreamEvent received) {
        if (!(received instanceof StreamEvent.Message message) || !BOOK.equals(message.type())) {
            return null;
        }
        Map<String, JsonNode> data = message.data();
        EventData d = new EventData(BOOK, data);
        return new Book(message.channel(), message.seq(), side(data.get("bids")), side(data.get("asks")), d.text("broker_ts"),
                (int) d.integer("levels"));
    }

    private static List<Book.Level> side(@Nullable JsonNode raw) {
        if (raw == null || !raw.isArray()) {
            throw new IllegalArgumentException("a book side is a list of [price, volume]");
        }
        List<Book.Level> levels = new ArrayList<>();
        for (JsonNode level : raw) {
            levels.add(new Book.Level(new BigDecimal(text(level.get(0))), new BigDecimal(text(level.get(1)))));
        }
        return levels;
    }

    private static String text(@Nullable JsonNode value) {
        if (value == null) {
            throw new IllegalArgumentException("a book side is a list of [price, volume]");
        }
        return value.isTextual() ? value.textValue() : value.toString();
    }

    /**
     * A message from {@code events} as the subscription-health event it is, or null for any other. A
     * subscription keeps its stream while it is degraded: nothing needs subscribing again when it recovers.
     *
     * @param received what the stream delivered
     * @return a {@link SubscriptionDegraded} or a {@link SubscriptionRecovered}; null for any other message
     * @throws IllegalArgumentException a required field is missing
     */
    public static @Nullable SubscriptionHealth subscriptionEvent(StreamEvent received) {
        if (!(received instanceof StreamEvent.Message message)) {
            return null;
        }
        String kind = message.type();
        if (!DEGRADED.equals(kind) && !RECOVERED.equals(kind)) {
            return null;
        }
        EventData d = new EventData(kind, message.data());
        if (DEGRADED.equals(kind)) {
            return new SubscriptionDegraded(d.uuid("subscription_id"), d.uuid("broker_id"), d.uuid("server_id"), d.text("symbol"),
                    d.text("reason"), d.instant("since"), d.instantOrNull("last_tick_at"));
        }
        return new SubscriptionRecovered(d.uuid("subscription_id"), d.uuid("broker_id"), d.uuid("server_id"), d.text("symbol"),
                d.text("reason"), d.instant("since"), d.instant("recovered_at"), d.instantOrNull("last_tick_at"));
    }
}
