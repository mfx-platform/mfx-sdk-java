package com.mfxhub.sdk;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/**
 * The default connection, over the JDK's {@link WebSocket}. One frame is asked for each time
 * the stream reads one, so a reader that falls behind holds the platform back, as the protocol expects.
 */
final class WebSocketConnection implements StreamConnection, WebSocket.Listener {
    private final HttpClient http;
    private final Object lock = new Object();
    private final Deque<String> frames = new ArrayDeque<>();
    private final StringBuilder partial = new StringBuilder();
    private @Nullable CompletableFuture<String> reading;
    private @Nullable StreamClosedException ended;
    private @Nullable WebSocket socket;
    private CompletableFuture<?> sending = CompletableFuture.completedFuture(null);

    private WebSocketConnection(HttpClient http) {
        this.http = http;
    }

    static CompletableFuture<StreamConnection> open(URI url, Map<String, String> headers) {
        HttpClient http = HttpClient.newHttpClient();
        WebSocketConnection connection = new WebSocketConnection(http);
        WebSocket.Builder upgrade = http.newWebSocketBuilder();
        headers.forEach(upgrade::header);
        return upgrade.buildAsync(url, connection).handle((socket, failure) -> {
            if (failure != null) {
                http.shutdownNow();
                throw new java.util.concurrent.CompletionException(
                        new StreamClosedException(StreamClosedException.DROPPED, "the stream could not be opened: " + failure.getMessage(), failure));
            }
            synchronized (connection.lock) {
                connection.socket = socket;
            }
            return connection;
        });
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        // Nothing is asked for until the stream reads.
    }

    @Override
    public @Nullable CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        synchronized (lock) {
            partial.append(data);
            if (!last) {
                webSocket.request(1);
                return null;
            }
            String frame = partial.toString();
            partial.setLength(0);
            CompletableFuture<String> waiter = reading;
            reading = null;
            if (waiter != null) {
                waiter.complete(frame);
            } else {
                frames.add(frame);
            }
        }
        return null;
    }

    @Override
    public @Nullable CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        end(new StreamClosedException(statusCode, reason == null ? "" : reason));
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        end(new StreamClosedException(StreamClosedException.DROPPED, String.valueOf(error.getMessage()), error));
    }

    private void end(StreamClosedException why) {
        CompletableFuture<String> waiter;
        synchronized (lock) {
            if (ended == null) {
                ended = why;
            }
            waiter = reading;
            reading = null;
        }
        if (waiter != null) {
            waiter.completeExceptionally(why);
        }
        http.shutdownNow();
    }

    @Override
    public CompletableFuture<String> receive() {
        WebSocket open;
        CompletableFuture<String> next = new CompletableFuture<>();
        synchronized (lock) {
            String frame = frames.poll();
            if (frame != null) {
                return CompletableFuture.completedFuture(frame);
            }
            if (ended != null) {
                return CompletableFuture.failedFuture(ended);
            }
            reading = next;
            open = socket;
        }
        if (open != null) {
            open.request(1);
        }
        return next;
    }

    @Override
    public CompletableFuture<Void> send(String message) {
        synchronized (lock) {
            WebSocket open = socket;
            if (open == null || ended != null) {
                return CompletableFuture.failedFuture(ended != null ? ended
                        : new StreamClosedException(StreamClosedException.DROPPED, "the stream is not open"));
            }
            // The JDK's WebSocket takes one send at a time, so each waits for the one before.
            CompletableFuture<Void> sent = sending.handle((done, failed) -> null).thenCompose(ignored -> open.sendText(message, true))
                    .thenApply(ignored -> null);
            sending = sent;
            return sent;
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        WebSocket open;
        synchronized (lock) {
            open = socket;
        }
        if (open == null || open.isOutputClosed()) {
            http.shutdownNow();
            return CompletableFuture.completedFuture(null);
        }
        return open.sendClose(WebSocket.NORMAL_CLOSURE, "closed by the caller")
                .handle((done, failed) -> {
                    // Already gone or not: closing is done either way.
                    open.abort();
                    http.shutdownNow();
                    return null;
                });
    }
}
