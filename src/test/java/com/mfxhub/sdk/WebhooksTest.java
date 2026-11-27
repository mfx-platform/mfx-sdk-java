package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mfxhub.sdk.models.TenantExpertAdvisor;
import com.mfxhub.sdk.models.WebhookEndpoint;
import com.mfxhub.sdk.models.WebhookFailure;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Webhooks and expert advisors. */
class WebhooksTest {
    private static final UUID ENDPOINT = UUID.fromString("0191f0c2-9999-7aaa-8bbb-000000000001");
    private static final UUID VERSION = UUID.fromString("0191f0c2-8888-7aaa-8bbb-000000000001");
    private static final Instant NOW = Instant.ofEpochSecond(1_763_370_000L);

    private static Fakes.Answer ok(String body) {
        return Fakes.answer(200, body);
    }

    private static String body(Fakes.Sent sent) {
        return new String(sent.body(), StandardCharsets.UTF_8);
    }

    private static String sign(String secret, long stamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((stamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Given an endpoint created with a description, listed, updated to pause it, its secret rotated, a test
     * sent and the endpoint deleted / When each is sent / Then each goes to its route with only what was
     * set (paused as the status paused), the keyed writes carry the caller's key and the two the API takes
     * no key on carry none, and the list is a plain list.
     */
    @Test
    void anEndpointIsCreatedListedPausedRotatedTestedAndDeleted() throws Exception {
        String endpoint = "{\"id\": \"" + ENDPOINT + "\", \"url\": \"https://hooks.example.test/mfx\"}";
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(201, "{\"id\": \"" + ENDPOINT + "\", \"secret\": \"whsec_1\"}"),
                ok("{\"items\": [" + endpoint + "]}"), ok(endpoint), ok("{\"secret\": \"whsec_2\"}"), Fakes.answer(202, "{}"), Fakes.answer(204, ""));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            client.webhooks().create("https://hooks.example.test/mfx", List.of("position.closed", "terminal.*"), "desk alerts", "w-1").join();
            List<WebhookEndpoint> listed = client.webhooks().list().join();
            client.webhooks().update(ENDPOINT, "w-2", UpdateWebhook.builder().paused(true).build()).join();
            client.webhooks().rotateSecret(ENDPOINT, "w-3").join();
            client.webhooks().test(ENDPOINT).join();
            client.webhooks().delete(ENDPOINT).join();

            assertEquals("{\"url\":\"https://hooks.example.test/mfx\",\"event_types\":[\"position.closed\",\"terminal.*\"],\"description\":\"desk alerts\"}",
                    body(transport.requests.get(0)));
            assertEquals(1, listed.size());
            assertEquals("PATCH", transport.requests.get(2).method());
            assertEquals("{\"status\":\"paused\"}", body(transport.requests.get(2)));
            assertEquals("w-3", transport.requests.get(3).header("Idempotency-Key"));
            assertEquals("/v1/webhooks/" + ENDPOINT + "/test", transport.requests.get(4).pathWithQuery());
            assertTrue(!transport.requests.get(4).has("Idempotency-Key"));
            assertEquals("DELETE", transport.requests.get(5).method());
            assertTrue(!transport.requests.get(5).has("Idempotency-Key"));
        }
    }

    /**
     * Given an endpoint's failures on two pages filtered by two states, and a replay after a cursor and of
     * one failure / When each is sent / Then the states go as one comma-separated value on every page, the
     * failures follow the cursor, and each replay posts exactly the one it names.
     */
    @Test
    void theFailuresAreListedByCursorAndReplayedByCursorOrOne() {
        UUID failure = UUID.fromString("0191f0c2-9999-7aaa-8bbb-0000000000f1");
        Fakes.Recording transport = new Fakes.Recording(ok("{\"items\": [{\"id\": \"" + failure + "\"}], \"next_cursor\": \"f-2\"}"),
                ok("{\"items\": [{\"id\": \"" + ENDPOINT + "\"}], \"next_cursor\": null}"), Fakes.answer(202, "{}"), Fakes.answer(202, "{}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            List<UUID> ids = new ArrayList<>();
            for (WebhookFailure one : client.webhooks().failures(ENDPOINT, Set.of(WebhookFailure.StateEnum.DEAD), 10)) {
                ids.add(one.getId());
            }
            client.webhooks().replay(ENDPOINT, ReplayWebhook.after("evt-41"), "r-1").join();
            client.webhooks().replay(ENDPOINT, ReplayWebhook.failureId(failure), "r-2").join();

            assertEquals(List.of(failure, ENDPOINT), ids);
            assertEquals(List.of("/v1/webhooks/" + ENDPOINT + "/failures?state=dead&limit=10",
                    "/v1/webhooks/" + ENDPOINT + "/failures?state=dead&cursor=f-2&limit=10"),
                    transport.requests.subList(0, 2).stream().map(Fakes.Sent::pathWithQuery).toList());
            assertEquals("{\"after\":\"evt-41\"}", body(transport.requests.get(2)));
            assertEquals("{\"failure_id\":\"" + failure + "\"}", body(transport.requests.get(3)));
        }
    }

    /**
     * Given a delivery signed with the endpoint's secret, one signed with the previous secret after a
     * rotation, one signed too long ago, one with no v1, and one whose body was changed / When each is
     * verified / Then the genuine ones answer their signed time, from either secret, and the rest are
     * refused as stale, malformed and mismatch.
     */
    @Test
    void aDeliveryIsVerifiedByItsSignatureAndItsTime() throws Exception {
        String payload = "{\"type\":\"position.closed\"}";
        byte[] raw = payload.getBytes(StandardCharsets.UTF_8);
        long stamp = NOW.getEpochSecond() - 10;
        List<Secret> both = List.of(Secret.of("whsec_new"), Secret.of("whsec_old"));
        String header = "t=" + stamp + ", v1=" + sign("whsec_old", stamp, payload);

        assertEquals(stamp, Webhooks.verify(raw, header, both, Webhooks.TOLERANCE, NOW));
        assertEquals(stamp, Webhooks.verify(raw, "t=" + stamp + ",v1=" + sign("whsec_new", stamp, payload), List.of(Secret.of("whsec_new")),
                Webhooks.TOLERANCE, NOW));
        long old = NOW.getEpochSecond() - 301;
        WebhookSignatureException stale = assertThrows(WebhookSignatureException.class,
                () -> Webhooks.verify(raw, "t=" + old + ",v1=" + sign("whsec_new", old, payload), both, Duration.ofMinutes(5), NOW));
        assertEquals("stale", stale.reason());
        assertEquals("malformed", assertThrows(WebhookSignatureException.class,
                () -> Webhooks.verify(raw, "t=" + stamp, both, Webhooks.TOLERANCE, NOW)).reason());
        assertEquals("mismatch", assertThrows(WebhookSignatureException.class,
                () -> Webhooks.verify("{\"type\":\"x\"}".getBytes(StandardCharsets.UTF_8), header, both, Webhooks.TOLERANCE, NOW)).reason());
        assertInstanceOf(MfxException.class, stale);
    }

    /**
     * Given a bundle uploaded, the versions listed, a report read and a version withdrawn / When each is
     * sent / Then the upload posts each file's path and its source as base64 of its UTF-8, in order, with
     * the caller's key, the list follows the cursor, and the withdraw is a keyed DELETE; and a wait for the
     * verdict reads again after 2 s, doubling, until the version is settled.
     */
    @Test
    void aBundleIsUploadedCheckedAndWithdrawnAndItsVerdictWaitedFor() throws Exception {
        String uploaded = "{\"id\": \"" + VERSION + "\", \"state\": \"uploaded\"}";
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(201, uploaded),
                ok("{\"items\": [" + uploaded + "], \"next_cursor\": null}"), ok("{\"findings\": []}"), ok(uploaded),
                ok("{\"id\": \"" + VERSION + "\", \"state\": \"analysing\"}"), ok("{\"id\": \"" + VERSION + "\", \"state\": \"judged\"}"),
                ok("{\"id\": \"" + VERSION + "\", \"state\": \"approved\"}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            Map<String, String> files = new LinkedHashMap<>();
            files.put("Expert.mq5", "#include \"Lib.mqh\"\n// é");
            files.put("Lib.mqh", "int x;");
            client.expertAdvisors().upload("Breakout", files, "ea-1").join();
            List<TenantExpertAdvisor> versions = new ArrayList<>();
            client.expertAdvisors().list().forEach(versions::add);
            client.expertAdvisors().report(VERSION).join();
            client.expertAdvisors().delete(VERSION, "ea-2").join();
            TenantExpertAdvisor settled = client.expertAdvisors().waitForVerdict(VERSION).join();

            JsonNode sent = MfxJson.MAPPER.readTree(transport.requests.get(0).body());
            assertEquals("Breakout", sent.get("name").asText());
            assertEquals("Expert.mq5", sent.get("files").get(0).get("path").asText());
            assertEquals("#include \"Lib.mqh\"\n// é",
                    new String(Base64.getDecoder().decode(sent.get("files").get(0).get("content_base64").asText()), StandardCharsets.UTF_8));
            assertEquals("Lib.mqh", sent.get("files").get(1).get("path").asText());
            assertEquals("ea-1", transport.requests.get(0).header("Idempotency-Key"));
            assertEquals(1, versions.size());
            assertEquals("/v1/expert-advisors/" + VERSION + "/report", transport.requests.get(2).pathWithQuery());
            assertEquals("DELETE", transport.requests.get(3).method());
            assertEquals("ea-2", transport.requests.get(3).header("Idempotency-Key"));
            assertEquals("approved", settled.getState().getValue());
            assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4)), TerminalWritesTest.WAITS);
        }
    }

    /**
     * Given a telemetry batch on an ea: channel, a loss, and another message / When each is read / Then the
     * batch has its records with their place and what the expert wrote, and its counts (0 when absent), the
     * loss its files and count, and any other message is null.
     */
    @Test
    void theEaChannelsMessagesAreReadAsWhatTheyAre() {
        String channel = Channel.ea(TerminalWritesTest.ID);
        EaTelemetry batch = assertInstanceOf(EaTelemetry.class, ExpertAdvisors.telemetryOf(MarketDataTest.message(channel, ExpertAdvisors.TELEMETRY,
                "{\"records\": [{\"segment\": 4, \"line\": 17, \"record\": {\"signal\": \"long\", \"score\": \"0.82\"}}], \"dropped\": 2}")));
        assertEquals(1, batch.records().size());
        assertEquals(4L, batch.records().get(0).segment());
        assertEquals(17L, batch.records().get(0).line());
        assertEquals("0.82", batch.records().get(0).record().get("score").asText());
        assertEquals(0L, batch.skipped());
        assertEquals(2L, batch.dropped());

        EaTelemetryLost lost = assertInstanceOf(EaTelemetryLost.class, ExpertAdvisors.telemetryOf(MarketDataTest.message(channel,
                ExpertAdvisors.TELEMETRY_LOST, "{\"segment_from\": 3, \"segment_to\": 5, \"records\": 1200}")));
        assertEquals(5L, lost.segmentTo());
        assertEquals(1200L, lost.records());
        assertEquals(channel, lost.channel());
        assertNull(ExpertAdvisors.telemetryOf(MarketDataTest.message(channel, "md.tick", "{}")));
        assertNull(ExpertAdvisors.telemetryOf(new StreamEvent.Resync(channel)));
    }
}
