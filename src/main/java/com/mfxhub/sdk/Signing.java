package com.mfxhub.sdk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The four headers every MFX Hub request carries, and the exact message their signature covers.
 *
 * <p>A request is signed, not bearing a token. {@code X-MFX-Key} names the key. {@code X-MFX-Timestamp}
 * and {@code X-MFX-Nonce} make each request usable once. {@code X-MFX-Signature} is the base64 Ed25519
 * signature over {@code timestamp_ms\nnonce\nMETHOD\npath_with_query\nsha256_hex(body)}.
 *
 * <p>The timestamp is epoch milliseconds and must be within 30 s of the platform's clock. The nonce
 * must be unique per key for five minutes. It is one line of the signed message, so it is restricted
 * to {@code ^[A-Za-z0-9_-]{16,64}$}: a nonce with a newline in it could move the line boundaries and
 * sign one request as another. A retry is therefore a new signature, never the same one sent twice.
 */
public final class Signing {
    /** The header naming the key. */
    public static final String KEY_HEADER = "X-MFX-Key";
    /** The header carrying the request's time, in epoch milliseconds. */
    public static final String TIMESTAMP_HEADER = "X-MFX-Timestamp";
    /** The header carrying the request's single-use nonce. */
    public static final String NONCE_HEADER = "X-MFX-Nonce";
    /** The header carrying the base64 Ed25519 signature. */
    public static final String SIGNATURE_HEADER = "X-MFX-Signature";

    private static final Pattern NONCE = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

    private Signing() {
    }

    /**
     * The exact bytes an MFX Hub signature covers. One place, so the client and its tests agree.
     *
     * @param timestamp epoch milliseconds, as sent in {@code X-MFX-Timestamp}
     * @param nonce the request's nonce
     * @param method the HTTP method, in any case
     * @param pathWithQuery the path below the host, with its query exactly as sent
     * @param body the body's bytes; empty when there is none
     * @return the UTF-8 message
     * @throws IllegalArgumentException the nonce is not 16 to 64 characters of {@code A-Z a-z 0-9 _ -}.
     *     It is refused here rather than sent, because the platform answers a bad nonce with 401,
     *     which says nothing about why.
     */
    public static byte[] message(String timestamp, String nonce, String method, String pathWithQuery, byte[] body) {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(pathWithQuery, "pathWithQuery");
        Objects.requireNonNull(body, "body");
        if (!NONCE.matcher(nonce).matches()) {
            throw new IllegalArgumentException("nonce: a nonce is 16 to 64 characters of A-Z a-z 0-9 _ - ; got "
                    + nonce.length() + " characters. It is a line of the signed message, so anything else"
                    + " could move the line boundaries.");
        }
        String digest = HexFormat.of().formatHex(sha256(body));
        return String.join("\n", timestamp, nonce, method.toUpperCase(Locale.ROOT), pathWithQuery, digest)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] body) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(body);
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("every Java platform has SHA-256", absent);
        }
    }
}
