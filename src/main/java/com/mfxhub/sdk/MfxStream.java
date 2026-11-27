package com.mfxhub.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;

/**
 * The multiplexed stream: ticks, candles, events and results on one signed WebSocket.
 *
 * <p>A reconnect resumes what can be resumed and counts the rest: events, results, account and expert
 * channels carry a per-channel {@code seq} and are resumed from the last one seen; ticks and candles
 * subscribe again, and their gap is counted rather than hidden.
 *
 * <p>Nothing is buffered on the caller's behalf: a read takes the next frame from the socket, so a slow
 * consumer meets real backpressure and is closed by the platform with 4008, which is counted. Close code
 * 4001 (re-authenticate) fails the read with {@link AuthException}: every reconnect would meet the same
 * wall, and a client spinning on a revoked key looks like an outage.
 *
 * <p>Read it with {@link #next()}, or iterate it, which blocks on each event; close it with
 * {@link #close()}, or try-with-resources.
 */
public final class MfxStream implements AutoCloseable, Iterable<StreamEvent> {
    /** Re-authenticate: the key was rotated, expired or revoked. Reconnecting cannot help. */
    public static final int CLOSE_REAUTHENTICATE = 4001;
    /** The platform closed the stream for falling behind. */
    public static final int CLOSE_SLOW_CONSUMER = 4008;
    /** The connection reached its lifetime. */
    public static final int CLOSE_LIFETIME = 4009;

    /** One reader at a time, each on its own virtual thread: a blocked read costs no platform thread. */
    private static final ExecutorService READERS = Executors.newVirtualThreadPerTaskExecutor();
    private static final Map<String, JsonNode> EMPTY = Map.of();

    private final MfxClient client;
    private final StreamConnector connect;
    private final URI url;
    private final @Nullable Integer maxReconnects;
    private final Object lock = new Object();
    /** Every channel subscribed, in the order asked for, with the last seq seen (null when none). */
    private final LinkedHashMap<String, @Nullable Long> lastSeq = new LinkedHashMap<>();
    private volatile StreamStats stats = StreamStats.NONE;
    private volatile @Nullable StreamConnection connection;
    private volatile @Nullable CompletableFuture<?> waiting;
    private volatile boolean closed;

    MfxStream(MfxClient client, StreamOptions options) {
        this.client = client;
        StreamConnector given = options.connect();
        this.connect = given == null ? WebSocketConnection::open : given;
        URI at = options.url();
        this.url = at == null ? urlBeside(client.baseUrl()) : at;
        this.maxReconnects = options.maxReconnects();
    }

    /**
     * The stream's URL.
     *
     * @return the URL it connects to
     */
    public URI url() {
        return url;
    }

    /**
     * What has been missed so far.
     *
     * @return a snapshot
     */
    public StreamStats stats() {
        return stats;
    }

    /**
     * The channels currently subscribed.
     *
     * @return the keys, in the order they were asked for
     */
    public List<String> channels() {
        synchronized (lock) {
            return List.copyOf(lastSeq.keySet());
        }
    }

    CompletableFuture<MfxStream> open() {
        Map<String, String> headers = new LinkedHashMap<>(client.signer().headersFor("GET", url.getRawPath(), new byte[0]));
        headers.put("User-Agent", MfxClient.USER_AGENT);
        return connect.connect(url, Collections.unmodifiableMap(headers)).thenApply(opened -> {
            connection = opened;
            return this;
        });
    }

    /**
     * Subscribes to one channel; the platform acknowledges it on the stream. Subscribing twice is not an
     * error.
     *
     * @param channel a key from {@link Channel}
     * @return completes when the request is sent
     * @throws IllegalArgumentException not a channel key the platform accepts
     */
    public CompletableFuture<Void> subscribe(String channel) {
        Channel.checked(channel);
        synchronized (lock) {
            lastSeq.putIfAbsent(channel, null);
        }
        return send(frame("subscribe", channel, null));
    }

    /**
     * Asks the platform to bring one channel up to date from the last {@code seq} seen. On a channel that
     * does not replay, or one with no position yet, this subscribes instead.
     *
     * @param channel a key from {@link Channel}
     * @return completes when the request is sent
     */
    public CompletableFuture<Void> resume(String channel) {
        Channel.checked(channel);
        Long last;
        synchronized (lock) {
            last = lastSeq.get(channel);
        }
        if (last == null || !Channel.replays(channel)) {
            return subscribe(channel);
        }
        return send(frame("resume", channel, last));
    }

    /**
     * Stops watching a channel, and stops subscribing to it again after a reconnect.
     *
     * @param channel the key
     * @return completes when the request is sent
     */
    public CompletableFuture<Void> unsubscribe(String channel) {
        Objects.requireNonNull(channel, "channel");
        synchronized (lock) {
            lastSeq.remove(channel);
        }
        return send(frame("unsubscribe", channel, null));
    }

