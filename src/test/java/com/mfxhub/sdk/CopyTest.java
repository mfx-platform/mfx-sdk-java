package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mfxhub.sdk.models.CopyExternalOpen;
import com.mfxhub.sdk.models.CopyFollowerState;
import com.mfxhub.sdk.models.CopyLeader;
import com.mfxhub.sdk.models.CopyLeaderPageStats;
import com.mfxhub.sdk.models.CopyLeaderSource;
import com.mfxhub.sdk.models.CopyLeaderState;
import com.mfxhub.sdk.models.CopyOutcome;
import com.mfxhub.sdk.models.CopyReason;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Copy Trading and its events. */
class CopyTest {
    private static final UUID LEADER = UUID.fromString("06abfa00-0000-7000-8000-0000000000c1");
    private static final UUID FOLLOWER = UUID.fromString("06abfa00-0000-7000-8000-0000000000f1");
    private static final UUID GROUP = UUID.fromString("06abfa00-0000-7000-8000-0000000000e1");
    private static final UUID ACCOUNT = UUID.fromString("4c2d3e4f-5a6b-7c8d-9e0f-102030405061");
    private static final OffsetDateTime DAY = OffsetDateTime.of(2026, 11, 12, 0, 0, 0, 0, ZoneOffset.UTC);

    private static Fakes.Answer ok(String body) {
        return Fakes.answer(200, body);
    }

    private static String body(Fakes.Sent sent) {
        return new String(sent.body(), StandardCharsets.UTF_8);
    }

    /**
     * Given a leader made on an account with its filters, the leaders listed by two states, and an external
     * leader's open signals / When each is sent / Then each body carries only what was set in the contract's
     * names with the caller's key; a misspelt setting, a word the contract does not have, a double, or a size
     * below zero is refused before anything is sent, while a stop below zero goes; and a page keeps its stats.
     */
    @Test
    void aLeaderIsMadeListedAndSignalledWithEveryBodyCheckedAgainstTheContract() throws Exception {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(201, "{\"id\": \"" + LEADER + "\", \"state\": \"active\"}"),
                ok("{\"items\": [{\"id\": \"" + LEADER + "\"}], \"next_cursor\": null, \"stats\": {\"leaders\": 1, \"active\": 1}}"),
                Fakes.answer(202, "{\"signal_id\": \"" + GROUP + "\", \"duplicate\": false}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            Copy copy = client.copy();
            copy.createLeader(CopyLeaderSource.ACCOUNT, 30, "l-1", CreateLeader.builder().accountId(ACCOUNT)
                    .filters(Map.of("symbols", List.of("EURUSD"))).build()).join();
            CopyPage<CopyLeader, CopyLeaderPageStats> page = copy.leaders(null, ListCopyLeaders.builder()
                    .state(CopyLeaderState.ACTIVE, CopyLeaderState.PAUSED).build()).join();
            copy.sendOpenSignal(LEADER, "s-1", "EURUSD", CopyExternalOpen.SideEnum.BUY, "l-2",
                    OpenSignal.volume(new BigDecimal("1.50")).sl(new BigDecimal("-2.5")).build()).join();

            assertEquals("{\"source\":\"account\",\"signal_lifetime_s\":30,\"account_id\":\"" + ACCOUNT + "\",\"filters\":{\"symbols\":[\"EURUSD\"]}}",
                    body(transport.requests.get(0)));
            assertEquals("l-1", transport.requests.get(0).header("Idempotency-Key"));
            assertEquals("/v1/copy/leaders?state=active%2Cpaused", transport.requests.get(1).pathWithQuery());
            assertEquals(1, page.stats().getLeaders());
            assertEquals("{\"id\":\"s-1\",\"symbol\":\"EURUSD\",\"side\":\"buy\",\"volume\":\"1.50\",\"sl\":\"-2.5\"}", body(transport.requests.get(2)));

            IllegalArgumentException misspelt = assertThrows(IllegalArgumentException.class, () -> copy.createLeader(CopyLeaderSource.EXTERNAL, 30,
                    "l-3", CreateLeader.builder().filters(Map.of("simbols", List.of("EURUSD"))).build()));
            assertTrue(misspelt.getMessage().contains("simbols"), misspelt.getMessage());
            assertThrows(IllegalArgumentException.class, () -> copy.createLeader(CopyLeaderSource.EXTERNAL, 30, "l-4",
                    CreateLeader.builder().filters(Map.of("side", "sideways")).build()));
            assertThrows(IllegalArgumentException.class, () -> copy.createLeader(CopyLeaderSource.EXTERNAL, 30, "l-5",
                    CreateLeader.builder().limits(Map.of("max_volume", 0.1)).build()));
            assertThrows(IllegalArgumentException.class, () -> copy.sendOpenSignal(LEADER, "s-2", "EURUSD", CopyExternalOpen.SideEnum.SELL, "l-6",
                    OpenSignal.units(new BigDecimal("-1")).build()));
            assertEquals(3, transport.requests.size());
        }
    }

