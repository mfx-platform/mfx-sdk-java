package com.mfxhub.sdk;

import com.mfxhub.sdk.models.WebhookEndpoint;
import com.mfxhub.sdk.models.WebhookEndpointCreated;
import com.mfxhub.sdk.models.WebhookEndpointList;
import com.mfxhub.sdk.models.WebhookFailure;
import com.mfxhub.sdk.models.WebhookReplayAccepted;
import com.mfxhub.sdk.models.WebhookSecretRotated;
import com.mfxhub.sdk.models.WebhookTestAccepted;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * The tenant's webhook endpoints ({@code client.webhooks()}; {@code hook:read} to read, {@code hook:write}
 * to change), and {@link #verify} for a receiver to check that a delivery is MFX Hub's.
 */
public final class Webhooks {
    /** The header that signs every delivery. */
    public static final String SIGNATURE_HEADER = "MFX-Signature";
    /** The header naming the event, the same for a delivery sent again. */
    public static final String EVENT_ID_HEADER = "MFX-Event-Id";
    /** The header naming the event's type. */
    public static final String EVENT_TYPE_HEADER = "MFX-Event-Type";
    /** The header counting the delivery's attempts. */
    public static final String ATTEMPT_HEADER = "MFX-Delivery-Attempt";
    /** How far the signed time may be from the receiver's clock, either way. */
    public static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final MfxClient client;

    Webhooks(MfxClient client) {
        this.client = client;
    }

    /**
     * Checks one delivery's {@code MFX-Signature} against the endpoint's secret, on this machine's clock.
     *
     * @param body the request body exactly as received
     * @param header the {@code MFX-Signature} header
     * @param secret the endpoint's secret as it was shown
     * @return the signed time, in unix seconds
     * @throws WebhookSignatureException the delivery is not provably MFX Hub's: refuse it
     */
    public static long verify(byte[] body, String header, Secret secret) {
        return verify(body, header, List.of(secret), TOLERANCE, Instant.now());
    }

    /**
     * Checks one delivery's {@code MFX-Signature}: genuine when any one of the secrets (the new and the
     * previous, for the day after a rotation) made any one of the header's {@code v1} values, and refused
     * when the signed time is more than {@code tolerance} from {@code now}, which is what stops a captured
     * delivery being sent again later. The comparison takes the same time whatever it finds.
     *
     * @param body the request body exactly as received
     * @param header the {@code MFX-Signature} header
     * @param secrets the endpoint's secrets
     * @param tolerance how far the signed time may be from {@code now}
     * @param now the receiver's clock
     * @return the signed time, in unix seconds
     * @throws WebhookSignatureException {@code malformed} (no {@code t} or no {@code v1}), {@code stale}
     *     (outside the tolerance) or {@code mismatch}
     */
    public static long verify(byte[] body, String header, List<Secret> secrets, Duration tolerance, Instant now) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(header, "header");
        Long stamp = null;
        List<String> signatures = new ArrayList<>();
        for (String item : header.split(",", -1)) {
            String part = item.strip();
            int equals = part.indexOf('=');
            String key = equals < 0 ? part : part.substring(0, equals);
            String value = equals < 0 ? "" : part.substring(equals + 1);
            if (key.equals("t") && !value.isEmpty() && value.chars().allMatch(c -> c >= '0' && c <= '9')) {
                stamp = Long.parseLong(value);
            } else if (key.equals("v1") && !value.isEmpty()) {
                signatures.add(value);
            }
        }
        if (stamp == null || signatures.isEmpty()) {
            throw new WebhookSignatureException("malformed", SIGNATURE_HEADER + " needs t= and v1=: '" + header + "'");
        }
        long apart = Math.abs(now.getEpochSecond() - stamp);
        if (apart > tolerance.toSeconds()) {
            throw new WebhookSignatureException("stale", "signed at " + stamp + ", " + apart + " s from this clock");
        }
        byte[] prefix = (stamp + ".").getBytes(StandardCharsets.US_ASCII);
        byte[] signed = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, signed, 0, prefix.length);
        System.arraycopy(body, 0, signed, prefix.length, body.length);
        for (Secret secret : secrets) {
            byte[] expected = HexFormat.of().formatHex(hmac(secret, signed)).getBytes(StandardCharsets.US_ASCII);
            for (String given : signatures) {
                if (MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.US_ASCII))) {
                    return stamp;
                }
            }
        }
        throw new WebhookSignatureException("mismatch", "no secret given made this signature");
    }

    private static byte[] hmac(Secret secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.reveal().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("HmacSHA256 is part of every JDK", unavailable);
        }
    }

    private static String endpoint(UUID endpointId) {
        return "/webhooks/" + Objects.requireNonNull(endpointId, "endpointId");
    }

    /**
     * Registers an HTTPS address for these event types, with no description.
     *
     * @param url the address
     * @param eventTypes such as {@code position.closed}, {@code terminal.*} or {@code *}
     * @param idempotencyKey the caller's key
     * @return the endpoint, with its signing secret: shown here and never again
     */
    public CompletableFuture<WebhookEndpointCreated> create(String url, List<String> eventTypes, String idempotencyKey) {
        return created(url, eventTypes, null, idempotencyKey);
    }

    /**
     * Registers an HTTPS address for these event types. The answer carries the signing {@code secret},
     * shown here and never again, so keep it before doing anything else. An address that resolves to a
     * private, loopback, link-local or metadata address is refused (422).
     *
     * @param url the address
     * @param eventTypes such as {@code position.closed}, {@code terminal.*} or {@code *}
     * @param description for people
     * @param idempotencyKey the caller's key
     * @return the endpoint, with its signing secret
     */
    public CompletableFuture<WebhookEndpointCreated> create(String url, List<String> eventTypes, String description, String idempotencyKey) {
        return created(url, eventTypes, Objects.requireNonNull(description, "description"), idempotencyKey);
    }

    private CompletableFuture<WebhookEndpointCreated> created(String url, List<String> eventTypes, @Nullable String description,
            String idempotencyKey) {
        Body body = Body.of().put("url", Objects.requireNonNull(url, "url"))
                .put("event_types", List.copyOf(Objects.requireNonNull(eventTypes, "eventTypes"))).put("description", description);
        return client.write("POST", "/webhooks", body.build(), idempotencyKey, WebhookEndpointCreated.class);
    }

    /**
     * Every endpoint of the tenant, each with how it is doing (delivered, retrying, dead).
     *
     * @return every endpoint
     */
    public CompletableFuture<List<WebhookEndpoint>> list() {
        return client.get("/webhooks", WebhookEndpointList.class)
                .thenApply(list -> list.getItems() == null ? List.of() : List.copyOf(list.getItems()));
    }

    /**
     * One endpoint; another tenant's id is a 404.
     *
     * @param endpointId the endpoint
     * @return the endpoint
     */
    public CompletableFuture<WebhookEndpoint> get(UUID endpointId) {
        return client.get(endpoint(endpointId), WebhookEndpoint.class);
    }

    /**
     * Changes the address or the types, or pauses and resumes it. What is not set is left as it is.
     *
     * @param endpointId the endpoint
     * @param idempotencyKey the caller's key
     * @param change what changes
     * @return the endpoint
     */
    public CompletableFuture<WebhookEndpoint> update(UUID endpointId, String idempotencyKey, UpdateWebhook change) {
        Objects.requireNonNull(change, "change");
        Boolean paused = change.paused();
        Body body = Body.of().put("url", change.url()).put("event_types", change.eventTypes()).put("description", change.description())
                .put("status", paused == null ? null : paused ? "paused" : "active");
        return client.write("PATCH", endpoint(endpointId), body.build(), idempotencyKey, WebhookEndpoint.class);
    }

    /**
     * Removes the endpoint and its secret. Nothing more is sent to it. The API takes no idempotency key here.
     *
     * @param endpointId the endpoint
     * @return completes when it is gone
     */
    public CompletableFuture<Void> delete(UUID endpointId) {
        return client.writeNothing("DELETE", endpoint(endpointId), List.of(), null, null);
    }

    /**
     * A new secret, shown once; the previous one also signs until {@code previous_secret_until}.
     *
     * @param endpointId the endpoint
     * @param idempotencyKey the caller's key
     * @return the new secret
     */
    public CompletableFuture<WebhookSecretRotated> rotateSecret(UUID endpointId, String idempotencyKey) {
        return client.write("POST", endpoint(endpointId) + "/rotate-secret", null, idempotencyKey, WebhookSecretRotated.class);
    }

    /**
     * Asks for a {@code webhook.test} event at the endpoint, delivered and signed like any other. The API
     * takes no idempotency key here.
     *
     * @param endpointId the endpoint
     * @return the acceptance
     */
    public CompletableFuture<WebhookTestAccepted> test(UUID endpointId) {
        return client.write("POST", endpoint(endpointId) + "/test", List.of(), null, null, WebhookTestAccepted.class);
    }

    /**
     * What did not arrive, in every state.
     *
     * @param endpointId the endpoint
     * @return every failed event
     */
    public Iterable<WebhookFailure> failures(UUID endpointId) {
        return failures(endpointId, Set.of(), null);
    }

    /**
     * What did not arrive: each failed event with every attempt on it, following the cursor to the end.
     * Iterating blocks on each page.
     *
     * @param endpointId the endpoint
     * @param state {@code retrying}, {@code dead} (gave up) or {@code delivered}, sent as one
     *     comma-separated value; every state when empty
     * @param limit the page size; the platform's default when null
     * @return every failed event
     */
    public Iterable<WebhookFailure> failures(UUID endpointId, Set<WebhookFailure.StateEnum> state, @Nullable Integer limit) {
        String path = endpoint(endpointId) + "/failures";
        Set<WebhookFailure.StateEnum> wanted = new LinkedHashSet<>(state);
        return Walk.of(cursor -> client.page(path, Query.of().joined("state", wanted).add("cursor", cursor).add("limit", limit).build(),
                WebhookFailure.class));
    }

    /**
     * Sends events again: every one after a stream cursor, or one failure; exactly one of the two.
     *
     * @param endpointId the endpoint
     * @param which after a cursor, or one failure
     * @param idempotencyKey the caller's key
     * @return the acceptance
     */
    public CompletableFuture<WebhookReplayAccepted> replay(UUID endpointId, ReplayWebhook which, String idempotencyKey) {
        Objects.requireNonNull(which, "which");
        Body body = Body.of().put("after", which.after()).put("failure_id", which.failureId());
        return client.write("POST", endpoint(endpointId) + "/replay", body.build(), idempotencyKey, WebhookReplayAccepted.class);
    }
}
