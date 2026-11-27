package com.mfxhub.sdk;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One request as the client sends it: the path is below the base URL. */
final class MfxRequest {
    private final String method;
    private final String path;
    private final List<Map.Entry<String, String>> query;
    private final byte @Nullable [] body;
    private final @Nullable String idempotencyKey;

    MfxRequest(String method, String path, List<Map.Entry<String, String>> query, byte @Nullable [] body,
            @Nullable String idempotencyKey) {
        this.method = Objects.requireNonNull(method, "method");
        this.path = Objects.requireNonNull(path, "path");
        this.query = List.copyOf(query);
        this.body = body == null ? null : body.clone();
        this.idempotencyKey = idempotencyKey;
    }

    static MfxRequest get(String path) {
        return new MfxRequest("GET", path, List.of(), null, null);
    }

    static MfxRequest get(String path, List<Map.Entry<String, String>> query) {
        return new MfxRequest("GET", path, query, null, null);
    }

    /**
     * A write: {@code body} is written by {@link MfxJson}, or none is sent when it is null. The key is
     * the caller's own, checked by {@link #key(String)} before anything is built.
     */
    static MfxRequest write(String method, String path, List<Map.Entry<String, String>> query, @Nullable Object body,
            @Nullable String idempotencyKey) {
        byte[] bytes = null;
        if (body != null) {
            try {
                bytes = MfxJson.REQUESTS.writeValueAsBytes(body);
            } catch (com.fasterxml.jackson.core.JsonProcessingException unwritable) {
                throw new IllegalArgumentException("the request body cannot be written as JSON: " + unwritable.getMessage(), unwritable);
            }
        }
        return new MfxRequest(method, path, query, bytes, idempotencyKey);
    }

    /**
     * The caller's idempotency key, as given: never null and never blank, and never one this SDK made
     * up, because a key made up per attempt makes a retry by the caller a second order.
     *
     * @throws NullPointerException there is none
     * @throws IllegalArgumentException it is blank
     */
    static String key(@Nullable String idempotencyKey) {
        if (idempotencyKey == null) {
            throw new NullPointerException("idempotencyKey: every write takes the caller's own idempotency key");
        }
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey: an idempotency key is the caller's own, and not blank");
        }
        return idempotencyKey;
    }

    String method() {
        return method;
    }

    String path() {
        return path;
    }

    List<Map.Entry<String, String>> query() {
        return query;
    }

    byte @Nullable [] body() {
        return body == null ? null : body.clone();
    }

    @Nullable String idempotencyKey() {
        return idempotencyKey;
    }
}