    /**
     * Given a follow with its mandate, its preview after a 429, the followers listed, a mandate changed and an
     * unfollow / When each is sent / Then the follow is a keyed write in the contract's names, the preview a
     * read with no key tried again after the 429, the list sends its sort on every page, a change naming a
     * group or a preview with no rows is refused here, and the unfollow says whether it closes what it copied.
     */
    @Test
    void aFollowIsPreviewedMadeChangedAndEnded() throws Exception {
        String follower = "{\"id\": \"" + FOLLOWER + "\", \"state\": \"copying\"}";
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(201, follower), Fakes.rateLimited("0"), ok("{\"rows\": []}"),
                ok("{\"items\": [" + follower + "], \"next_cursor\": \"f-2\"}"), ok("{\"items\": [], \"next_cursor\": null}"), ok(follower), ok(follower));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            Copy copy = client.copy();
            Map<String, Object> scaling = Map.of("mode", "fixed_multiplier", "multiplier", new BigDecimal("0.5"));
            copy.createFollower(ACCOUNT, LEADER, "f-1", FollowerMandate.builder().scaling(scaling).closeOnStopout(true).build()).join();
            copy.previewFollower(ACCOUNT, LEADER, scaling, List.of(Map.of("symbol", "EURUSD", "volume", "1.00"))).join();
            List<UUID> ids = new ArrayList<>();
            copy.allFollowers(ListCopyFollowers.builder().state(CopyFollowerState.COPYING).build()).forEach(f -> ids.add(f.getId()));
            copy.updateFollower(FOLLOWER, "f-2", FollowerMandate.builder().options(Map.of("when_risk_unchecked", "copy")).build()).join();
            copy.unfollow(FOLLOWER, false, "f-3").join();

