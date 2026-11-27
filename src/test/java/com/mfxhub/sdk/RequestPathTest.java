package com.mfxhub.sdk;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The client's request path: every attempt is signed afresh over exactly what is sent, a read that is
 * rate limited is tried again as the platform says, and a write is sent once.
 */
class RequestPathTest {
    private final List<Duration> waits = new ArrayList<>();

    private MfxClient client(Fakes.Recording transport, int readRetries, @Nullable Clock clock) {
        MfxClient.Builder builder = MfxClient.builder(Fakes.BASE, "mfxk_C0NF0RMANCE1", Fakes.key())
                .transport(transport)
                .readRetries(readRetries)
                .random(() -> 0.5)
                .pause(wait -> {
                    waits.add(wait);
                    return CompletableFuture.completedFuture(null);
                });
        if (clock != null) {
            builder.clock(clock);
        }
        return builder.build();
    }

    private static void assertSignedOver(Fakes.Sent sent, String pathWithQuery) throws Exception {
        byte[] message = Signing.message(sent.header(Signing.TIMESTAMP_HEADER), sent.header(Signing.NONCE_HEADER),
                sent.method(), pathWithQuery, sent.body());
        assertEquals("mfxk_C0NF0RMANCE1", sent.header(Signing.KEY_HEADER));
        assertTrue(Fakes.verifies(message, sent.header(Signing.SIGNATURE_HEADER)), "the signature verifies over what was sent");
    }

