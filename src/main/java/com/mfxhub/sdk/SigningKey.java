package com.mfxhub.sdk;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tenant's Ed25519 private key. It renders as {@code ***} wherever it could be printed, and it is
 * never serialised.
 *
 * <p>The platform returns the private key once, when the key is issued, and never stores it. This type
 * never writes it anywhere: {@link #toString()} is {@code ***}, serialising it with Jackson throws, and
 * it is not {@link java.io.Serializable}. Signing is the JDK's own Ed25519 (JEP 339).
 */
@JsonSerialize(using = SigningKey.NeverSerialised.class)
@JsonDeserialize(using = SigningKey.NeverDeserialised.class)
public final class SigningKey {
    private static final int SEED_BYTES = 32;
    /** The PKCS #8 envelope of a raw Ed25519 private key (RFC 8410), before its 32 bytes. */
    private static final byte[] PKCS8_PREFIX = Base64.getDecoder().decode("MC4CAQAwBQYDK2VwBCIEIA==");
    private static final Pattern PEM = Pattern.compile(
            "-----BEGIN ([A-Z ]+)-----([A-Za-z0-9+/=\\s]+)-----END \\1-----");

    private final PrivateKey key;

    private SigningKey(PrivateKey key) {
        this.key = key;
    }

    /**
     * A key from its 32 raw private bytes.
     *
     * @param seed the 32 bytes
     * @return the key
     * @throws IllegalArgumentException the seed is not 32 bytes.
     */
    public static SigningKey fromSeed(byte[] seed) {
        Objects.requireNonNull(seed, "seed");
        if (seed.length != SEED_BYTES) {
            throw new IllegalArgumentException(
                    "an Ed25519 private key is " + SEED_BYTES + " bytes; this is " + seed.length);
        }
        byte[] der = new byte[PKCS8_PREFIX.length + SEED_BYTES];
        System.arraycopy(PKCS8_PREFIX, 0, der, 0, PKCS8_PREFIX.length);
        System.arraycopy(seed, 0, der, PKCS8_PREFIX.length, SEED_BYTES);
        return fromPkcs8(der);
    }

    /**
     * A key from its PEM (PKCS #8) form, or from the base64 of its 32 raw bytes.
     *
     * @param key the text
     * @return the key
     * @throws IllegalArgumentException the text is neither, or is a key of another kind.
     */
    public static SigningKey parse(String key) {
        Objects.requireNonNull(key, "key");
        String text = key.strip();
        if (text.startsWith("-----BEGIN")) {
            return fromPem(text);
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException notBase64) {
            throw new IllegalArgumentException(
                    "a signing key is a PEM private key or the base64 of its 32 raw bytes", notBase64);
        }
        return fromSeed(raw);
    }

    /**
     * A key from its PEM (PKCS #8 {@code PRIVATE KEY}) form.
     *
     * @param pem the PEM text
     * @return the key
     * @throws IllegalArgumentException the text is not one, or is a key of another kind.
     */
    public static SigningKey fromPem(String pem) {
        Objects.requireNonNull(pem, "pem");
        Matcher found = PEM.matcher(pem.strip());
        if (!found.matches()) {
            throw new IllegalArgumentException("not a PEM block");
        }
        if (!found.group(1).equals("PRIVATE KEY")) {
            throw new IllegalArgumentException("an MFX key is a PKCS #8 PRIVATE KEY, not " + found.group(1));
        }
        return fromPkcs8(Base64.getMimeDecoder().decode(found.group(2)));
    }

    private static SigningKey fromPkcs8(byte[] der) {
        try {
            return new SigningKey(KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der)));
        } catch (GeneralSecurityException notEd25519) {
            throw new IllegalArgumentException("an MFX key is Ed25519", notEd25519);
        }
    }

    /**
     * The base64 Ed25519 signature of {@code message}.
     *
     * @param message the bytes to sign, as {@link Signing#message} builds them
     * @return the signature, standard base64
     */
    public String sign(byte[] message) {
        Objects.requireNonNull(message, "message");
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(key);
            signer.update(message);
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException failed) {
            throw new IllegalStateException("the JDK's Ed25519 could not sign", failed);
        }
    }

    /**
     * Always {@code ***}: a key cannot reach a log by being printed.
     *
     * @return {@code ***}
     */
    @Override
    public String toString() {
        return "***";
    }

    static final class NeverSerialised extends JsonSerializer<SigningKey> {
        @Override
        public void serialize(SigningKey value, JsonGenerator generator, SerializerProvider provider) {
            throw new UnsupportedOperationException(
                    "a SigningKey is never serialised; load it from your own secret store");
        }
    }

    static final class NeverDeserialised extends JsonDeserializer<SigningKey> {
        @Override
        public SigningKey deserialize(JsonParser parser, DeserializationContext context) {
            throw new UnsupportedOperationException(
                    "a SigningKey is never deserialised; load it from your own secret store with SigningKey.parse");
        }
    }
}
