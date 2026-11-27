package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mfxhub.sdk.models.BulkAction;
import com.mfxhub.sdk.models.CredentialSubmission;
import com.mfxhub.sdk.models.ExpertAdvisorAttach;
import com.mfxhub.sdk.models.TenantTerminal;
import com.mfxhub.sdk.models.TerminalCreate;
import com.mfxhub.sdk.models.TerminalOperation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * The rest of the terminals client beyond Core:
 * create, the credential, the governed relaunches, the bulk restart, drain, resume, retire and the waits.
 */
class TerminalWritesTest {
    static final UUID ID = UUID.fromString("0191f0c2-aaaa-7bbb-8ccc-000000000001");
    static final UUID BROKER = UUID.fromString("2c2d3e4f-5a6b-7c8d-9e0f-102030405060");
    static final UUID SERVER = UUID.fromString("3c2d3e4f-5a6b-7c8d-9e0f-102030405060");
    static final UUID FARM = UUID.fromString("1c2d3e4f-5a6b-7c8d-9e0f-102030405060");
    static final UUID OPERATION = UUID.fromString("0191f0c2-aaaa-7bbb-8ccc-0000000000aa");
    static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");

    static final List<Duration> WAITS = new ArrayList<>();

    static MfxClient client(Fakes.Recording transport) {
        WAITS.clear();
        return MfxClient.builder(Fakes.BASE, "mfxk_1", Fakes.key())
                .transport(transport)
                .clock(Clock.fixed(NOW, ZoneOffset.UTC))
                .pause(wait -> {
                    WAITS.add(wait);
                    return CompletableFuture.completedFuture(null);
                })
                .build();
    }

    static String terminal(String state, String extra) {
        return "{\"id\": \"" + ID + "\", \"state\": \"" + state + "\", \"streaming\": false, \"execution\": true" + extra + "}";
    }

    static JsonNode sent(Fakes.Sent request) throws Exception {
        return MfxJson.MAPPER.readTree(request.body());
    }