    /**
     * Given a read with a query and a write with a body to a path with a colon in it / When each is sent
     * / Then each carries the key id, a timestamp, a nonce and a signature that verifies over its method,
     * its path with the base path and the query exactly as sent, and its body; the write carries its
     * idempotency key and its JSON content type; and the colon stays in the path.
     */
    @Test
    void everyRequestIsSignedOverExactlyWhatIsSent() throws Exception {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(200, "{}"), Fakes.answer(202, "{}"));
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_790_000_000_000L), ZoneOffset.UTC);
        try (MfxClient client = client(transport, MfxClient.DEFAULT_READ_RETRIES, clock)) {
            client.read(MfxRequest.get("/terminals", List.of(entry("limit", "2"), entry("cursor", "c 2/+~")))).join();
            byte[] body = "{\"reason\":\"nightly\",\"farm_id\":\"f-1\"}".getBytes(StandardCharsets.UTF_8);
            client.write(new MfxRequest("POST", "terminals:restart", List.of(), body, "bulk-1")).join();

            Fakes.Sent read = transport.requests.get(0);
            assertEquals("/v1/terminals?limit=2&cursor=c%202%2F%2B~", read.pathWithQuery());
            assertEquals("1790000000000", read.header(Signing.TIMESTAMP_HEADER));
            assertEquals(MfxClient.USER_AGENT, read.header("User-Agent"));
            assertEquals("application/json", read.header("Accept"));
            assertSignedOver(read, "/v1/terminals?limit=2&cursor=c%202%2F%2B~");
            assertEquals(0, read.body().length);
            assertFalse(read.has("Content-Type"));

            Fakes.Sent write = transport.requests.get(1);
            assertEquals(URI.create("https://api.example.test/v1/terminals:restart"), write.url());
            assertArrayEquals(body, write.body());
            assertEquals("bulk-1", write.header("Idempotency-Key"));
            assertEquals("application/json", write.header("Content-Type"));
            assertSignedOver(write, "/v1/terminals:restart");
            assertNotEquals(read.header(Signing.NONCE_HEADER), write.header(Signing.NONCE_HEADER));
        }
    }

    /**
     * Given a read answered 429 with Retry-After in seconds, then as an HTTP date, then with none / When
     * the client tries again / Then it waits the seconds the platform said, or a jittered backoff when
     * told nothing it reads (the API writes whole seconds only, so a date is not read), and each
     * attempt is signed again with a new nonce; after its retries are spent the
     * 429 is a RateLimitException carrying the last wait.
     */
    @Test
    void aRateLimitedReadIsTriedAgainAsThePlatformSaysAndSignedAgain() throws Exception {
        Fakes.Recording transport = new Fakes.Recording(Fakes.rateLimited("2"),
                Fakes.rateLimited("Sun, 15 Nov 2026 08:00:05 GMT"), Fakes.rateLimited(null), Fakes.rateLimited("1"));
        try (MfxClient client = client(transport, MfxClient.DEFAULT_READ_RETRIES, null)) {
            RateLimitException limited = assertInstanceOf(RateLimitException.class,
                    Fakes.failure(client.read(MfxRequest.get("/terminals/t-1"))));

            assertEquals(Duration.ofSeconds(1), limited.retryAfter());
            assertEquals(429, limited.status());
            assertEquals(4, transport.requests.size());
            assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofSeconds(2)), waits);
            assertEquals(4, transport.requests.stream().map(s -> s.header(Signing.NONCE_HEADER)).distinct().count());
            for (Fakes.Sent sent : transport.requests) {
                assertSignedOver(sent, "/v1/terminals/t-1");
            }
        }

        waits.clear();
        Fakes.Recording dated = new Fakes.Recording(Fakes.rateLimited("Sun, 15 Nov 2026 08:00:05 GMT"), Fakes.rateLimited("-3"));
        try (MfxClient late = client(dated, 1, null)) {
            RateLimitException told = assertInstanceOf(RateLimitException.class, Fakes.failure(late.read(MfxRequest.get("/terminals"))));
            assertEquals(List.of(Duration.ofMillis(500)), waits);
            assertEquals(Duration.ZERO, told.retryAfter());
        }
    }

    /**
     * Given a write answered 429 / When it is sent / Then it is sent once and the 429 is a
     * RateLimitException: a retried write is a second order.
     */
    @Test
    void aWriteIsNeverTriedAgain() {
        Fakes.Recording transport = new Fakes.Recording(Fakes.rateLimited("0"));
        try (MfxClient client = client(transport, MfxClient.DEFAULT_READ_RETRIES, null)) {
            assertInstanceOf(RateLimitException.class, Fakes.failure(client.write(
                    new MfxRequest("POST", "/terminals", List.of(), "{}".getBytes(StandardCharsets.UTF_8), "k-1"))));

            assertEquals(1, transport.requests.size());
            assertTrue(waits.isEmpty());
        }
    }

    /**
     * Given writes with a key, with a blank key and with none / When each is sent / Then the key goes as
     * given, a blank one is refused before anything is sent, and none is ever invented: a key made up by
     * the client would make the caller's own retry a second order.
     */
    @Test
    void anIdempotencyKeyIsTheCallersAndNeverInvented() {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(202, "{}"), Fakes.answer(200, "{}"));
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        try (MfxClient client = client(transport, MfxClient.DEFAULT_READ_RETRIES, null)) {
            client.write(new MfxRequest("POST", "/terminals", List.of(), body, "mine-1")).join();
            assertThrows(IllegalArgumentException.class,
                    () -> client.write(new MfxRequest("POST", "/terminals", List.of(), body, "  ")));
            client.write(new MfxRequest("POST", "/terminals/t-1/confirm", List.of(), body, null)).join();

            assertEquals(2, transport.requests.size());
            assertEquals("mine-1", transport.requests.get(0).header("Idempotency-Key"));
            assertFalse(transport.requests.get(1).has("Idempotency-Key"));
        }
    }

    /** The backoff doubles from 1 s and never passes 30 s; a relative or odd base is refused. */
    @Test
    void theBackoffDoublesToACapAndTheBaseUrlIsChecked() {
        try (MfxClient client = client(new Fakes.Recording(), MfxClient.DEFAULT_READ_RETRIES, null)) {
            assertEquals(Duration.ofMillis(500), client.backoff(0));
            assertEquals(Duration.ofSeconds(4), client.backoff(3));
            assertEquals(Duration.ofSeconds(15), client.backoff(10));
            assertEquals(URI.create("https://api.example.test/v1"), client.baseUrl());
            assertFalse(client.toString().contains("AAEC"));
        }
        SigningKey key = Fakes.key();
        assertEquals(URI.create("http://127.0.0.1:8080/v1"),
                MfxClient.builder(URI.create("http://127.0.0.1:8080/v1/"), "k", key).build().baseUrl());
        assertThrows(IllegalArgumentException.class, () -> MfxClient.builder(URI.create("/v1"), "k", key).build());
        assertThrows(IllegalArgumentException.class,
                () -> MfxClient.builder(URI.create("ftp://api.example.test/v1"), "k", key).build());
        assertThrows(IllegalArgumentException.class, () -> MfxClient.builder(Fakes.BASE, "k", key).readRetries(-1));
        assertThrows(IllegalArgumentException.class, () -> MfxClient.builder(Fakes.BASE, " ", key).build());
    }
}
