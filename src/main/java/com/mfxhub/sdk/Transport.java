package com.mfxhub.sdk;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Sends one HTTP request and hands back its answer. The client's own is the JDK's
 * {@link java.net.http.HttpClient}; replace it to test, or to send through your own proxy or pool.
 *
 * <p>A request that gets no answer completes exceptionally with the {@link java.io.IOException} the JDK
 * would raise ({@link java.net.ConnectException} when the connection never opened,
 * {@link java.net.http.HttpTimeoutException} when it ran out of time), which the client turns into
 * {@link MfxTransportException} or {@link MfxTimeoutException}.
 */
@FunctionalInterface
public interface Transport {
    /**
     * Sends {@code request} exactly as it is built: the client has already signed it.
     *
     * @param request the signed request
     * @return its answer, the body as bytes
     */
    CompletableFuture<HttpResponse<byte[]>> send(HttpRequest request);
}
