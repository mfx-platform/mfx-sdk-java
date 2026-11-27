package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A new entry broke a Risk Management rule in force on the account (422 {@code risk_refused});
 * nothing was sent to the broker.
 */
public class RiskRefusedException extends ValidationException {
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
    public RiskRefusedException(String message, int status, String code, @Nullable Map<String, JsonNode> details,
            @Nullable Duration retryAfter) {
        super(message, status, code, details, retryAfter);
    }

    /**
     * What refused the entry, read from {@link #details()}.
     *
     * @return the refusal; a field the platform did not send, or sent as another kind, is null
     */
    public RiskRefusal refusal() {
        String reason = figure("reason");
        return new RiskRefusal(reason == null ? "risk_refused" : reason, figure("rule"), figure("policy_id"),
                whole("version"), figure("challenge_id"), whole("challenge_version"), figure("used"),
                figure("limit"), figure("would_be"));
    }

    private @Nullable String figure(String name) {
        JsonNode value = details().get(name);
        if (value == null) {
            return null;
        }
        if (value.isTextual()) {
            return value.textValue().isEmpty() ? null : value.textValue();
        }
        return value.isNumber() ? value.asText() : null;
    }

    private @Nullable Integer whole(String name) {
        JsonNode value = details().get(name);
        if (value == null) {
            return null;
        }
        if (value.isInt()) {
            return value.intValue();
        }
        if (value.isTextual()) {
            try {
                return Integer.valueOf(value.textValue());
            } catch (NumberFormatException notWhole) {
                return null;
            }
        }
        return null;
    }
}
