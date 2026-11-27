package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The stream resumes what can be resumed after a reconnect, counts what it missed, stops when the key no
 * longer signs, and lets one refused channel cost only that channel.
 */
class StreamTest {
    private static final UUID TERMINAL = UUID.fromString("0191f0c2-aaaa-7bbb-8ccc-000000000001");
    private static final UUID SERVER = UUID.fromString("3c2d3e4f-5a6b-7c8d-9e0f-102030405060");

    /** One scripted connection: frames to hand out, then a close, and what the SDK sent. */
    static final class Scripted implements StreamConnection {
        private final Deque<String> frames;
        private final @Nullable Integer closeWith;
        final List<String> sent = new ArrayList<>();
        Map<String, String> headers = Map.of();
        volatile boolean closed;

        Scripted(List<String> frames, @Nullable Integer closeWith) {
            this.frames = new ArrayDeque<>(frames);
            this.closeWith = closeWith;
        }

        @Override
        public CompletableFuture<Void> send(String message) {
            sent.add(message);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<String> receive() {
            String frame = frames.poll();
            if (frame != null) {
                return CompletableFuture.completedFuture(frame);
            }
            if (closeWith != null) {
                return CompletableFuture.failedFuture(new StreamClosedException(closeWith, "scripted"));
            }
            return new CompletableFuture<>(); // quiet: only close() ends it
        }

        @Override
        public CompletableFuture<Void> close() {
            closed = true;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static String msg(String channel, long seq, @Nullable String type) {
        return MfxJson.MAPPER.createObjectNode().put("op", "msg").put("ch", channel).put("seq", seq).put("type", type)
                .set("data", MfxJson.MAPPER.createObjectNode().put("n", seq)).toString();
    }

    private static String msg(String channel, long seq) {
        return msg(channel, seq, "terminal.state_changed");
    }

    private static MfxClient client() {
        return MfxClient.builder(URI.create("https://api.example.test/v1"), "mfxk_1", Fakes.key())
                .pause(wait -> CompletableFuture.completedFuture(null))
                .build();
    }

    private static StreamOptions over(List<Scripted> opened, int maxReconnects, Scripted... connections) {
        Deque<Scripted> queue = new ArrayDeque<>(List.of(connections));
        return StreamOptions.builder()
                .maxReconnects(maxReconnects)
                .connect((url, headers) -> {
                    Scripted next = queue.poll();
                    if (next == null) {
                        return CompletableFuture.failedFuture(new AssertionError("an unexpected reconnect"));
                    }
                    next.headers = headers;
                    opened.add(next);
                    return CompletableFuture.completedFuture(next);
                })
                .build();
    }

    private static List<StreamEvent> take(MfxStream stream, int count) {
        List<StreamEvent> events = new ArrayList<>();
        while (events.size() < count) {
            StreamEvent event = stream.next().join();
            if (event == null) {
                break;
            }
            events.add(event);
        }
        return events;
    }

    private static Throwable failure(CompletableFuture<?> future) {
        CompletionException failed = assertThrows(CompletionException.class, future::join);
        return failed.getCause();
    }

    /**
     * Given channel keys built from ids, a key the platform would refuse, and the client's base URL / When
     * each is checked and the stream URL derived / Then the keys are the platform's shapes, the bad ones
     * refused before anything is sent, and the stream is beside the API (api. becomes ws., https becomes
     * wss) with its upgrade signed over its path.
     */
    @Test
    void channelsAreKeyedByIdAndTheStreamUrlSitsBesideTheApi() throws Exception {
        assertEquals("md:" + SERVER + ":EURUSD.sd", Channel.md(SERVER, "EURUSD.sd"));
        assertEquals("candles:" + SERVER + ":EURUSD:H1", Channel.candles(SERVER, "EURUSD", "H1"));
        assertEquals("results:" + TERMINAL, Channel.results(TERMINAL));
        assertEquals("account:" + TERMINAL, Channel.account(TERMINAL));
        assertEquals("ea:" + TERMINAL, Channel.ea(TERMINAL));
        assertEquals("book:" + SERVER + ":EURUSD", Channel.book(SERVER, "EURUSD"));
        assertThrows(IllegalArgumentException.class, () -> Channel.md(SERVER, "EUR:USD"));
        assertThrows(IllegalArgumentException.class, () -> Channel.candles(SERVER, "EURUSD", "H2"));
        assertThrows(IllegalArgumentException.class, () -> Channel.checked("results:terminal-one"));

        assertEquals(URI.create("wss://ws.dev.mfxhub.com/v1/stream"), MfxStream.urlBeside(URI.create("https://api.dev.mfxhub.com/v1")));
        assertEquals(URI.create("ws://127.0.0.1:8080/v1/stream"), MfxStream.urlBeside(URI.create("http://127.0.0.1:8080/v1/")));

        Scripted connection = new Scripted(List.of(), null);
        try (MfxClient client = client();
                MfxStream stream = client.stream(over(new ArrayList<>(), 5, connection)).join()) {
            assertEquals("mfxk_1", connection.headers.get(Signing.KEY_HEADER));
            assertEquals(MfxClient.USER_AGENT, connection.headers.get("User-Agent"));
            byte[] message = Signing.message(connection.headers.get(Signing.TIMESTAMP_HEADER),
                    connection.headers.get(Signing.NONCE_HEADER), "GET", "/v1/stream", new byte[0]);
            assertTrue(Fakes.verifies(message, connection.headers.get(Signing.SIGNATURE_HEADER)));
            assertThrows(IllegalArgumentException.class, () -> stream.subscribe("not a channel"));
        }
    }

    /**
     * Given events and a terminal's results subscribed, and ticks, when the connection reaches its
     * lifetime (4009) and later the platform drops the stream for falling behind (4008) / When the stream
     * reconnects / Then events and results resume from their own last seq, ticks subscribe again with
     * their position forgotten, a seq gap is counted as dropped, and each close is counted.
     */
    @Test
    void aReconnectResumesEachChannelFromItsOwnSeqAndCountsTheRest() {
        String results = Channel.results(TERMINAL);
        String ticks = Channel.md(SERVER, "EURUSD");
        Scripted first = new Scripted(List.of(msg("events", 7), msg(results, 20), msg(ticks, 40, null)), MfxStream.CLOSE_LIFETIME);
        Scripted second = new Scripted(List.of(msg("events", 8), msg(results, 23), msg(ticks, 900, null)), MfxStream.CLOSE_SLOW_CONSUMER);
        Scripted third = new Scripted(List.of(msg("events", 9)), null);
        List<Scripted> opened = new ArrayList<>();
        try (MfxClient client = client(); MfxStream stream = client.stream(over(opened, 5, first, second, third)).join()) {
            stream.subscribe("events").join();
            stream.subscribe(results).join();
            stream.subscribe(ticks).join();

            List<StreamEvent> events = take(stream, 7);

            List<StreamEvent.Message> messages = events.stream().map(StreamEvent.Message.class::cast).toList();
            assertEquals(List.of(7L, 20L, 40L, 8L, 23L, 900L, 9L), messages.stream().map(StreamEvent.Message::seq).toList());
            assertNull(messages.get(2).type());
            assertEquals(40, messages.get(2).data().get("n").asInt());
            assertEquals(3, opened.size());
            assertEquals(List.of("{\"op\":\"resume\",\"ch\":\"events\",\"after_seq\":7}",
                    "{\"op\":\"resume\",\"ch\":\"" + results + "\",\"after_seq\":20}",
                    "{\"op\":\"subscribe\",\"ch\":\"" + ticks + "\"}"), second.sent);
            assertEquals(new StreamStats(2, 2, 0, 1, 1, 0), stream.stats());
            assertEquals(List.of("events", results, ticks), stream.channels());
        }
    }

    /**
     * Given a refused channel, a resync, an ack and an op this SDK does not know / When they arrive / Then
     * the refusal is delivered and costs only its channel (not asked for again on reconnect), the resync
     * is delivered and forgets the position, and the rest are skipped; an error that names no channel
     * fails the read typed; and a 4001 close fails it with AuthException instead of reconnecting.
     */
    @Test
    void aRefusalCostsOneChannelAResyncForgetsAndA4001StopsTheStream() {
        String results = Channel.results(TERMINAL);
        Scripted first = new Scripted(List.of(
                "{\"op\":\"ack\",\"ch\":\"events\"}",
                "{\"op\":\"error\",\"ch\":\"" + results + "\",\"error\":{\"code\":\"not_found\",\"message\":\"gone\","
                        + "\"details\":{\"terminal_id\":\"" + TERMINAL + "\"}}}",
                msg("events", 3),
                "{\"op\":\"resync\",\"ch\":\"events\"}",
                "{\"op\":\"teleport\",\"ch\":\"events\"}"), MfxStream.CLOSE_LIFETIME);
        Scripted second = new Scripted(List.of(msg("events", 100)), MfxStream.CLOSE_REAUTHENTICATE);
        try (MfxClient client = client(); MfxStream stream = client.stream(over(new ArrayList<>(), 5, first, second)).join()) {
            stream.subscribe("events").join();
            stream.subscribe(results).join();

            List<StreamEvent> events = take(stream, 3);

            StreamEvent.ChannelRefused refused = assertInstanceOf(StreamEvent.ChannelRefused.class, events.get(0));
            assertEquals(List.of(results, "not_found", "gone"), List.of(refused.channel(), refused.code(), refused.message()));
            JsonNode terminal = refused.details().get("terminal_id");
            assertEquals(TERMINAL.toString(), terminal.asText());
            assertEquals(3L, assertInstanceOf(StreamEvent.Message.class, events.get(1)).seq());
            assertEquals("events", assertInstanceOf(StreamEvent.Resync.class, events.get(2)).channel());
            assertEquals(100L, assertInstanceOf(StreamEvent.Message.class, stream.next().join()).seq());
            assertEquals(List.of("{\"op\":\"subscribe\",\"ch\":\"events\"}"), second.sent);
            assertEquals(new StreamStats(1, 0, 1, 0, 1, 1), stream.stats());

            AuthException revoked = assertInstanceOf(AuthException.class, failure(stream.next()));
            assertEquals(List.of("auth_error", "401", "scripted"), List.of(revoked.code(), Integer.toString(revoked.status()), revoked.getMessage()));
        }

        Scripted shut = new Scripted(List.of("{\"op\":\"error\",\"error\":{\"code\":\"tenant_disabled\",\"message\":\"off\"}}"), null);
        try (MfxClient client = client(); MfxStream stream = client.stream(over(new ArrayList<>(), 5, shut)).join()) {
            assertEquals(0, assertInstanceOf(TenantDisabledException.class, failure(stream.next())).status());
        }
    }

    /**
     * Given a read waiting on a quiet stream / When the stream is closed / Then the read ends with nothing
     * and no error, no reconnect is attempted, the connection is closed, and a stream that spends its
     * reconnects ends rather than spinning.
     */
    @Test
    void closingEndsAWaitingReadAndSpentReconnectsEndTheStream() throws Exception {
        Scripted quiet = new Scripted(List.of(), null);
        try (MfxClient client = client()) {
            MfxStream stream = client.stream(over(new ArrayList<>(), 5, quiet)).join();
            stream.subscribe("events").join();

            CompletableFuture<@Nullable StreamEvent> waiting = stream.next();
            Thread.sleep(50);
            stream.close();

            assertNull(waiting.get(2, TimeUnit.SECONDS));
            assertTrue(quiet.closed);
            assertEquals(0, stream.stats().reconnects());
            assertInstanceOf(StreamClosedException.class, failure(stream.subscribe("events")));

            Scripted[] dropping = {new Scripted(List.of(), 1006), new Scripted(List.of(), 1006), new Scripted(List.of(), 1006)};
            try (MfxStream limited = client.stream(over(new ArrayList<>(), 2, dropping)).join()) {
                assertNull(limited.next().join());
                assertEquals(2, limited.stats().reconnects());
                assertTrue(!limited.iterator().hasNext());
            }
        }
    }
}