    /**
     * The next event, reconnecting underneath as needed.
     *
     * @return the event; null when the stream is over (closed by the caller, or its reconnects spent).
     *     Fails with {@link AuthException} when the platform closed with 4001, and with the typed
     *     {@link MfxApiException} when it sent an error about the connection itself
     */
    public CompletableFuture<@Nullable StreamEvent> next() {
        return CompletableFuture.supplyAsync(this::nextBlocking, READERS);
    }

    /**
     * Every event, blocking on each, until the stream is over. A failure is thrown as the
     * {@link MfxException} it is, not wrapped.
     *
     * @return the events, as they arrive
     */
    @Override
    public Iterator<StreamEvent> iterator() {
        return new Iterator<>() {
            private @Nullable StreamEvent ahead;
            private boolean over;

            @Override
            public boolean hasNext() {
                if (ahead == null && !over) {
                    ahead = nextBlocking();
                    over = ahead == null;
                }
                return ahead != null;
            }

            @Override
            public StreamEvent next() {
                StreamEvent event = hasNext() ? ahead : null;
                if (event == null) {
                    throw new NoSuchElementException("the stream is over");
                }
                ahead = null;
                return event;
            }
        };
    }

    /**
     * Closes the stream for good. A read that is waiting ends with nothing, and a reconnect under way is
     * given up: closing is the caller's decision, not a drop to recover from.
     */
    @Override
    public void close() {
        closed = true;
        CompletableFuture<?> pending = waiting;
        if (pending != null) {
            pending.cancel(false);
        }
        StreamConnection open = connection;
        connection = null;
        if (open != null) {
            quietly(open.close());
        }
    }

    @Override
    public String toString() {
        return "MfxStream(" + url + ", channels=" + channels().size() + ")";
    }

    private @Nullable StreamEvent nextBlocking() {
        while (true) {
            StreamConnection open = connection;
            if (open == null || closed) {
                return null;
            }
            String raw;
            try {
                raw = await(open.receive());
            } catch (CancellationException cancelled) {
                if (closed) {
                    return null;
                }
                throw cancelled;
            } catch (StreamClosedException dropped) {
                if (closed || !reopen(dropped)) {
                    return null;
                }
                continue;
            }
            StreamEvent event = interpret(raw);
            if (event != null) {
                return event;
            }
        }
    }