            JsonNode follow = MfxJson.MAPPER.readTree(transport.requests.get(0).body());
            assertEquals("0.5", follow.get("scaling").get("multiplier").asText());
            assertTrue(follow.get("close_on_stopout").asBoolean());
            assertFalse(transport.requests.get(1).has("Idempotency-Key"));
            assertEquals("/v1/copy/followers/preview", transport.requests.get(2).pathWithQuery());
            assertEquals(List.of(FOLLOWER), ids);
            assertEquals("/v1/copy/followers?cursor=f-2&state=copying&sort=newest", transport.requests.get(4).pathWithQuery());
            assertEquals("PATCH", transport.requests.get(5).method());
            assertEquals("{\"close_copied\":false}", body(transport.requests.get(6)));
            assertThrows(IllegalArgumentException.class,
                    () -> copy.updateFollower(FOLLOWER, "f-4", FollowerMandate.builder().groupId(GROUP).build()));
            assertThrows(IllegalArgumentException.class, () -> copy.previewFollower(ACCOUNT, LEADER, scaling, List.of()));
            assertEquals(7, transport.requests.size());
        }
    }

    /**
     * Given a group's cap cleared and its policy left, members added, the group closed, and its template
     * rolled over as a dry run and applied / When each is sent / Then a clear goes as null and what was not
     * set is left out, the members go as their ids, the close names its reason and pause, a dry run carries
     * no key and an applied run its key; no members, more than 500, an applied run with no key or a dry run
     * with one are refused before anything is sent.
     */
    @Test
    void aGroupIsChangedFilledClosedAndRolledOver() {
        Fakes.Answer group = ok("{\"id\": \"" + GROUP + "\"}");
        Fakes.Recording transport = new Fakes.Recording(group, ok("{\"results\": []}"), ok("{\"batch_id\": \"" + GROUP + "\", \"closes\": []}"),
                ok("{\"results\": []}"), ok("{\"results\": []}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            Copy copy = client.copy();
            copy.updateGroup(GROUP, "g-1", UpdateGroup.builder().name("EU desk").clearGrossExposureCap().build()).join();
            copy.addGroupFollowers(GROUP, List.of(FOLLOWER), "g-2").join();
            copy.closeGroup(GROUP, "desk closing", true, "g-3").join();
            copy.rolloverGroup(GROUP, true, RolloverGroup.builder().fields(List.of(RolloverField.SCALING)).limit(100).build(), null).join();
            copy.rolloverGroup(GROUP, false, "g-4").join();

            assertEquals("{\"name\":\"EU desk\",\"gross_exposure_cap\":null}", body(transport.requests.get(0)));
            assertEquals("{\"follower_ids\":[\"" + FOLLOWER + "\"]}", body(transport.requests.get(1)));
            assertEquals("{\"reason\":\"desk closing\",\"pause\":true}", body(transport.requests.get(2)));
            assertEquals("{\"dry_run\":true,\"fields\":[\"scaling\"],\"limit\":100}", body(transport.requests.get(3)));
            assertFalse(transport.requests.get(3).has("Idempotency-Key"));
            assertEquals("g-4", transport.requests.get(4).header("Idempotency-Key"));

            assertThrows(IllegalArgumentException.class, () -> copy.addGroupFollowers(GROUP, List.of(), "g-5"));
            assertThrows(IllegalArgumentException.class, () -> copy.addGroupFollowers(GROUP, Collections.nCopies(501, FOLLOWER), "g-6"));
            assertThrows(IllegalArgumentException.class, () -> copy.rolloverGroup(GROUP, false, null));
            assertThrows(IllegalArgumentException.class, () -> copy.rolloverGroup(GROUP, true, "g-7"));
            assertEquals(5, transport.requests.size());
        }
    }

    /**
     * Given the copy log by outcome and reason inside a window, the signals by kind, the closes by batch and
     * every stop-out / When each is read / Then several values go as one comma-separated parameter of the
     * contract's words, the window as instants, each page keeps its stats, a window that ends before it starts
     * is refused, and a reason outside the closed list reads as UNKNOWN_DEFAULT_OPEN_API, which is never sent.
     */
    @Test
    void theLogSignalsClosesAndStopoutsAreReadByTheirFilters() {
        Fakes.Recording transport = new Fakes.Recording(ok("{\"items\": [{\"reason\": \"mapping\"}], \"stats\": {\"refused\": 1}}"),
                ok("{\"items\": [], \"stats\": {}}"), ok("{\"items\": [], \"stats\": {\"incomplete\": 0}}"), ok("{\"items\": []}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            Copy copy = client.copy();
            CopyPage<?, ?> log = copy.log(null, ListCopyLog.builder().outcome(CopyOutcome.REFUSED, CopyOutcome.SKIPPED)
                    .reason(CopyReason.MAPPING, CopyReason.MIN_VOLUME).from(DAY).to(DAY.plusDays(1)).sort(ListCopyLog.Sort.MFX_MS).build()).join();
            copy.signals(null, ListCopySignals.builder().kind(ListCopySignals.Kind.EXTERNAL).action(ListCopySignals.Action.CLOSE_PART).build()).join();
            copy.closes(null, ListCopyCloses.builder().batchId(GROUP).build()).join();
            copy.stopouts(FOLLOWER, null, ListCopyStopouts.all()).join();

            assertEquals(List.of("/v1/copy/log?outcome=refused%2Cskipped&reason=mapping%2Cmin_volume&from=2026-11-12T00%3A00%3A00%2B00%3A00"
                    + "&to=2026-11-13T00%3A00%3A00%2B00%3A00&sort=mfx_ms", "/v1/copy/signals?kind=external&action=close_part",
                    "/v1/copy/closes?batch_id=" + GROUP, "/v1/copy/followers/" + FOLLOWER + "/stopouts"),
                    transport.requests.stream().map(Fakes.Sent::pathWithQuery).toList());
            assertEquals(1, log.size());
            assertEquals("1", MfxJson.MAPPER.valueToTree(log.stats()).get("refused").asText());
            assertThrows(IllegalArgumentException.class, () -> ListCopyLog.builder().from(DAY).to(DAY.minusDays(1)).build());
            assertEquals(CopyReason.UNKNOWN_DEFAULT_OPEN_API, CopyReason.fromValue("terminal_not_active"));
            assertEquals(16, Copy.REFUSAL_REASONS.size());
        }
    }

    /**
     * Given copy.copied, copy.refused with Risk Management's detail as JSON text, and copy.closed / When each is
     * read as an event / Then each is its typed record naming the follower account and its venue: what the
     * copy became and its stage timings, the refusal's reason with the rule and figures as text, and what a
     * requested close closed and what it did not.
     */
    @Test
    void theCopyEventsAreTyped() {
        String common = "\"schema_version\": \"1\", \"follower_id\": \"" + FOLLOWER + "\", \"leader_id\": \"" + LEADER + "\", \"account_id\": \""
                + ACCOUNT + "\", \"venue\": \"ctrader\", \"at\": \"2026-11-12T09:00:00Z\"";
        String deal = "\"deal\": {\"action\": \"open\", \"side\": \"buy\", \"units\": \"100000\"}";
        CopyCopied copied = assertInstanceOf(CopyCopied.class, Events.eventOf(MarketDataTest.message("events", "copy.copied", "{" + common + ","
                + " \"signal_id\": \"" + GROUP + "\", \"command_id\": \"" + GROUP + "\", " + deal + ", \"as\": {\"symbol\": \"EURUSD\","
                + " \"side\": \"buy\", \"volume\": \"0.50\"}, \"latency_ms\": \"12.5\", \"stages\": {\"detect_ms\": 1.5, \"broker_ms\": null}}")));
        assertEquals(new BigDecimal("0.50"), copied.as().volume());
        assertEquals(new BigDecimal("100000"), copied.deal().units());
        assertEquals(Map.of("detect_ms", 1.5), copied.stages());
        assertEquals(12.5, copied.latencyMs());
        assertEquals("ctrader", copied.venue());

        CopyRefused refused = assertInstanceOf(CopyRefused.class, Events.eventOf(MarketDataTest.message("events", "copy.refused", "{" + common + ","
                + " \"signal_id\": \"" + GROUP + "\", " + deal + ", \"outcome\": \"refused\", \"reason\": \"risk_rule\", \"risk\":"
                + " \"{\\\"reason\\\": \\\"daily_loss_reached\\\", \\\"rule\\\": \\\"daily_loss\\\", \\\"version\\\": 3, \\\"used\\\": \\\"240.00\\\"}\"}")));
        assertEquals("risk_rule", refused.reason());
        assertEquals("daily_loss", refused.risk().rule());
        assertEquals(3, refused.risk().version());
        assertEquals("240.00", refused.risk().used());

        CopyClosedEvent closed = assertInstanceOf(CopyClosedEvent.class, Events.eventOf(MarketDataTest.message("events", "copy.closed", "{" + common
                + ", \"close_id\": \"" + GROUP + "\", \"cause\": \"unfollowed\", \"state\": \"incomplete\", \"closed\": [{\"symbol\": \"EURUSD\","
                + " \"volume\": \"0.50\"}], \"not_closed\": [{\"symbol\": \"GBPUSD\", \"why\": \"gave_up\"}]}")));
        assertEquals(1, closed.closed().size());
        assertNull(closed.closed().get(0).commandId());
        assertEquals("gave_up", closed.notClosed().get(0).why());
        assertNull(closed.notClosed().get(0).volume());
    }
}
