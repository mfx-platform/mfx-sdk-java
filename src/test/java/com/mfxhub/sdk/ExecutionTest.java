package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mfxhub.sdk.models.Deal;
import com.mfxhub.sdk.models.OrderPlace;
import com.mfxhub.sdk.models.OrderPlace.SideEnum;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Execution and the account reads: every command completes with a result and never exceptionally,
 * decimals go exactly, and a final duplicate is read once for the original result.
 */
class ExecutionTest {
    private static final UUID ID = TerminalWritesTest.ID;
    private static final UUID COMMAND = UUID.fromString("0191f0c2-bbbb-7ccc-8ddd-000000000001");
    private static final String ACCEPTED = "{\"command_id\": \"" + COMMAND + "\", \"state\": \"ACCEPTED\", \"duplicate\": false}";
    private static final String SUCCEEDED = "{\"command_id\": \"" + COMMAND + "\", \"state\": \"SUCCEEDED\", \"final\": true, \"result\": {"
            + "\"ok\": true, \"reason\": null, \"retryable\": false, \"mt5_retcode\": 10009, \"comment_raw\": \"Request executed\","
            + " \"tickets\": {\"order\": 30551, \"deal\": 30552, \"position\": 30551}, \"exec_price\": \"1.12180\","
            + " \"exec_volume\": \"0.01\", \"broker_latency_ms\": 41, \"agent_latency_ms\": 3, \"attempts\": 1}}";

    private static MfxClient client(Fakes.Recording transport) {
        return TerminalWritesTest.client(transport);
    }

    private static String body(Fakes.Sent sent) {
        return new String(sent.body(), StandardCharsets.UTF_8);
    }

    private static Fakes.Answer error(int status, String code, String extra, String... headers) {
        return Fakes.answer(status, "{\"error\":{\"code\":\"" + code + "\",\"message\":\"m\"" + extra + "}}", headers);
    }

    /**
     * Given an order with a volume, a limit price written with an exponent, a stop loss and a magic number
     * / When it is placed / Then one POST carries the caller's key and each decimal as plain text with its
     * scale kept (1E+1 is "10"), the order type market unless set, only what was set, and the accepted
     * command; a negative decimal or a blank key is refused before anything is sent, and no execution method
     * takes a double.
     */
    @Test
    void anOrderGoesWithExactDecimalsTheCallersKeyAndOnlyWhatWasSet() {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(202, ACCEPTED), Fakes.answer(202, ACCEPTED));
        try (MfxClient client = client(transport)) {
            ExecutionResult market = client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, new BigDecimal("0.10"), "k-1").join();
            client.execution().placeOrder(ID, "XAUUSD", SideEnum.SELL, new BigDecimal("1E+1"), "k-2", PlaceOrder.builder()
                    .orderType(OrderPlace.OrderTypeEnum.LIMIT).price(new BigDecimal("2.0405E+3")).sl(new BigDecimal("2050.00")).magic(7).build()).join();

            assertEquals("/v1/terminals/" + ID + "/orders", transport.requests.get(0).pathWithQuery());
            assertEquals("k-1", transport.requests.get(0).header("Idempotency-Key"));
            assertEquals("{\"symbol\":\"EURUSD\",\"side\":\"buy\",\"volume\":\"0.10\",\"order_type\":\"market\"}", body(transport.requests.get(0)));
            assertEquals("{\"symbol\":\"XAUUSD\",\"side\":\"sell\",\"volume\":\"10\",\"order_type\":\"limit\",\"price\":\"2040.5\","
                    + "\"sl\":\"2050.00\",\"magic\":7}", body(transport.requests.get(1)));
            assertEquals(CommandState.ACCEPTED, market.state());
            assertEquals(COMMAND, market.commandId());
            assertFalse(market.isFinal());
            assertEquals("k-1", market.idempotencyKey());

