package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mfxhub.sdk.models.RiskAccount;
import com.mfxhub.sdk.models.RiskAccountPage;
import com.mfxhub.sdk.models.RiskActivityKind;
import com.mfxhub.sdk.models.RiskClear;
import com.mfxhub.sdk.models.RiskPlanCreate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Risk Management and its events. */
class RiskTest {
    private static final UUID POLICY = UUID.fromString("0191f0c2-dddd-7eee-8fff-000000000001");
    private static final UUID ACCOUNT = UUID.fromString("0191f0c2-aaaa-7bbb-8ccc-0000000000a1");
    private static final UUID CHALLENGE = UUID.fromString("0191f0c2-cccc-7ddd-8eee-000000000001");
    private static final UUID PLAN = UUID.fromString("0191f0c2-eeee-7fff-8000-000000000001");

    private static Fakes.Answer ok(String body) {
        return Fakes.answer(200, body);
    }

    private static String body(Fakes.Sent sent) {
        return new String(sent.body(), StandardCharsets.UTF_8);
    }

    /**
     * Given a policy created with a decimal limit, a version that relaxes it with a reason, an activation
     * and an archive / When each is sent / Then each posts what was given in the API's names, a decimal as
     * exact text, the account ids as text, with the caller's key; a double anywhere in the rules is refused
     * before anything is sent; and an archive refused while accounts hold the policy is a StateException.
     */
    @Test
    void aPolicyIsCreatedVersionedActivatedAndArchivedWithItsFiguresAsText() throws Exception {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(201, "{\"id\": \"" + POLICY + "\"}"), Fakes.answer(201, "{\"version\": 2}"),
                ok("{\"results\": []}"), Fakes.answer(409, "{\"error\":{\"code\":\"state_error\",\"message\":\"held\",\"details\":{\"accounts\":[\"a\"]}}}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            Map<String, Object> rules = Map.of("daily_loss", Map.of("limit", Map.of("pct", new BigDecimal("5.0"))));
            client.risk().createPolicy("Funded", rules, Map.of("zone", "broker"), "p-1").join();
            client.risk().addPolicyVersion(POLICY, rules, Map.of("zone", "utc"), "a wider limit for the desk", "p-2").join();
            client.risk().activate(POLICY, List.of(ACCOUNT), 2, "p-3").join();
            Throwable held = Fakes.failure(client.risk().archivePolicy(POLICY, "p-4"));

            assertEquals("{\"name\":\"Funded\",\"rules\":{\"daily_loss\":{\"limit\":{\"pct\":\"5.0\"}}},\"reset\":{\"zone\":\"broker\"}}",
                    body(transport.requests.get(0)));
            assertEquals("p-1", transport.requests.get(0).header("Idempotency-Key"));
            assertEquals("a wider limit for the desk", MfxJson.MAPPER.readTree(transport.requests.get(1).body()).get("reason").asText());
            assertEquals("{\"account_ids\":[\"" + ACCOUNT + "\"],\"version\":2}", body(transport.requests.get(2)));
            assertEquals("/v1/risk/policies/" + POLICY + "/archive", transport.requests.get(3).pathWithQuery());
            StateException state = assertInstanceOf(StateException.class, held);
            assertEquals("a", state.details().get("accounts").get(0).asText());

            assertThrows(IllegalArgumentException.class,
                    () -> client.risk().createPolicy("Float", Map.of("daily_loss", Map.of("pct", 0.07 * 3)), Map.of(), "p-5"));
            assertEquals(4, transport.requests.size());
        }
    }