    /**
     * Given a terminal asked for with and without options / When it is created / Then one POST of
     * /terminals carries the caller's Idempotency-Key and only what the caller set (no default instruments,
     * labels or farm the caller did not choose), and a write with no key or a blank one is refused before
     * anything is sent.
     */
    @Test
    void aTerminalIsAskedForWithOnlyWhatTheCallerSetAndTheCallersKey() throws Exception {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(201, terminal("REQUESTED", "")),
                Fakes.answer(201, terminal("REQUESTED", "")));
        try (MfxClient client = client(transport)) {
            TenantTerminal plain = client.terminals().create(BROKER, SERVER, "key-1").join();
            client.terminals().create(BROKER, SERVER, "key-2", CreateTerminal.builder().farmId(FARM).execution(true)
                    .instruments(List.of("EURUSD.sd")).label("desk", "fx").redundancy(TerminalCreate.RedundancyEnum.ACTIVE_PASSIVE).build()).join();

            assertEquals(TenantTerminal.StateEnum.REQUESTED, plain.getState());
            Fakes.Sent first = transport.requests.get(0);
            assertEquals("POST", first.method());
            assertEquals("/v1/terminals", first.pathWithQuery());
            assertEquals("key-1", first.header("Idempotency-Key"));
            assertEquals("{\"broker_id\":\"" + BROKER + "\",\"server_id\":\"" + SERVER + "\"}", new String(first.body(), java.nio.charset.StandardCharsets.UTF_8));
            JsonNode full = sent(transport.requests.get(1));
            assertEquals(FARM.toString(), full.get("farm_id").asText());
            assertTrue(full.get("execution").asBoolean());
            assertEquals("EURUSD.sd", full.get("instruments").get(0).asText());
            assertEquals("fx", full.get("labels").get("desk").asText());
            assertEquals("active_passive", full.get("redundancy").asText());
            assertFalse(full.has("streaming"));

            assertThrows(IllegalArgumentException.class, () -> client.terminals().create(BROKER, SERVER, " "));
            assertThrows(NullPointerException.class, () -> client.terminals().retire(ID, null));
            assertEquals(2, transport.requests.size());
        }
    }

    /**
     * Given a broker login with its password as a Secret / When it is submitted / Then the password is in
     * the one request's body and nowhere it could be printed: the Secret, the options and the client all
     * render without it, and an investor credential says so in cred_type.
     */
    @Test
    void aCredentialIsSentOnceAndNeverRendered() throws Exception {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(202, "{\"terminal_id\": \"" + ID + "\", \"execution_disabled\": true}"));
        Secret password = Secret.of("hunter2-broker");
        try (MfxClient client = client(transport)) {
            client.terminals().submitCredentials(ID, 5_000_123L, password, "Broker-Live", "key-c",
                    CredentialSubmission.CredTypeEnum.INVESTOR).join();

            JsonNode body = sent(transport.requests.get(0));
            assertEquals("/v1/terminals/" + ID + "/credentials", transport.requests.get(0).pathWithQuery());
            assertEquals("hunter2-broker", body.get("password").asText());
            assertEquals("investor", body.get("cred_type").asText());
            assertEquals(5_000_123L, body.get("login").asLong());
            assertEquals("Secret(***)", password.toString());
            assertEquals("\"***\"", MfxJson.MAPPER.writeValueAsString(password));
            assertFalse(client.toString().contains("hunter2"));
        }
    }

    /**
     * Given a restart, an expert advisor attached and detached, and a bulk restart / When each is sent /
     * Then a restart queues unless told not to and an expert advisor does not, the detach sends its fields
     * as the query of a DELETE, open positions are confirmed only when the caller says so, and a bulk
     * restart that names nothing, has a blank reason or lists more than 1000 terminals is refused here.
     */
    @Test
    void aGovernedRelaunchQueuesAsEachCallDefaultsAndABadBulkRestartIsOurs() throws Exception {
        String accepted = "{\"operation_id\": \"" + OPERATION + "\", \"state\": \"QUEUED\"}";
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(202, accepted), Fakes.answer(202, accepted),
                Fakes.answer(202, accepted), Fakes.answer(202, accepted),
                Fakes.answer(202, "{\"id\": \"" + OPERATION + "\", \"refused\": []}"));
        try (MfxClient client = client(transport)) {
            client.terminals().restart(ID, "stuck on login", "k1").join();
            client.terminals().restart(ID, "stuck", "k2", Relaunch.builder().queue(false).confirmOpenPositions(true).build()).join();
            client.terminals().attachExpertAdvisor(ID, OPERATION, "EURUSD", ExpertAdvisorAttach.TimeframeEnum.H1, "trial", "k3").join();
            client.terminals().detachExpertAdvisor(ID, "done", "k4").join();
            BulkAction bulk = client.terminals().bulkRestart("night reset", "k5", BulkRestart.builder().farmId(FARM).build()).join();

            JsonNode restart = sent(transport.requests.get(0));
            assertTrue(restart.get("queue").asBoolean());
            assertFalse(restart.get("confirm_open_positions").asBoolean());
            JsonNode told = sent(transport.requests.get(1));
            assertFalse(told.get("queue").asBoolean());
            assertTrue(told.get("confirm_open_positions").asBoolean());
            JsonNode attach = sent(transport.requests.get(2));
            assertEquals("PUT", transport.requests.get(2).method());
            assertFalse(attach.get("queue").asBoolean());
            assertEquals("H1", attach.get("timeframe").asText());
            Fakes.Sent detach = transport.requests.get(3);
            assertEquals("DELETE", detach.method());
            assertEquals("/v1/terminals/" + ID + "/expert-advisor?reason=done&queue=false&confirm_open_positions=false", detach.pathWithQuery());
            assertEquals(0, detach.body().length);
            assertEquals("/v1/terminals:restart", transport.requests.get(4).pathWithQuery());
            assertEquals(OPERATION, bulk.getId());

            assertThrows(IllegalArgumentException.class, () -> client.terminals().bulkRestart("reason", "k", BulkRestart.builder().build()));
            assertThrows(IllegalArgumentException.class, () -> client.terminals().bulkRestart(" ", "k", BulkRestart.builder().farmId(FARM).build()));
            List<UUID> many = new ArrayList<>();
            for (int i = 0; i < 1001; i++) {
                many.add(UUID.randomUUID());
            }
            assertThrows(IllegalArgumentException.class, () -> client.terminals().bulkRestart("r", "k", BulkRestart.builder().terminalIds(many).build()));
            assertEquals(5, transport.requests.size());
        }
    }

    /**
     * Given a terminal drained with a reason, resumed and retired / When each is sent / Then drain posts
     * its reason and the confirmation, resume posts no body, retire is a DELETE of the terminal, each with
     * its own key, and a journal of 0 or 201 lines is refused here while 50 is asked for.
     */
    @Test
    void aTerminalIsDrainedResumedAndRetiredAndItsJournalReadWithinItsBounds() throws Exception {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(200, terminal("DRAINING", "")),
                Fakes.answer(200, terminal("REQUESTED", "")), Fakes.answer(200, terminal("DRAINING", "")),
                Fakes.answer(200, "{\"terminal_id\": \"" + ID + "\", \"from\": \"live\", \"lines\": []}"));
        try (MfxClient client = client(transport)) {
            client.terminals().drain(ID, "d1", DrainTerminal.builder().reason("weekend").build()).join();
            client.terminals().resume(ID, "r1").join();
            client.terminals().retire(ID, "x1").join();
            client.terminals().journal(ID, 50).join();

            assertEquals("{\"reason\":\"weekend\",\"confirm_open_positions\":false}",
                    new String(transport.requests.get(0).body(), java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("/v1/terminals/" + ID + "/resume", transport.requests.get(1).pathWithQuery());
            assertEquals(0, transport.requests.get(1).body().length);
            assertEquals("r1", transport.requests.get(1).header("Idempotency-Key"));
            assertEquals("DELETE", transport.requests.get(2).method());
            assertEquals("/v1/terminals/" + ID + "/journal?lines=50", transport.requests.get(3).pathWithQuery());
            assertThrows(IllegalArgumentException.class, () -> client.terminals().journal(ID, 0));
            assertThrows(IllegalArgumentException.class, () -> client.terminals().journal(ID, 201));
        }
    }

    /**
     * Given a terminal QUEUED with an expected start, then PROVISIONING, then ACTIVE, and an operation
     * running then SUCCEEDED / When each is waited for / Then a QUEUED terminal is read again at its
     * expected start (never more than a minute away), any other state after 2 s doubling, the wait ends
     * at ACTIVE and at a final operation state, and a wait whose time runs out returns the last read.
     */
    @Test
    void theWaitsReadAgainAtTheExpectedStartOrOnABackoffAndReturnTheLastRead() {
        String queued = terminal("QUEUED", ", \"queue\": {\"position\": 3, \"expected_start_at\": \"2026-11-17T09:00:40Z\"}");
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(200, queued), Fakes.answer(200, terminal("PROVISIONING", "")),
                Fakes.answer(200, terminal("ACTIVE", "")),
                Fakes.answer(200, "{\"id\": \"" + OPERATION + "\", \"state\": \"RELAUNCHING\"}"),
                Fakes.answer(200, "{\"id\": \"" + OPERATION + "\", \"state\": \"SUCCEEDED\"}"),
                Fakes.answer(200, terminal("LOGGING_IN", "")), Fakes.answer(200, terminal("LOGGING_IN", "")));
        try (MfxClient client = client(transport)) {
            TenantTerminal active = client.terminals().waitUntilActive(ID).join();
            assertEquals(TenantTerminal.StateEnum.ACTIVE, active.getState());
            assertTrue(Terminals.isUsable(active));
            assertEquals(List.of(Duration.ofSeconds(40), Duration.ofSeconds(4)), WAITS);

            WAITS.clear();
            TerminalOperation done = client.terminals().waitForOperation(ID, OPERATION).join();
            assertEquals(TerminalOperation.StateEnum.SUCCEEDED, done.getState());
            assertEquals(List.of(Duration.ofSeconds(2)), WAITS);

            WAITS.clear();
            TenantTerminal still = client.terminals().waitUntilActive(ID, Duration.ofSeconds(1)).join();
            assertEquals(TenantTerminal.StateEnum.LOGGING_IN, still.getState());
            assertFalse(Terminals.isUsable(still));
            assertEquals(List.of(Duration.ofSeconds(1)), WAITS);
            assertThrows(IllegalArgumentException.class, () -> client.terminals().waitUntilActive(ID, Duration.ofSeconds(-1)));
        }
    }

    /**
     * The next read of a QUEUED terminal is never sooner than 2 s nor later than a minute, and with no
     * expected start it is the backoff, capped at 15 s; a failed read ends the wait as its typed error.
     */
    @Test
    void theQueuedWaitIsBoundedAndAFailedReadEndsTheWait() throws Exception {
        TenantTerminal soon = MfxJson.MAPPER.readValue(terminal("QUEUED", ", \"queue\": {\"expected_start_at\": \"2026-11-17T08:59:00Z\"}"), TenantTerminal.class);
        TenantTerminal late = MfxJson.MAPPER.readValue(terminal("QUEUED", ", \"queue\": {\"expected_start_at\": \"2026-11-17T10:00:00Z\"}"), TenantTerminal.class);
        TenantTerminal plain = MfxJson.MAPPER.readValue(terminal("PROVISIONING", ""), TenantTerminal.class);
        assertEquals(Duration.ofSeconds(2), Terminals.nextWait(soon, Duration.ofSeconds(8), NOW));
        assertEquals(Duration.ofSeconds(60), Terminals.nextWait(late, Duration.ofSeconds(8), NOW));
        assertEquals(Duration.ofSeconds(15), Terminals.nextWait(plain, Duration.ofSeconds(32), NOW));

        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(200, terminal("REQUESTED", "")),
                Fakes.answer(404, "{\"error\":{\"code\":\"not_found\",\"message\":\"gone\"}}"));
        try (MfxClient client = client(transport)) {
            assertInstanceOf(NotFoundException.class, Fakes.failure(client.terminals().waitUntilActive(ID)));
        }
    }
}
