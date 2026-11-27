package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * MFX Hub does not support this broker or this server now. {@link #refusedBy()} is {@code permission}
 * (this broker is not available) or {@code server_disabled} (try another server).
 */
public class UnsupportedBrokerException extends ConflictException {
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
    public UnsupportedBrokerException(String message, int status, String code, @Nullable Map<String, JsonNode> details,
            @Nullable Duration retryAfter) {
        super(message, status, code, details, retryAfter);
    }

    /**
     * Why: {@code permission} or {@code server_disabled}.
     *
     * @return the refusal's kind; null if the platform did not say
     */
    public @Nullable String refusedBy() {
        return detailText("refused_by");
    }

    /**
     * The platform's own reason code.
     *
     * @return the reason; null if the platform did not say
     */
    public @Nullable String reason() {
        return detailText("reason");
    }

    /**
     * The broker the refusal concerns.
     *
     * @return its id; null when the platform names none
     */
    public @Nullable String brokerId() {
        return detailText("broker_id");
    }

    /**
     * The broker server the refusal concerns.
     *
     * @return its id; null when the platform names none
     */
    public @Nullable String serverId() {
        return detailText("server_id");
    }
}