    /**
     * Given accounts filtered by two coverages and a restriction, their pages, one account's equity window
     * and its close scope / When each is read / Then the filters go as repeated parameters with the sort on
     * every page (daily loss used unless set), the page keeps its stats, every account is listed by cursor,
     * and the close scope's word is the contract's.
     */
    @Test
    void theAccountsAreListedByCoverageWithTheirSortOnEveryPage() {
        String row = "{\"account_id\": \"" + ACCOUNT + "\"}";
        Fakes.Recording transport = new Fakes.Recording(ok("{\"stats\": {\"accounts\": 2}, \"items\": [" + row + "], \"next_cursor\": \"r-2\"}"),
                ok("{\"items\": [" + row + "], \"next_cursor\": null}"), ok("{\"points\": []}"), ok("{\"positions\": []}"),
                ok("{\"stats\": {\"accounts\": 1}, \"items\": []}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            ListRiskAccounts filter = ListRiskAccounts.builder().coverage(ListRiskAccounts.Coverage.DEGRADED, ListRiskAccounts.Coverage.NO_RULES)
                    .restriction(ListRiskAccounts.Restriction.ENTRIES_BLOCKED).build();
            List<RiskAccount> accounts = new ArrayList<>();
            client.risk().accounts(filter).forEach(accounts::add);
            client.risk().equity(ACCOUNT, OffsetDateTime.of(2026, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC)).join();
            client.risk().closeScope(ACCOUNT, Risk.CloseScope.MFX_POSITIONS).join();
            RiskAccountPage page = client.risk().accountsPage(null, ListRiskAccounts.builder().sort(ListRiskAccounts.Sort.INPUTS_AGE).limit(5).build()).join();

            assertEquals(2, accounts.size());
            assertEquals(List.of("/v1/risk/accounts?coverage=degraded&coverage=no_rules&restriction=entries_blocked&sort=daily_loss_used",
                    "/v1/risk/accounts?cursor=r-2&coverage=degraded&coverage=no_rules&restriction=entries_blocked&sort=daily_loss_used",
                    "/v1/risk/accounts/" + ACCOUNT + "/equity?from=2026-11-01T00%3A00%3A00%2B00%3A00",
                    "/v1/risk/accounts/" + ACCOUNT + "/close-scope?scope=mfx_positions", "/v1/risk/accounts?limit=5&sort=inputs_age"),
                    transport.requests.stream().map(Fakes.Sent::pathWithQuery).toList());
            assertEquals(1, page.getStats().getAccounts());
        }
    }

    /**
     * Given an order checked, a trade previewed after a 429, a plan created from the generated model, and an
     * entry a rule refuses / When each is sent / Then the planner's calls are reads (no key, tried again
     * after a 429) carrying the volume as exact text, the plan is a keyed write, and the refusal is a
     * RiskRefusedException that names the rule, the policy, its version and the figures as text.
     */
    @Test
    void thePlannerReadsAndARefusedEntryNamesItsRule() throws Exception {
        Fakes.Recording transport = new Fakes.Recording(ok("{\"verdict\": \"allow\", \"checks\": []}"), Fakes.rateLimited("0"),
                ok("{\"volume\": \"0.40\"}"), Fakes.answer(201, "{\"id\": \"" + PLAN + "\"}"),
                Fakes.answer(422, "{\"error\":{\"code\":\"risk_refused\",\"message\":\"the daily loss limit\",\"details\":{\"reason\":"
                        + "\"daily_loss_reached\",\"rule\":\"daily_loss\",\"policy_id\":\"" + POLICY + "\",\"version\":3,\"used\":\"240.00\","
                        + "\"limit\":\"300.00\",\"would_be\":\"310.40\"}}}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            Map<String, Object> order = Map.of("symbol", "EURUSD", "side", "buy", "type", "market", "volume", new BigDecimal("0.40"));
            client.risk().check(ACCOUNT, order).join();
            client.risk().preview(Map.of("account_id", ACCOUNT.toString(), "risk", Map.of("pct", "1"))).join();
            client.risk().createPlan(new RiskPlanCreate().accountId(ACCOUNT), "plan-1").join();
            Throwable refused = Fakes.failure(client.risk().createPlan(Map.of("account_id", ACCOUNT.toString(), "order", order), "plan-2"));

            JsonNode checked = MfxJson.MAPPER.readTree(transport.requests.get(0).body());
            assertEquals("0.40", checked.get("order").get("volume").asText());
            assertTrue(checked.get("order").get("volume").isTextual());
            assertTrue(!transport.requests.get(0).has("Idempotency-Key"));
            assertEquals(List.of("/v1/risk/planner/check", "/v1/risk/planner/preview", "/v1/risk/planner/preview", "/v1/risk/plans", "/v1/risk/plans"),
                    transport.requests.stream().map(Fakes.Sent::pathWithQuery).toList());
            assertEquals("plan-1", transport.requests.get(3).header("Idempotency-Key"));
            RiskRefusal why = assertInstanceOf(RiskRefusedException.class, refused).refusal();
            assertEquals("daily_loss", why.rule());
            assertEquals(3, why.version());
            assertEquals("310.40", why.wouldBe());
            assertThrows(IllegalArgumentException.class, () -> client.risk().check(ACCOUNT, Map.of("volume", 0.4)));
            assertEquals(5, transport.requests.size());
        }
    }

    /**
     * Given a plan amended, paused, resumed and ended, a challenge created, versioned, archived and enrolled
     * in, its accounts filtered, and the activity by kind / When each is sent / Then each goes to its own
     * route, the writes with their keys, the challenge's accounts and the activity with their filters as
     * repeated parameters and their sort on every page, and a breach is cleared by the rule's own word.
     */
    @Test
    void plansChallengesAndTheActivityGoToTheirRoutes() throws Exception {
        Fakes.Answer one = ok("{\"id\": \"" + PLAN + "\"}");
        Fakes.Recording transport = new Fakes.Recording(one, one, one, one, Fakes.answer(201, "{\"id\": \"" + CHALLENGE + "\"}"),
                Fakes.answer(201, "{\"id\": \"" + CHALLENGE + "\"}"), one, ok("{\"results\": []}"),
                ok("{\"stats\": {}, \"items\": [{\"account_id\": \"" + ACCOUNT + "\"}], \"next_cursor\": null}"),
                ok("{\"stats\": {}, \"items\": [], \"next_cursor\": null}"), ok("{}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            Risk risk = client.risk();
            risk.amendPlan(PLAN, List.of(Map.of("trigger", Map.of("kind", "r", "value", new BigDecimal("2")))), "s-1").join();
            risk.pausePlan(PLAN, "news", "s-2").join();
            risk.resumePlan(PLAN, "s-3").join();
            risk.endPlan(PLAN, "s-4").join();
            risk.createChallenge("Phase 1", Map.of("daily_loss", Map.of("pct", "5")), "c-1").join();
            risk.addChallengeVersion(CHALLENGE, Map.of("daily_loss", Map.of("pct", "4")), "c-2").join();
            risk.archiveChallenge(CHALLENGE, "c-3").join();
            risk.enrol(CHALLENGE, List.of(ACCOUNT), "c-4").join();
            risk.challengeAccounts(CHALLENGE, ListChallengeAccounts.builder().state(ListChallengeAccounts.State.IN_PROGRESS)
                    .rule(ListChallengeAccounts.Rule.MAX_DRAWDOWN).belowMinDays(true).build()).forEach(a -> { });
            risk.activity(ListRiskActivity.builder().kind(RiskActivityKind.values()[0]).sort(ListRiskActivity.Sort.NEWEST).build()).forEach(a -> { });
            risk.clear(ACCOUNT, RiskClear.RuleEnum.DAILY_LOSS, "reviewed", "x-1").join();

            List<String> sent = transport.requests.stream().map(r -> r.method() + " " + r.pathWithQuery()).toList();
            assertEquals(List.of("POST /v1/risk/plans/" + PLAN + "/amend", "POST /v1/risk/plans/" + PLAN + "/pause",
                    "POST /v1/risk/plans/" + PLAN + "/resume", "DELETE /v1/risk/plans/" + PLAN, "POST /v1/risk/challenges",
                    "POST /v1/risk/challenges/" + CHALLENGE + "/versions", "POST /v1/risk/challenges/" + CHALLENGE + "/archive",
                    "POST /v1/risk/challenges/" + CHALLENGE + "/accounts",
                    "GET /v1/risk/challenges/" + CHALLENGE + "/accounts?state=in_progress&rule=max_drawdown&below_min_days=true&sort=daily_loss_used",
                    "GET /v1/risk/activity?kind=" + RiskActivityKind.values()[0].getValue() + "&sort=newest", "POST /v1/risk/accounts/" + ACCOUNT + "/clear"),
                    sent);
            assertEquals(MfxJson.MAPPER.readTree("{\"steps\":[{\"trigger\":{\"kind\":\"r\",\"value\":\"2\"}}]}"),
                    MfxJson.MAPPER.readTree(transport.requests.get(0).body()));
            assertEquals("{\"daily_loss\":{\"pct\":\"4\"}}", body(transport.requests.get(5)));
            assertEquals("{\"rule\":\"daily_loss\",\"reason\":\"reviewed\"}", body(transport.requests.get(10)));
            assertEquals("s-4", transport.requests.get(3).header("Idempotency-Key"));
        }
    }

    /**
     * Given a rule breach whose flat fields are empty for none and whose actions are JSON text, a coverage
     * change, a programme result and a closed period / When each is read as an event / Then each is its typed
     * record: an empty field is null, the figures exact, the actions a list of words, the coverage's kind
     * its coverage, and the period the platform's object; a period missing is refused, naming it.
     */
    @Test
    void theRiskEventsAreTyped() {
        String common = "\"schema_version\": \"1\", \"account_id\": \"" + ACCOUNT + "\", \"at\": \"2026-11-17T09:00:00Z\"";
        RiskRuleEvent breach = assertInstanceOf(RiskRuleEvent.class, Events.eventOf(MarketDataTest.message("events", "risk.rule_breached",
                "{" + common + ", \"rule\": \"daily_loss\", \"policy_id\": \"" + POLICY + "\", \"challenge_id\": \"\", \"version\": \"3\","
                        + " \"used\": \"300.10\", \"limit\": \"300.00\", \"level\": \"act\", \"actions\": \"[\\\"notify\\\",\\\"block_entries\\\"]\","
                        + " \"cleared_by\": \"\"}")));
        assertEquals("risk.rule_breached", breach.kind());
        assertEquals(POLICY, breach.policyId());
        assertNull(breach.challengeId());
        assertNull(breach.clearedBy());
        assertEquals(3L, breach.version());
        assertEquals(new BigDecimal("300.10"), breach.used());
        assertEquals(List.of("notify", "block_entries"), breach.actions());

        RiskCoverageChanged coverage = assertInstanceOf(RiskCoverageChanged.class, Events.eventOf(MarketDataTest.message("events",
                "risk.coverage_changed", "{" + common + ", \"kind\": \"pre_trade\", \"from\": \"healthy\", \"to\": \"degraded\", \"reason\": \"\"}")));
        assertEquals("pre_trade", coverage.coverage());
        assertNull(coverage.reason());

        RiskProgrammeResult result = assertInstanceOf(RiskProgrammeResult.class, Events.eventOf(MarketDataTest.message("events",
                "risk.programme_result", "{" + common + ", \"challenge_id\": \"" + CHALLENGE + "\", \"version\": 2, \"result\": \"passed\"}")));
        assertEquals("passed", result.result());
        assertNull(result.rule());

        RiskPeriodClosed period = assertInstanceOf(RiskPeriodClosed.class, Events.eventOf(MarketDataTest.message("events", "risk.period_closed",
                "{" + common + ", \"challenge_id\": \"" + CHALLENGE + "\", \"period\": \"{\\\"profit\\\": \\\"12.50\\\"}\"}")));
        assertEquals("12.50", period.period().get("profit").asText());
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> Events.eventOf(MarketDataTest.message("events",
                "risk.period_closed", "{" + common + ", \"challenge_id\": \"" + CHALLENGE + "\"}")));
        assertTrue(missing.getMessage().contains("`period`"));
    }
}
