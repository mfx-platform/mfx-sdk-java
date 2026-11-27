package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every MFX Hub request is signed with Ed25519 over the API's canonical message, and the key that signs
 * it never reaches a log or a file.
 */
class SigningTest {

    /**
     * Given the API's published signing test vector / When its message is signed with the test key /
     * Then the signature is the published one, byte for byte, and it verifies against the test key's
     * public half.
     */
    @Test
    void thePublishedTestVectorIsReproducedByteForByte() throws Exception {
        byte[] message = Signing.message("1790000000000", "3f0c9a4e5b7d4c1e9a2b6d8f0a1c2e3f", "post",
                "/v1/terminals?dry_run=false",
                "{\"farm_id\":\"00000000-0000-0000-0000-000000000001\"}".getBytes(StandardCharsets.UTF_8));

        String signature = Fakes.key().sign(message);

        assertEquals("OLOeJhRskf6kvodIyvrrpI/Hwlg3MXn//TTc64zpcl0qACk3BOhNfMV8yrVthZUdUXlOCntJAIrfgHTNK6faDg==",
                signature);
        assertTrue(new String(message, StandardCharsets.UTF_8)
                .startsWith("1790000000000\n3f0c9a4e5b7d4c1e9a2b6d8f0a1c2e3f\nPOST\n"));
        assertTrue(Fakes.verifies(message, signature));
    }

    /**
     * Given a tenant's key / When it is printed, concatenated or serialised / Then it is *** or a
     * refusal, never the key; and it loads from PEM or the base64 of its 32 bytes, the same key either
     * way, while any other text, length or kind of key is refused.
     */
    @Test
    void aKeyIsNeverPrintedOrSerialisedAndLoadsFromPemOrBase64() {
        SigningKey key = SigningKey.parse(Fakes.PEM);

        assertEquals("***", key.toString());
        assertEquals("key ***", "key " + key);
        JsonMappingException written = assertThrows(JsonMappingException.class,
                () -> MfxJson.MAPPER.writeValueAsString(Map.of("key", key)));
        assertTrue(written.getCause() instanceof UnsupportedOperationException);
        assertThrows(UnsupportedOperationException.class, () -> MfxJson.MAPPER.readValue("{}", SigningKey.class));
        assertFalse(new RequestSigner("mfxk_1", key).toString().contains("AAECAwQ"));

        byte[] probe = "probe".getBytes(StandardCharsets.UTF_8);
        String fromSeed = Fakes.key().sign(probe);
        assertEquals(fromSeed, key.sign(probe));
        assertEquals(fromSeed, SigningKey.parse(Base64.getEncoder().encodeToString(Fakes.SEED)).sign(probe));

        assertThrows(IllegalArgumentException.class, () -> SigningKey.parse("not a key at all"));
        assertThrows(IllegalArgumentException.class, () -> SigningKey.parse(Base64.getEncoder().encodeToString(new byte[31])));
        assertThrows(IllegalArgumentException.class, () -> SigningKey.fromSeed(new byte[33]));
        assertThrows(IllegalArgumentException.class, () -> SigningKey.parse(
                "-----BEGIN PUBLIC KEY-----\n" + Fakes.PUBLIC_KEY + "\n-----END PUBLIC KEY-----"));
    }

    /**
     * Given a nonce that is short, long, or carries a newline or another character outside
     * A-Z a-z 0-9 _ - / When a message is built with it / Then it is refused before anything is signed,
     * because it is a line of the signed message.
     */
    @ParameterizedTest
    @MethodSource("badNonces")
    void aNonceThatCouldMoveALineOfTheMessageIsRefused(String nonce) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Signing.message("1790000000000", nonce, "GET", "/v1/terminals", new byte[0]));
        assertTrue(refused.getMessage().startsWith("nonce:"));
    }

    static Stream<String> badNonces() {
        return Stream.of("short", "0123456789abcdef\nGET", "0123456789abcdef 0", "0123456789abcdef".repeat(4) + "0");
    }

    /** The default nonce fits the shape the platform accepts, and differs every time. */
    @Test
    void theDefaultNonceFitsThePlatformShapeAndIsFreshEachRequest() {
        RequestSigner signer = new RequestSigner("mfxk_1", Fakes.key());

        Map<String, String> first = signer.headersFor("GET", "/v1/terminals", new byte[0]);
        Map<String, String> second = signer.headersFor("GET", "/v1/terminals", new byte[0]);

        assertTrue(first.get(Signing.NONCE_HEADER).matches("^[A-Za-z0-9_-]{16,64}$"));
        assertNotEquals(first.get(Signing.NONCE_HEADER), second.get(Signing.NONCE_HEADER));
        assertThrows(UnsupportedOperationException.class, () -> first.put("X", "y"));
    }
}
