package com.mfxhub.sdk;

import java.util.concurrent.CompletableFuture;

/**
 * What the stream needs of a WebSocket, and nothing more: narrow, so the reconnect and gap logic can be
 * tested against a socket that closes exactly when a test says.
 */
public interface StreamConnection {
    /**
     * Sends one text frame.
     *
     * @param message the frame's text
     * @return completes when it is sent
     */
    CompletableFuture<Void> send(String message);

    /**
     * The next text frame. Asking for it is what lets the platform send it, so a reader that is slow
     * meets real backpressure.
     *
     * @return the frame; fails with {@link StreamClosedException} when the connection has ended, and
     *     may be cancelled by the stream when the caller closes it
     */
    CompletableFuture<String> receive();

    /**
     * Closes the connection.
     *
     * @return completes when it is closed
     */
    CompletableFuture<Void> close();
}
