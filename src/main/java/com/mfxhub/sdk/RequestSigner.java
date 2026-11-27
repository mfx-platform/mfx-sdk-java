package com.mfxhub.sdk;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Signs each request with a fresh timestamp and nonce. The client signs every attempt through here,
 * and so does the stream's WebSocket upgrade, so both put the same bytes under the same signature.
 */
public final class RequestSigner {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String keyId;
    private final SigningKey key;
    private final Clock clock;
    private final Supplier<String> nonce;

    /**
     * A signer for {@code keyId}.
     *
     * @param keyId the key's id, sent as {@code X-MFX-Key}
     * @param key the private key
     * @param clock the clock the timestamp is read from; the system clock when null
     * @param nonce makes each nonce; 16 random bytes in base64url when null
     */
    public RequestSigner(String keyId, SigningKey key, @Nullable Clock clock, @Nullable Supplier<String> nonce) {
        Objects.requireNonNull(keyId, "keyId");
        if (keyId.isBlank()) {
            throw new IllegalArgumentException("keyId: a key id is not blank");
        }
        this.keyId = keyId;
        this.key = Objects.requireNonNull(key, "key");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.nonce = nonce == null ? RequestSigner::newNonce : nonce;
    }

    /**
     * A signer for {@code keyId} on the system clock, with random nonces.
     *
     * @param keyId the key's id
     * @param key the private key
     */
    public RequestSigner(String keyId, SigningKey key) {
        this(keyId, key, null, null);
    }

    /**
     * The key's id.
     *
     * @return the id sent as {@code X-MFX-Key}
     */
    public String keyId() {
        return keyId;
    }

    /**
     * The four headers for one request, with a fresh timestamp and nonce.
     *
     * @param method the HTTP method
     * @param pathWithQuery the path below the host, with its query exactly as sent
     * @param body the body's bytes; empty when there is none
     * @return the headers, in a map that cannot be changed
     */
    public Map<String, String> headersFor(String method, String pathWithQuery, byte[] body) {
        String timestamp = Long.toString(clock.millis());
        String once = nonce.get();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(Signing.KEY_HEADER, keyId);
        headers.put(Signing.TIMESTAMP_HEADER, timestamp);
        headers.put(Signing.NONCE_HEADER, once);
        headers.put(Signing.SIGNATURE_HEADER, key.sign(Signing.message(timestamp, once, method, pathWithQuery, body)));
        return Collections.unmodifiableMap(headers);
    }

    @Override
    public String toString() {
        return "RequestSigner(keyId=" + keyId + ", key=***)";
    }

    private static String newNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