    /** Waits for {@code future} where close() can cancel it; its failure is thrown as it is. */
    private <T> T await(CompletableFuture<T> future) {
        waiting = future;
        try {
            if (closed) {
                future.cancel(false);
            }
            return future.join();
        } catch (CompletionException failed) {
            if (failed.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw failed;
        } finally {
            waiting = null;
        }
    }

    private @Nullable StreamEvent interpret(String raw) {
        JsonNode message;
        try {
            message = MfxJson.MAPPER.readTree(raw);
        } catch (JsonProcessingException notJson) {
            throw new MfxException("the stream sent a frame that is not JSON", notJson);
        }
        String op = text(message, "op");
        String channel = text(message, "ch");
        String ch = channel == null ? "" : channel;
        switch (op == null ? "" : op) {
            case "error" -> {
                JsonNode body = message.path("error");
                String code = text(body, "code");
                String said = text(body, "message");
                String sentence = said == null ? "stream error on " + (ch.isEmpty() ? "the connection" : ch) : said;
                Map<String, JsonNode> details = object(body, "details");
                if (!ch.isEmpty()) {
                    // One channel's refusal costs that channel, and is not asked for again on reconnect.
                    synchronized (lock) {
                        lastSeq.remove(ch);
                    }
                    stats = stats.withRefused();
                    return new StreamEvent.ChannelRefused(ch, code == null ? "stream_error" : code, sentence, details);
                }
                // About the connection itself, typed as a call's answer is. Status 0: no HTTP answer.
                throw ApiErrors.of(code == null ? "stream_error" : code, sentence, 0, details, null);
            }
            case "resync" -> {
                stats = stats.withResync();
                // No position to resume from any more: forgetting it stops the next reconnect asking
                // for the same dead seq.
                forget(ch);
                return new StreamEvent.Resync(ch);
            }
            case "msg" -> {
                Long seq = whole(message, "seq");
                account(ch, seq);
                return new StreamEvent.Message(ch, seq, text(message, "type"), object(message, "data"), whole(message, "base"));
            }
            default -> {
                // An ack is protocol, not data; an op added after this SDK shipped is ignored, not fatal.
                return null;
            }
        }
    }

    /** Counts what the sequence numbers prove was not delivered. */
    private void account(String channel, @Nullable Long seq) {
        if (seq == null) {
            return;
        }
        synchronized (lock) {
            if (!lastSeq.containsKey(channel)) {
                return;
            }
            Long last = lastSeq.get(channel);
            if (last != null && seq > last + 1) {
                stats = stats.withDropped(seq - last - 1);
            }
            lastSeq.put(channel, seq);
        }
    }

    private void forget(String channel) {
        synchronized (lock) {
            if (lastSeq.containsKey(channel)) {
                lastSeq.put(channel, null);
            }
        }
    }

    private boolean reopen(StreamClosedException dropped) {
        if (dropped.code() == CLOSE_REAUTHENTICATE) {
            connection = null;
            throw new AuthException(dropped.reason().isEmpty() ? "the stream's credentials were rotated, expired or revoked"
                    : dropped.reason(), 401, "auth_error", null, null);
        }
        if (dropped.code() == CLOSE_SLOW_CONSUMER) {
            stats = stats.withSlowConsumerDrop();
        } else if (dropped.code() == CLOSE_LIFETIME) {
            stats = stats.withLifetimeReconnect();
        }
        Integer most = maxReconnects;
        if (most != null && stats.reconnects() >= most) {
            connection = null;
            return false;
        }
        stats = stats.withReconnect();
        StreamConnection old = connection;
        connection = null;
        if (old != null) {
            quietly(old.close());
        }
        try {
            // The client's own jittered backoff, so the stream and the read path lean on MFX Hub alike.
            await(client.pause(client.backoff(stats.reconnects())));
            if (closed) {
                return false;
            }
            await(open());
        } catch (CancellationException cancelled) {
            if (closed) {
                return false;
            }
            throw cancelled;
        }
        if (closed) {
            close();
            return false;
        }
        resubscribe();
        return true;
    }

    /** Asks for everything again: resuming what replays, plain subscribing what does not. */
    private void resubscribe() {
        Map<String, @Nullable Long> asked;
        synchronized (lock) {
            asked = new LinkedHashMap<>(lastSeq);
        }
        for (Map.Entry<String, @Nullable Long> entry : asked.entrySet()) {
            String channel = entry.getKey();
            Long last = entry.getValue();
            if (Channel.replays(channel) && last != null) {
                await(send(frame("resume", channel, last)));
                continue;
            }
            if (!Channel.replays(channel)) {
                // Ticks and candles are never replayed, so the position means nothing across a reconnect;
                // dropping it stops the next gap being counted against a stale seq.
                forget(channel);
            }
            await(send(frame("subscribe", channel, null)));
        }
    }

    private CompletableFuture<Void> send(String frame) {
        StreamConnection open = connection;
        if (open == null) {
            return CompletableFuture.failedFuture(new StreamClosedException(StreamClosedException.DROPPED, "the stream is not open"));
        }
        return open.send(frame);
    }

    private static String frame(String op, String channel, @Nullable Long afterSeq) {
        ObjectNode frame = MfxJson.MAPPER.createObjectNode().put("op", op).put("ch", channel);
        if (afterSeq != null) {
            frame.put("after_seq", afterSeq);
        }
        return frame.toString();
    }

    /** A close is fire and forget: the connection is over either way, so its failure is nobody's. */
    @SuppressWarnings("FutureReturnValueIgnored")
    private static void quietly(CompletableFuture<Void> closing) {
        closing.exceptionally(ignored -> null);
    }

    private static @Nullable String text(JsonNode element, String name) {
        JsonNode value = element.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.textValue() : value.toString();
    }

    private static @Nullable Long whole(JsonNode element, String name) {
        JsonNode value = element.get(name);
        return value != null && value.isIntegralNumber() && value.canConvertToLong() ? value.longValue() : null;
    }

    private static Map<String, JsonNode> object(JsonNode element, String name) {
        JsonNode value = element.get(name);
        if (value == null || !value.isObject()) {
            return EMPTY;
        }
        Map<String, JsonNode> fields = new HashMap<>();
        value.fields().forEachRemaining(field -> fields.put(field.getKey(), field.getValue()));
        return fields;
    }

    /**
     * The stream URL beside the REST base: {@code api.} becomes {@code ws.}, so a client pointed at dev
     * cannot leave its stream pointed at production.
     */
    static URI urlBeside(URI baseUrl) {
        String url = baseUrl.toString();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.startsWith("https://")) {
            url = "wss://" + url.substring("https://".length());
        } else if (url.startsWith("http://")) {
            url = "ws://" + url.substring("http://".length());
        }
        int host = url.indexOf("://api.");
        if (host >= 0) {
            url = url.substring(0, host) + "://ws." + url.substring(host + "://api.".length());
        }
        return URI.create(url.endsWith("/stream") ? url : url + "/stream");
    }
}
