package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.mfxhub.sdk.models.CandleList;
import com.mfxhub.sdk.models.Tick;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Market data and the typed stream events.
 */
class MarketDataTest {
    private static final UUID SERVER = TerminalWritesTest.SERVER;
    private static final UUID BROKER = TerminalWritesTest.BROKER;
    private static final UUID ACCOUNT = UUID.fromString("4c2d3e4f-5a6b-7c8d-9e0f-102030405060");
    private static final OffsetDateTime FROM = OffsetDateTime.of(2026, 11, 12, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime TO = OffsetDateTime.of(2026, 11, 12, 11, 0, 0, 0, ZoneOffset.UTC);

    private static Fakes.Answer ok(String body) {
        return Fakes.answer(200, body);
    }

    static StreamEvent.Message message(String channel, String type, String data) {
        try {
            Map<String, JsonNode> fields = MfxJson.MAPPER.readValue(data, new TypeReference<Map<String, JsonNode>>() { });
            return new StreamEvent.Message(channel, 3L, type, fields, null);
        } catch (java.io.IOException bad) {
            throw new IllegalArgumentException(bad);
        }
    }

    /**
     * Given a server symbol subscribed with candles, a cTrader account symbol subscribed, both listed and
     * one stopped / When each is sent / Then the subscription posts the broker's name, its timeframes in the
     * contract's words and delivery ws, with the caller's key; the account's posts its symbol alone; the
     * lists follow the cursor; and the stop is a DELETE with its key.
     */
    @Test
    void aSymbolIsSubscribedOnAServerOrACtraderAccountListedAndStopped() {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(201, "{\"id\": \"" + SERVER + "\"}"),
                Fakes.answer(201, "{\"id\": \"" + SERVER + "\", \"state\": \"pending\"}"),
                ok("{\"items\": [{\"symbol\": \"EURUSD\"}], \"next_cursor\": \"c-2\"}"), ok("{\"items\": [{\"symbol\": \"GBPUSD\"}]}"),
                ok("{\"items\": [{\"symbol\": \"XAUUSD\"}]}"), Fakes.answer(204, ""));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            client.md().subscribe(SERVER, "EURUSD.sd", "s-1", List.of(Timeframe.M1, Timeframe.H4)).join();
            client.md().subscribeAccount(ACCOUNT, "EURUSD", "s-2").join();
            List<String> symbols = new ArrayList<>();
            client.md().allAccountSubscriptions(ACCOUNT).forEach(s -> symbols.add(s.getSymbol()));
            client.md().subscriptions().join();
            client.md().unsubscribe(SERVER, "s-3").join();

            assertEquals("{\"server_id\":\"" + SERVER + "\",\"symbol\":\"EURUSD.sd\",\"timeframes\":[\"M1\",\"H4\"],\"delivery\":\"ws\"}",
                    new String(transport.requests.get(0).body(), StandardCharsets.UTF_8));
            assertEquals("s-1", transport.requests.get(0).header("Idempotency-Key"));
            assertEquals("/v1/accounts/" + ACCOUNT + "/md/subscriptions", transport.requests.get(1).pathWithQuery());
            assertEquals("{\"symbol\":\"EURUSD\"}", new String(transport.requests.get(1).body(), StandardCharsets.UTF_8));
            assertEquals(List.of("EURUSD", "GBPUSD"), symbols);
            assertEquals("/v1/accounts/" + ACCOUNT + "/md/subscriptions?cursor=c-2", transport.requests.get(3).pathWithQuery());
            assertEquals("DELETE /v1/md/subscriptions/" + SERVER, transport.requests.get(5).method() + " " + transport.requests.get(5).pathWithQuery());
            assertEquals("s-3", transport.requests.get(5).header("Idempotency-Key"));
        }
    }

    /**
     * Given a broker's candles and ticks on a named server and an account's candles / When each is read /
     * Then each is one GET with its query in the contract's names and order (broker, server_id, symbol, tf,
     * from, to, limit), the window as ISO 8601, and the bars' prices as the exact text the API wrote.
     */
    @Test
    void candlesAndTicksAreReadWithTheContractsQuery() {
        Fakes.Recording transport = new Fakes.Recording(ok("{\"items\": [{\"ts\": \"2026-11-12T09:00:00Z\", \"o\": \"1.15800\", \"c\": \"1.1590\"}]}"),
                ok("{\"items\": []}"), ok("{\"items\": [], \"more\": false}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            CandleList bars = client.md().candles(BROKER, "EURUSD", Timeframe.H1, FROM, TO, 500, SERVER).join();
            client.md().accountCandles(ACCOUNT, "EURUSD", Timeframe.D1, FROM, TO).join();
            client.md().ticks(BROKER, "EURUSD", FROM, null, 100, null).join();

            assertEquals("/v1/md/candles?broker=" + BROKER + "&server_id=" + SERVER + "&symbol=EURUSD&tf=H1&from=2026-11-12T09%3A00%3A00%2B00%3A00"
                    + "&to=2026-11-12T11%3A00%3A00%2B00%3A00&limit=500", transport.requests.get(0).pathWithQuery());
            assertEquals("1.15800", bars.getItems().get(0).getO());
            assertEquals("/v1/accounts/" + ACCOUNT + "/md/candles?symbol=EURUSD&tf=D1&from=2026-11-12T09%3A00%3A00%2B00%3A00&to=2026-11-12T11%3A00%3A00%2B00%3A00",
                    transport.requests.get(1).pathWithQuery());
            assertEquals("/v1/md/ticks?broker=" + BROKER + "&symbol=EURUSD&from=2026-11-12T09%3A00%3A00%2B00%3A00&limit=100",
                    transport.requests.get(2).pathWithQuery());
        }
    }

    /**
     * Given lists of enums on the models (a farm's usages, a window's suspended services, a subscription's
     * timeframes), an enum the contract writes as a number, and instants with and without a fraction / When
     * they are written and read back / Then a list of enums goes as the contract's words and reads back as
     * the same constants, the numeric enum stays a number, and an instant is written in ISO 8601:
     * seconds always, six digits of fraction when there is one, the offset as +00:00.
     */
    @Test
    void enumListsAndInstantsAreWrittenAsTheContractWritesThem() throws Exception {
        com.mfxhub.sdk.models.TenantFarm farm = MfxJson.MAPPER.readValue("{\"usages\": [\"order_execution\", \"quote_streaming\"]}",
                com.mfxhub.sdk.models.TenantFarm.class);
        JsonNode written = MfxJson.MAPPER.valueToTree(farm);
        assertEquals("[\"order_execution\",\"quote_streaming\"]", written.get("usages").toString());
        assertEquals(farm.getUsages(), MfxJson.MAPPER.treeToValue(written, com.mfxhub.sdk.models.TenantFarm.class).getUsages());
        com.mfxhub.sdk.models.TenantMaintenance window = MfxJson.MAPPER.readValue("{\"suspended\": [\"order_placement\"]}",
                com.mfxhub.sdk.models.TenantMaintenance.class);
        assertEquals("[\"order_placement\"]", MfxJson.MAPPER.valueToTree(window).get("suspended").toString());
        com.mfxhub.sdk.models.TenantTerminal terminal = MfxJson.MAPPER.readValue("{\"refresh_ms\": 4000}", com.mfxhub.sdk.models.TenantTerminal.class);
        assertTrue(MfxJson.MAPPER.valueToTree(terminal).get("refresh_ms").isNumber());

        assertEquals("2026-11-12T09:00:00+00:00", Times.iso(FROM));
        assertEquals("2026-11-12T09:00:00.120000+04:00", Times.iso(OffsetDateTime.of(2026, 11, 12, 9, 0, 0, 120_456_789, ZoneOffset.ofHours(4))
                .withNano(120_000_000)));
        assertEquals("2026-11-12T09:00:00.120456+00:00", Times.iso(FROM.withNano(120_456_789)));
    }

    /**
     * Given a window whose ticks span two calls with two ticks sharing the millisecond at the seam, and a
     * millisecond holding more ticks than a call returns / When every tick is read / Then each tick comes
     * once and in order, the next call starts at the last tick's time, and a call with nothing new ends the
     * walk rather than asking for the same ticks for ever.
     */
    @Test
    void everyTickOfAWindowIsReadOnceAcrossTheSeams() {
        String t1 = "{\"ts\": \"2026-11-12T09:00:00.001Z\", \"bid\": \"1.1\", \"ask\": \"1.2\"}";
        String t2 = "{\"ts\": \"2026-11-12T09:00:00.002Z\", \"bid\": \"1.1\", \"ask\": \"1.2\"}";
        String t3 = "{\"ts\": \"2026-11-12T09:00:00.002Z\", \"bid\": \"1.0\", \"ask\": \"1.2\"}";
        String t4 = "{\"ts\": \"2026-11-12T09:00:00.003Z\", \"bid\": \"1.3\", \"ask\": \"1.4\"}";
        Fakes.Recording transport = new Fakes.Recording(ok("{\"items\": [" + t1 + "," + t2 + "], \"more\": true}"),
                ok("{\"items\": [" + t2 + "," + t3 + "," + t4 + "], \"more\": false}"),
                ok("{\"items\": [" + t2 + "], \"more\": true}"), ok("{\"items\": [" + t2 + "], \"more\": true}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            List<String> read = new ArrayList<>();
            for (Tick tick : client.md().allTicks(BROKER, "EURUSD", FROM, null, 2, null)) {
                read.add(tick.getTs().getNano() / 1_000_000 + ":" + tick.getBid());
            }
            assertEquals(List.of("1:1.1", "2:1.1", "2:1.0", "3:1.3"), read);
            assertEquals("/v1/md/ticks?broker=" + BROKER + "&symbol=EURUSD&from=2026-11-12T09%3A00%3A00.002000%2B00%3A00&limit=2",
                    transport.requests.get(1).pathWithQuery());

            List<Tick> stuck = new ArrayList<>();
            client.md().allTicks(BROKER, "EURUSD", FROM).forEach(stuck::add);
            assertEquals(1, stuck.size());
            assertEquals(4, transport.requests.size());
        }
    }

    /**
     * Given order, position and terminal messages whose values arrive as text, a candle on its channel with
     * no type, a message of another type, and one missing a field the contract requires / When each is read
     * as an event / Then each is its typed record with exact decimals, whole tickets and instants, a null for
     * an optional field the platform left out, the candle read by its channel, null for the other type, and
     * the missing field named.
     */
    @Test
    void aStreamMessageIsReadAsItsTypedEvent() {
        String position = "{\"schema_version\": \"1\", \"terminal_id\": \"" + TerminalWritesTest.ID + "\", \"event_id\": \"e-1\", \"ticket\": \"30551\","
                + " \"symbol\": \"EURUSD\", \"side\": \"buy\", \"volume\": \"0.10\", \"open_price\": \"1.10450\", \"open_time\":"
                + " \"2026-11-12T09:00:00Z\", \"magic\": \"7\", \"comment\": \"\", \"ts\": \"2026-11-12T09:00:00.120Z\", \"order_ticket\": \"30550\"}";
        Event opened = Events.eventOf(message("events", "position.opened", position));
        PositionOpened open = assertInstanceOf(PositionOpened.class, opened);
        assertEquals(30551L, open.ticket());
        assertEquals(new BigDecimal("1.10450"), open.openPrice());
        assertEquals("1.10450", open.openPrice().toPlainString());
        assertNull(open.sl());
        assertNull(open.idempotencyKey());
        assertEquals(30550L, open.orderTicket());
        assertEquals("position.opened", open.kind());

        TerminalStateChanged moved = assertInstanceOf(TerminalStateChanged.class, Events.eventOf(message("events", "terminal.state_changed",
                "{\"schema_version\": \"1\", \"terminal_id\": \"" + TerminalWritesTest.ID + "\", \"from\": \"ACTIVE\", \"to\": \"OFFLINE\","
                        + " \"reason\": \"cell_silent\", \"transition_id\": \"" + SERVER + "\", \"ts\": \"2026-11-12T09:00:00Z\"}")));
        assertEquals("OFFLINE", moved.toState());

        CandleClosed bar = assertInstanceOf(CandleClosed.class, Events.eventOf(message(Channel.candles(SERVER, "EURUSD", "H1"), null,
                "{\"schema_version\": \"1\", \"server_id\": \"" + SERVER + "\", \"symbol\": \"EURUSD\", \"timeframe\": \"H1\", \"ts\": \"2026-11-12T09:00:00Z\","
                        + " \"o\": \"1.1\", \"h\": \"1.2\", \"l\": \"1.0\", \"c\": \"1.15\", \"v\": \"812\", \"prev_ts\": \"2026-11-12T08:00:00Z\"}")));
        assertEquals(812L, bar.v());
        assertEquals(new BigDecimal("1.15"), bar.c());

        assertNull(Events.eventOf(message("events", "maintenance.window_changed", "{}")));
        assertNull(Events.eventOf(message("events", null, "{}")));
        assertNull(Events.eventOf(new StreamEvent.Resync("events")));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> Events.eventOf(message("events", "position.closed", position)));
        assertTrue(missing.getMessage().contains("`close_reason`"), missing.getMessage());
        assertTrue(Events.EVENT_KINDS.contains("order.closed"));
    }

    /**
     * Given a book message, a degraded and a recovered subscription on events, and other messages / When
     * each is read / Then the book has both sides as exact (price, volume) levels with its best bid and ask,
     * the health messages are their typed records with their instants, and any other message is null; a side
     * that is not a list is refused, naming the rule.
     */
    @Test
    void aBookAndASubscriptionsHealthAreReadAsWhatTheyAre() {
        Book book = MarketData.bookOf(message(Channel.book(SERVER, "XAUUSD"), MarketData.BOOK,
                "{\"bids\": [[\"2040.10\", \"1.5\"], [\"2040.00\", \"3\"]], \"asks\": [[\"2040.30\", \"2\"]], \"broker_ts\": \"2026.11.12 11:00:00.123\", \"levels\": 10}"));
        assertEquals(new BigDecimal("2040.10"), book.bestBid());
        assertEquals("2040.30", book.bestAsk().toPlainString());
        assertEquals(2, book.bids().size());
        assertEquals(new BigDecimal("3"), book.bids().get(1).volume());
        assertEquals(10, book.levels());
        assertNull(new Book("book:x", null, List.of(), List.of(), "", 0).bestBid());
        assertNull(MarketData.bookOf(message("events", "md.tick", "{}")));
        assertThrows(IllegalArgumentException.class, () -> MarketData.bookOf(message("book:x", MarketData.BOOK, "{\"bids\": 1, \"asks\": []}")));

        String health = "{\"subscription_id\": \"" + SERVER + "\", \"broker_id\": \"" + BROKER + "\", \"server_id\": \"" + SERVER + "\","
                + " \"symbol\": \"EURUSD\", \"reason\": \"report_stale\", \"since\": \"2026-11-12T09:00:00Z\", \"recovered_at\": \"2026-11-12T09:05:00Z\"}";
        SubscriptionDegraded degraded = assertInstanceOf(SubscriptionDegraded.class,
                MarketData.subscriptionEvent(message("events", MarketData.DEGRADED, health)));
        assertEquals("report_stale", degraded.reason());
        assertNull(degraded.lastTickAt());
        SubscriptionRecovered recovered = assertInstanceOf(SubscriptionRecovered.class,
                MarketData.subscriptionEvent(message("events", MarketData.RECOVERED, health)));
        assertEquals(OffsetDateTime.of(2026, 11, 12, 9, 5, 0, 0, ZoneOffset.UTC), recovered.recoveredAt());
        assertNull(MarketData.subscriptionEvent(message("events", "order.placed", health)));
        assertFalse(MarketData.subscriptionEvent(message("events", MarketData.DEGRADED, health)) instanceof SubscriptionRecovered);
    }
}
