package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A planned maintenance window suspends this until {@link #endsAt()} (403 {@code maintenance}). Send
 * it again when the window ends, with the same idempotency key for a write.
 */
public class MaintenanceException extends ForbiddenException {
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
    public MaintenanceException(String message, int status, String code, @Nullable Map<String, JsonNode> details,
            @Nullable Duration retryAfter) {
        super(message, status, code, details, retryAfter);
    }

    /**
     * The window's id.
     *
     * @return the id; null if the platform did not say
     */
    public @Nullable String windowId() {
        return detailText("window_id");
    }

    /**
     * The window's label as people say it, such as {@code MW-0031}.
     *
     * @return the label; null if the platform did not say
     */
    public @Nullable String window() {
        return detailText("window");
    }

    /**
     * The window's current end. It can move.
     *
     * @return the moment; null if the platform did not say
     */
    public @Nullable OffsetDateTime endsAt() {
        return detailTime("ends_at");
    }

    /**
     * The suspended service that refused this call, such as {@code order_placement}.
     *
     * @return the service; null if the platform did not say
     */
    public @Nullable String service() {
        return detailText("service");
    }

    /**
     * Every service the window suspends.
     *
     * @return the services' names; empty when the platform did not say
     */
    public List<String> suspended() {
        JsonNode value = details().get("suspended");
        List<String> names = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(name -> {
                if (name.isTextual()) {
                    names.add(name.textValue());
                }
            });
        }
        return List.copyOf(names);
    }
}
