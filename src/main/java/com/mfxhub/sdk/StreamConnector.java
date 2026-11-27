package com.mfxhub.sdk;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Opens one connection: given the URL and the signed upgrade headers, a live socket. */
@FunctionalInterface
public interface StreamConnector {
    /**
     * Opens a connection to {@code url}, upgrading with {@code headers}.
     *
     * @param url the stream's URL
     * @param headers the signed upgrade headers and the {@code User-Agent}
     * @return the open connection
     */
    CompletableFuture<StreamConnection> connect(URI url, Map<String, String> headers);
}