            assertThrows(IllegalArgumentException.class,
                    () -> client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, new BigDecimal("-0.01"), "k-3"));
            assertThrows(IllegalArgumentException.class, () -> client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, BigDecimal.ONE, ""));
            assertEquals(2, transport.requests.size());
        }
        List<Method> doubles = Arrays.stream(Execution.class.getMethods())
                .filter(m -> Arrays.asList(m.getParameterTypes()).contains(double.class) || Arrays.asList(m.getParameterTypes()).contains(Double.class))
                .toList();
        assertEquals(List.of(), doubles);
    }

    /**
     * Given an order comment of exactly 31 emoji (31 code points, 62 Java chars) / When the order is placed
     * / Then it is sent whole, in one POST: the limit counts characters as the contract's maxLength does,
     * not UTF-16 chars.
     */
    @Test
    void aCommentOfThirtyOneCodePointsIsSentWhole() throws IOException {
        String comment = "\uD83D\uDE00".repeat(Execution.COMMENT_MAX);
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(202, ACCEPTED));
        try (MfxClient client = client(transport)) {
            client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, new BigDecimal("0.01"), "k-c31",
                    PlaceOrder.builder().comment(comment).build()).join();

            assertEquals(62, comment.length());
            assertEquals(1, transport.requests.size());
            assertEquals(comment, MfxJson.MAPPER.readTree(body(transport.requests.get(0))).path("comment").asText());
        }
    }

    /**
     * Given an order comment of 32 emoji (32 code points) / When the order is placed / Then it throws
     * IllegalArgumentException naming the limit and the count given, before any request is sent, rather
     * than letting MT5 cut it.
     */
    @Test
    void aCommentOfThirtyTwoCodePointsIsRefusedBeforeAnyRequest() {
        String comment = "\uD83D\uDE00".repeat(Execution.COMMENT_MAX + 1);
        Fakes.Recording transport = new Fakes.Recording();
        try (MfxClient client = client(transport)) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, new BigDecimal("0.01"), "k-c32",
                            PlaceOrder.builder().comment(comment).build()));

            assertEquals("comment is at most 31 characters; the terminal would cut a longer one (32 given)", refused.getMessage());
            assertEquals(0, transport.requests.size());
        }
    }

    /**
     * Given a full close and a partial close each with a comment, and each without one / When they are sent
     * / Then the comment goes in the body as given (31 emoji whole, beside the deviation), and a close without
     * one sends no comment field at all, so the closing deal takes the position's own comment.
     */
    @Test
    void aCloseCarriesItsCommentAndNoneWhenNotGiven() throws IOException {
        String emoji = "\uD83D\uDE00".repeat(Execution.COMMENT_MAX);
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(202, ACCEPTED), Fakes.answer(202, ACCEPTED), Fakes.answer(202, ACCEPTED),
                Fakes.answer(202, ACCEPTED), Fakes.answer(202, ACCEPTED));
        try (MfxClient client = client(transport)) {
            client.execution().closePosition(ID, 7, null, "mine", "c-1").join();
            client.execution().closePosition(ID, 7, 30, emoji, "c-2").join();
            client.execution().closePositionPartial(ID, 7, new BigDecimal("0.10"), null, "part", "c-3").join();
            client.execution().closePosition(ID, 7, 30, null, "c-4").join();
            client.execution().closePositionPartial(ID, 7, new BigDecimal("0.10"), 5, null, "c-5").join();

            assertEquals("/v1/terminals/" + ID + "/positions/7/close", transport.requests.get(0).pathWithQuery());
            assertEquals("{\"comment\":\"mine\"}", body(transport.requests.get(0)));
            JsonNode whole = MfxJson.MAPPER.readTree(body(transport.requests.get(1)));
            assertEquals(30, whole.path("deviation_points").asInt());
            assertEquals(emoji, whole.path("comment").asText());
            assertEquals("/v1/terminals/" + ID + "/positions/7/close-partial", transport.requests.get(2).pathWithQuery());
            assertEquals("{\"volume\":\"0.10\",\"comment\":\"part\"}", body(transport.requests.get(2)));
            assertEquals("{\"deviation_points\":30}", body(transport.requests.get(3)));
            assertEquals("{\"volume\":\"0.10\",\"deviation_points\":5}", body(transport.requests.get(4)));
        }
    }

    /**
     * Given a full close and a partial close each with a comment of 32 emoji (32 code points) / When each
     * is sent / Then each throws the same IllegalArgumentException as placeOrder, before any request.
     */
    @Test
    void aCloseCommentOverThirtyOneCodePointsIsRefusedBeforeAnyRequest() {
        String comment = "\uD83D\uDE00".repeat(Execution.COMMENT_MAX + 1);
        Fakes.Recording transport = new Fakes.Recording();
        try (MfxClient client = client(transport)) {
            IllegalArgumentException full = assertThrows(IllegalArgumentException.class,
                    () -> client.execution().closePosition(ID, 7, null, comment, "c-6"));
            IllegalArgumentException part = assertThrows(IllegalArgumentException.class,
                    () -> client.execution().closePositionPartial(ID, 7, new BigDecimal("0.10"), null, comment, "c-7"));

            assertEquals("comment is at most 31 characters; the terminal would cut a longer one (32 given)", full.getMessage());
            assertEquals(full.getMessage(), part.getMessage());
            assertEquals(0, transport.requests.size());
        }
    }

    /**
     * Given a 409, a 429, a 403 maintenance refusal, a dropped connection, a connection that never opened
     * and a timeout / When an order meets each / Then each is a result that is not ok and the future never
     * fails: refused, rate limited (retryable, sent once), maintenance (retryable at the window's end),
     * UNKNOWN for the dropped connection (never retryable under a new key), retryable for the one that
     * never opened, and a timeout that is never retryable.
     */
    @Test
    void everyFailureOfAnOrderIsAResultAndNoneIsRetriedBehindTheCaller() {
        Fakes.Recording transport = new Fakes.Recording(error(409, "state_error", ""), error(429, "rate_limited", "", "Retry-After", "3"),
                error(403, "maintenance", ",\"details\":{\"window_id\":\"w-1\",\"ends_at\":\"2026-11-17T12:00:00Z\"}"),
                Fakes.failing(new IOException("connection reset")), Fakes.failing(new ConnectException("refused")),
                Fakes.hanging());
        MfxClient client = MfxClient.builder(Fakes.BASE, "mfxk_1", Fakes.key()).transport(transport).timeout(Duration.ofMillis(200))
                .pause(wait -> CompletableFuture.completedFuture(null)).build();
        try (client) {
            List<ExecutionResult> results = List.of(
                    client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, BigDecimal.ONE, "a").join(),
                    client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, BigDecimal.ONE, "b").join(),
                    client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, BigDecimal.ONE, "c").join(),
                    client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, BigDecimal.ONE, "d").join(),
                    client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, BigDecimal.ONE, "e").join(),
                    client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, BigDecimal.ONE, "f").join());

            assertTrue(results.stream().noneMatch(ExecutionResult::ok));
            assertEquals(List.of(Reason.REFUSED, Reason.RATE_LIMITED, Reason.MAINTENANCE, Reason.TRANSPORT_ERROR, Reason.TRANSPORT_ERROR,
                    Reason.TIMEOUT), results.stream().map(ExecutionResult::reason).toList());
            assertEquals(List.of(false, true, true, false, true, false), results.stream().map(ExecutionResult::retryable).toList());
            assertTrue(results.get(0).detail().startsWith("state_error (409)"));
            assertEquals(OffsetDateTime.of(2026, 11, 17, 12, 0, 0, 0, ZoneOffset.UTC), results.get(2).retryAt());
            assertEquals(CommandState.UNKNOWN, results.get(3).state());
            assertNull(results.get(4).state());
            assertEquals(6, transport.requests.size());
            assertEquals("d", results.get(3).idempotencyKey());
        }
    }

    /**
     * Given a repeat whose command is already final, the same with the read failing, one still in flight,
     * and a reason this version does not know / When each is answered / Then the final repeat is read once
     * and returned as the original result in full (exact price, tickets) marked duplicate; a failed read
     * keeps what the platform said, ok from its state, with a detail saying how to read the rest; one in
     * flight costs no extra read; and the new reason is kept as its text.
     */
    @Test
    void aFinalDuplicateIsReadOnceForTheOriginalAndANewReasonIsKeptAsText() {
        String finalDuplicate = "{\"command_id\": \"" + COMMAND + "\", \"state\": \"SUCCEEDED\", \"duplicate\": true}";
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(202, finalDuplicate), Fakes.answer(200, SUCCEEDED),
                Fakes.answer(202, finalDuplicate), error(503, "dependency_unavailable", ""),
                Fakes.answer(202, "{\"command_id\": \"" + COMMAND + "\", \"state\": \"EXECUTING\", \"duplicate\": true}"),
                Fakes.answer(200, "{\"command_id\": \"" + COMMAND + "\", \"state\": \"FAILED\", \"final\": true, \"result\": {\"ok\": false,"
                        + " \"reason\": \"venue_on_fire\", \"retryable\": true, \"venue\": \"ctrader\", \"deals\": [{\"deal\": 9, \"volume_units\": \"1000\"}, {\"x\": 1}]}}"));
        try (MfxClient client = client(transport)) {
            ExecutionResult original = client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, new BigDecimal("0.01"), "dup").join();
            ExecutionResult unread = client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, new BigDecimal("0.01"), "dup-2").join();
            ExecutionResult inFlight = client.execution().placeOrder(ID, "EURUSD", SideEnum.BUY, new BigDecimal("0.01"), "dup-3").join();
            ExecutionResult unknownReason = client.execution().getCommand(COMMAND).join();

            assertTrue(original.ok());
            assertTrue(original.duplicate());
            assertEquals(new BigDecimal("1.12180"), original.execPrice());
            assertEquals("1.12180", original.execPrice().toPlainString());
            assertEquals(30551L, original.tickets().position());
            assertEquals("Request executed", original.commentRaw());
            assertEquals("dup", original.idempotencyKey());
            assertEquals("/v1/commands/" + COMMAND, transport.requests.get(1).pathWithQuery());

            assertTrue(unread.ok());
            assertEquals(CommandState.SUCCEEDED, unread.state());
            assertTrue(unread.duplicate());
            assertNull(unread.execPrice());
            assertTrue(unread.detail().contains("getCommand(" + COMMAND + ")"));

            assertEquals(CommandState.EXECUTING, inFlight.state());
            assertFalse(inFlight.ok());
            assertNull(unknownReason.reason());
            assertEquals("venue_on_fire", unknownReason.reasonText());
            assertTrue(unknownReason.retryable());
            assertEquals("ctrader", unknownReason.venue());
            assertEquals(1, unknownReason.deals().size());
            assertEquals(new BigDecimal("1000"), unknownReason.deals().get(0).volumeUnits());
            assertEquals(6, transport.requests.size());
        }
    }

    /**
     * Given a modify, a close, a close-by, a partial close and a trailing stop set and cleared / When each
     * is sent / Then each goes to its own path with its body (an order's expiry left out to keep it, null
     * for good-till-cancelled), a modify that changes nothing is refused here, and waitFor reads the command
     * again after 200 ms, doubling, until it is final.
     */
    @Test
    void everyCommandGoesToItsPathAndWaitForReadsUntilFinal() {
        Fakes.Answer accepted = Fakes.answer(202, ACCEPTED);
        Fakes.Recording transport = new Fakes.Recording(accepted, accepted, accepted, accepted, accepted, accepted, accepted, accepted,
                Fakes.answer(200, ACCEPTED), Fakes.answer(200, "{\"command_id\": \"" + COMMAND + "\", \"state\": \"EXECUTING\"}"),
                Fakes.answer(200, SUCCEEDED));
        try (MfxClient client = client(transport)) {
            Execution execution = client.execution();
            execution.modifyPosition(ID, 7, "m1", ModifyPosition.builder().sl(new BigDecimal("0")).build()).join();
            execution.modifyOrder(ID, 8, "m2", ModifyOrder.builder().price(new BigDecimal("1.1000")).build()).join();
            execution.modifyOrder(ID, 8, "m3", ModifyOrder.builder().goodTillCancelled().build()).join();
            execution.closePosition(ID, 7, "c1").join();
            execution.closePositionBy(ID, 7, 9, "c2").join();
            execution.closePositionPartial(ID, 7, new BigDecimal("0.05"), 10, "c3").join();
            execution.setTrailingStop(ID, 7, new BigDecimal("0.0020"), "t1").join();
            execution.clearTrailingStop(ID, 7, "t2").join();
            ExecutionResult done = execution.waitFor(COMMAND).join();

            List<String> sent = transport.requests.subList(0, 8).stream().map(r -> r.method() + " " + r.pathWithQuery().substring(("/v1/terminals/" + ID).length())
                    + " " + body(r)).toList();
            assertEquals(List.of("POST /positions/7/modify {\"sl\":\"0\"}", "POST /orders/8/modify {\"price\":\"1.1000\"}",
                    "POST /orders/8/modify {\"expires_at\":null}", "POST /positions/7/close {}", "POST /positions/7/close-by {\"by_ticket\":9}",
                    "POST /positions/7/close-partial {\"volume\":\"0.05\",\"deviation_points\":10}",
                    "PUT /positions/7/trailing-stop {\"distance\":\"0.0020\"}", "DELETE /positions/7/trailing-stop "), sent);
            assertTrue(done.isFinal());
            assertTrue(done.ok());
            assertEquals(List.of(Duration.ofMillis(200), Duration.ofMillis(400)), TerminalWritesTest.WAITS);
            assertThrows(IllegalArgumentException.class, () -> execution.modifyPosition(ID, 7, "m4", ModifyPosition.builder().build()));
            assertThrows(IllegalArgumentException.class, () -> execution.modifyOrder(ID, 8, "m5", ModifyOrder.builder().build()));
        }
    }

    /**
     * Given a terminal's account, positions, pending orders, spread and a window of deals / When each is
     * read / Then each is one GET of its own path, the window goes as ISO 8601 with its seconds and
     * offset, the deals come back as a page with its cursor, and a decimal on a deal stays its text.
     */
    @Test
    void theAccountIsReadAsSnapshotsAndTheDealsAsAWindowedPage() {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(200, "{}"), Fakes.answer(200, "{\"items\": []}"),
                Fakes.answer(200, "{\"items\": []}"), Fakes.answer(200, "{\"items\": []}"),
                Fakes.answer(200, "{\"items\": [{\"ticket\": 30552, \"price\": \"1.12180\"}], \"next_cursor\": \"d-2\"}"));
        try (MfxClient client = client(transport)) {
            client.accounts().account(ID).join();
            client.accounts().positions(ID).join();
            client.accounts().pendingOrders(ID).join();
            client.accounts().spread(ID).join();
            Page<Deal> deals = client.accounts().deals(ID, OffsetDateTime.of(2026, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                    OffsetDateTime.of(2026, 11, 17, 9, 0, 0, 0, ZoneOffset.UTC), null, 50).join();

            String base = "/v1/terminals/" + ID;
            assertEquals(List.of(base + "/account", base + "/positions", base + "/orders/pending", base + "/spread",
                    base + "/history/deals?from=2026-11-01T00%3A00%3A00%2B00%3A00&to=2026-11-17T09%3A00%3A00%2B00%3A00&limit=50"),
                    transport.requests.stream().map(Fakes.Sent::pathWithQuery).toList());
            assertEquals("d-2", deals.nextCursor());
            JsonNode deal = MfxJson.MAPPER.valueToTree(deals.get(0));
            assertEquals("1.12180", deal.get("price").asText());
        }
    }
}
