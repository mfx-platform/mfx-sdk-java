package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The terminal cannot be placed.
 */
public class PlacementException extends ConflictException {
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
    public PlacementException(String message, int status, String code, @Nullable Map<String, JsonNode> details,
            @Nullable Duration retryAfter) {
        super(message, status, code, details, retryAfter);
    }
}
