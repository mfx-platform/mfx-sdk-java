package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 402: a payment is missing. Not retryable unchanged: it ends when someone pays.
 * {@link BillingPaymentRequiredException} is the one the platform sends with an API key; a 402 whose code
 * this version does not know arrives as this class, by its status.
 */
public class PaymentRequiredException extends MfxApiException {
    private static final long serialVersionUID = 1L;

    /**
     * A failure the platform described.
     *
     * @param message the platform's sentence
     * @param status the HTTP status; 0 for an error sent on the stream
     * @param code the platform's error code
     * @param details what the platform said about it
     * @param retryAfter how long the platform asked the caller to wait
     */
    public PaymentRequiredException(String message, int status, String code, @Nullable Map<String, JsonNode> details,
            @Nullable Duration retryAfter) {
        super(message, status, code, details, retryAfter);
    }
}
