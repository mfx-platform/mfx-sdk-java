package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mfxhub.sdk.models.CtraderGrantCreate;
import com.mfxhub.sdk.models.OrderPlace.SideEnum;
import com.mfxhub.sdk.models.TradingAccount;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The trading accounts over every venue. */
class TradingAccountsTest {
    private static final UUID ACCOUNT = UUID.fromString("0191f0c2-cccc-7ddd-8eee-000000000001");
    private static final UUID GRANT = UUID.fromString("0191f0c2-cccc-7ddd-8eee-0000000000aa");

    private static Fakes.Answer ok(String body) {
        return Fakes.answer(200, body);
    }

    /**
     * Given venues, an environment and states to list by, and an account's reads / When they are read /
     * Then the filters go as the contract reads them (venues and states each one comma-separated value),
     * every account is listed by cursor, and each read is a GET of the account's own path, the deals
     * narrowed to one position inside their window.
     */
    @Test
    void theAccountsAreListedByVenueEnvironmentAndStateAndEachIsRead() {
        Fakes.Recording transport = new Fakes.Recording(
                ok("{\"items\": [{\"id\": \"" + ACCOUNT + "\", \"venue\": \"ctrader\", \"state\": \"CONNECTED\"}], \"next_cursor\": \"a-2\"}"),
                ok("{\"items\": []}"), ok("{\"id\": \"" + ACCOUNT + "\", \"venue\": \"ctrader\"}"), ok("{}"), ok("{\"items\": []}"),
                ok("{\"items\": []}"), ok("{\"items\": [{\"symbol\": \"EURUSD\", \"volume_step\": \"0.01\"}]}"), ok("{\"items\": []}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            ListTradingAccounts filter = ListTradingAccounts.builder().venue(TradingAccount.VenueEnum.CTRADER, TradingAccount.VenueEnum.MT5)
                    .environment(TradingAccount.EnvironmentEnum.DEMO).state(TradingAccount.StateEnum.CONNECTED).limit(1).build();
            List<UUID> ids = new java.util.ArrayList<>();
            client.tradingAccounts().list(filter).forEach(a -> ids.add(a.getId()));
            TradingAccount one = client.tradingAccounts().get(ACCOUNT).join();
            client.tradingAccounts().funds(ACCOUNT).join();
            client.tradingAccounts().positions(ACCOUNT).join();
            client.tradingAccounts().pendingOrders(ACCOUNT).join();
            client.tradingAccounts().symbols(ACCOUNT).join();
            client.tradingAccounts().deals(ACCOUNT, OffsetDateTime.of(2026, 11, 10, 0, 0, 0, 0, ZoneOffset.UTC),
                    OffsetDateTime.of(2026, 11, 17, 0, 0, 0, 0, ZoneOffset.UTC), 30551L, null, null).join();

            assertEquals(List.of(ACCOUNT), ids);
            assertEquals(TradingAccount.VenueEnum.CTRADER, one.getVenue());
            String base = "/v1/accounts/" + ACCOUNT;
            assertEquals(List.of("/v1/accounts?limit=1&venue=ctrader%2Cmt5&environment=demo&state=CONNECTED",
                    "/v1/accounts?cursor=a-2&limit=1&venue=ctrader%2Cmt5&environment=demo&state=CONNECTED", base, base + "/account",
                    base + "/positions", base + "/orders/pending", base + "/symbols",
                    base + "/history/deals?from=2026-11-10T00%3A00%3A00%2B00%3A00&to=2026-11-17T00%3A00%3A00%2B00%3A00&position=30551"),
                    transport.requests.stream().map(Fakes.Sent::pathWithQuery).toList());
        }
    }

    /**
     * Given a full and a partial close on a trading account, with a comment and without / When they are
     * sent / Then each goes to the account's own route with the comment as given, and none when not given.
     */
    @Test
    void aCloseOnAnAccountCarriesItsCommentAndNoneWhenNotGiven() {
        String accepted = "{\"command_id\": \"0191f0c2-bbbb-7ccc-8ddd-000000000001\", \"state\": \"ACCEPTED\"}";
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(202, accepted), Fakes.answer(202, accepted), Fakes.answer(202, accepted));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            client.tradingAccounts().closePosition(ACCOUNT, 7, 10, "mine", "a-1").join();
            client.tradingAccounts().closePositionPartial(ACCOUNT, 7, new BigDecimal("0.01"), null, "part", "a-2").join();
            client.tradingAccounts().closePosition(ACCOUNT, 7, null, null, "a-3").join();

            String base = "/v1/accounts/" + ACCOUNT;
            assertEquals(List.of(base + "/positions/7/close", base + "/positions/7/close-partial", base + "/positions/7/close"),
                    transport.requests.stream().map(Fakes.Sent::pathWithQuery).toList());
            assertEquals("{\"deviation_points\":10,\"comment\":\"mine\"}", new String(transport.requests.get(0).body(), StandardCharsets.UTF_8));
            assertEquals("{\"volume\":\"0.01\",\"comment\":\"part\"}", new String(transport.requests.get(1).body(), StandardCharsets.UTF_8));
            assertEquals("{}", new String(transport.requests.get(2).body(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Given a full and a partial close on a trading account with a comment of 32 emoji / When each is sent /
     * Then each throws the placeOrder IllegalArgumentException before any request.
     */
    @Test
    void aCloseCommentOnAnAccountOverThirtyOneCodePointsIsRefusedBeforeAnyRequest() {
        String comment = "\uD83D\uDE00".repeat(Execution.COMMENT_MAX + 1);
        Fakes.Recording transport = new Fakes.Recording();
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            IllegalArgumentException full = assertThrows(IllegalArgumentException.class,
                    () -> client.tradingAccounts().closePosition(ACCOUNT, 7, null, comment, "a-4"));
            IllegalArgumentException part = assertThrows(IllegalArgumentException.class,
                    () -> client.tradingAccounts().closePositionPartial(ACCOUNT, 7, new BigDecimal("0.01"), null, comment, "a-5"));

            assertEquals("comment is at most 31 characters; the terminal would cut a longer one (32 given)", full.getMessage());
            assertEquals(full.getMessage(), part.getMessage());
            assertEquals(0, transport.requests.size());
        }
    }

    /**
     * Given an order, a close-by and a trailing stop on a trading account / When each is sent / Then each
     * goes to the account's own route under /accounts with the body client.execution sends and the caller's
     * key, and a venue's refusal comes back as a result, never as an error.
     */
    @Test
    void theCommandsOnAnAccountGoToItsRoutesAndCompleteWithResults() {
        String accepted = "{\"command_id\": \"0191f0c2-bbbb-7ccc-8ddd-000000000001\", \"state\": \"ACCEPTED\"}";
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(202, accepted),
                Fakes.answer(200, "{\"command_id\": \"0191f0c2-bbbb-7ccc-8ddd-000000000001\", \"state\": \"SUCCEEDED\", \"final\": true,"
                        + " \"result\": {\"ok\": true, \"close_by_emulated\": true, \"deals\": [{\"deal\": 1}, {\"deal\": 2}]}}"),
                Fakes.answer(422, "{\"error\":{\"code\":\"validation_error\",\"message\":\"no trailing stop on this venue\"}}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            ExecutionResult placed = client.tradingAccounts().placeOrder(ACCOUNT, "EURUSD", SideEnum.BUY, new BigDecimal("0.01"), "o-1").join();
            ExecutionResult closed = client.tradingAccounts().closePositionBy(ACCOUNT, 7, 9, "o-2").join();
            ExecutionResult trailing = client.tradingAccounts().setTrailingStop(ACCOUNT, 7, new BigDecimal("0.0020"), "o-3").join();

            String base = "/v1/accounts/" + ACCOUNT;
            assertEquals(List.of("POST " + base + "/orders", "POST " + base + "/positions/7/close-by", "PUT " + base + "/positions/7/trailing-stop"),
                    transport.requests.stream().map(r -> r.method() + " " + r.pathWithQuery()).toList());
            assertEquals("{\"symbol\":\"EURUSD\",\"side\":\"buy\",\"volume\":\"0.01\",\"order_type\":\"market\"}",
                    new String(transport.requests.get(0).body(), StandardCharsets.UTF_8));
            assertEquals("o-2", transport.requests.get(1).header("Idempotency-Key"));
            assertEquals(CommandState.ACCEPTED, placed.state());
            assertTrue(closed.closeByEmulated());
            assertEquals(2, closed.deals().size());
            assertFalse(trailing.ok());
            assertEquals(Reason.REFUSED, trailing.reason());
        }
    }

    /**
     * Given a cTrader grant started without and with the tenant's own state, read, its accounts connected
     * and the grant revoked / When each is sent / Then the start posts only what was given, connecting
     * answers the trading accounts as a list, no ids is refused here, and the revoke is a DELETE with the
     * caller's key.
     */
    @Test
    void aCtraderGrantIsStartedReadConnectedAndRevoked() throws Exception {
        String started = "{\"grant_id\": \"" + GRANT + "\", \"consent_url\": \"https://id.ctrader.com/x\"}";
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(201, started), Fakes.answer(201, started),
                ok("{\"id\": \"" + GRANT + "\", \"state\": \"ACTIVE\"}"),
                Fakes.answer(201, "{\"items\": [{\"id\": \"" + ACCOUNT + "\", \"state\": \"PENDING\"}]}"),
                ok("{\"id\": \"" + GRANT + "\", \"state\": \"REVOKED\"}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            client.tradingAccounts().startCtraderGrant(CtraderGrantCreate.EnvironmentEnum.DEMO, CtraderGrantCreate.ScopeEnum.TRADING,
                    "https://app.example.test/back", "g-1").join();
            client.tradingAccounts().startCtraderGrant(CtraderGrantCreate.EnvironmentEnum.LIVE, CtraderGrantCreate.ScopeEnum.ACCOUNTS,
                    "https://app.example.test/back", "user-42", "g-2").join();
            client.tradingAccounts().ctraderGrant(GRANT).join();
            List<TradingAccount> connected = client.tradingAccounts().connectCtraderAccounts(GRANT, List.of("1234567", "7654321"), "g-3").join();
            client.tradingAccounts().revokeCtraderGrant(GRANT, "g-4").join();

            assertEquals("{\"environment\":\"demo\",\"scope\":\"trading\",\"return_url\":\"https://app.example.test/back\"}",
                    new String(transport.requests.get(0).body(), StandardCharsets.UTF_8));
            JsonNode withState = MfxJson.MAPPER.readTree(transport.requests.get(1).body());
            assertEquals("user-42", withState.get("tenant_state").asText());
            assertEquals("/v1/accounts/ctrader/grants/" + GRANT, transport.requests.get(2).pathWithQuery());
            assertEquals("[\"1234567\",\"7654321\"]", MfxJson.MAPPER.readTree(transport.requests.get(3).body()).get("ctid_trader_account_ids").toString());
            assertEquals(ACCOUNT, connected.get(0).getId());
            assertEquals("DELETE", transport.requests.get(4).method());
            assertEquals("g-4", transport.requests.get(4).header("Idempotency-Key"));
            assertThrows(IllegalArgumentException.class, () -> client.tradingAccounts().connectCtraderAccounts(GRANT, List.of(), "g-5"));
            assertNull(withState.get("unexpected"));
        }
    }
}
