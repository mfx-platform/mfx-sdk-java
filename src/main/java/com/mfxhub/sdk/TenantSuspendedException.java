package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The operator has suspended this tenant: writes are refused now, reads until {@link #readsUntil()}.
 * Not retryable: it ends when the operator resumes the tenant.
 */
public class TenantSuspendedException extends ForbiddenException {
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
    public TenantSuspendedException(String message, int status, String code, @Nullable Map<String, JsonNode> details,
            @Nullable Duration retryAfter) {
        super(message, status, code, details, retryAfter);
    }

    /**
     * When the suspension began.
     *
     * @return the moment; null if the platform did not say
     */
    public @Nullable OffsetDateTime since() {
        return detailTime("since");
    }

    /**
     * When reads stop working too.
     *
     * @return the moment; null if the platform named no grace
     */
    public @Nullable OffsetDateTime readsUntil() {
        return detailTime("reads_until");
    }
}
