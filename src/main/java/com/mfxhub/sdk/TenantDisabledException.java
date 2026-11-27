package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The operator has disabled this tenant: every call is refused until it is enabled. Retrying
 * changes nothing; {@link #getMessage()} is what the operator wrote for the tenant.
 */
public class TenantDisabledException extends ForbiddenException {
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
    public TenantDisabledException(String message, int status, String code, @Nullable Map<String, JsonNode> details,
            @Nullable Duration retryAfter) {
        super(message, status, code, details, retryAfter);
    }

    /**
     * When the tenant was disabled.
     *
     * @return the moment; null if the platform did not say
     */
    public @Nullable OffsetDateTime since() {
        return detailTime("since");
    }
}
