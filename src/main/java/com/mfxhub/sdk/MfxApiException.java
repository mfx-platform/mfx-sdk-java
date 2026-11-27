package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * MFX Hub answered, and the answer was a failure.
 *
 * <p>The platform's {@code error.code} decides the type; the HTTP status decides only when the code is
 * one this version does not know, so a code the platform adds later still arrives typed.
 */
// toString is code (status): message, as in every MFX SDK; getMessage stays the platform's own sentence.
@SuppressWarnings("OverrideThrowableToString")
public class MfxApiException extends MfxException {
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final transient Map<String, JsonNode> details;
    private final @Nullable Duration retryAfter;

    /**
     * A failure the platform described.
     *
     * @param message the platform's sentence, as it was written
     * @param status the HTTP status; 0 for an error sent on the stream
     * @param code the platform's error code, as it arrived
     * @param details what the platform said about it, such as the field that was wrong; none when null
     * @param retryAfter how long the platform asked the caller to wait; null when it did not say
     */
    public MfxApiException(String message, int status, String code, @Nullable Map<String, JsonNode> details,
            @Nullable Duration retryAfter) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
        this.retryAfter = retryAfter;
    }

    /**
     * The HTTP status.
     *
     * @return the status; 0 for an error sent on the stream
     */
    public int status() {
        return status;
    }

    /**
     * The platform's error code.
     *
     * @return the code, or the type's name when the answer carried none
     */
    public String code() {
        return code;
    }

    /**
     * What the platform said about the failure.
     *
     * @return the details, in a map that cannot be changed
     */
    public Map<String, JsonNode> details() {
        return details == null ? Map.of() : details;
    }

    /**
     * How long the platform asked the caller to wait, from {@code Retry-After}.
     *
     * @return the wait; null when the platform did not say
     */
    public @Nullable Duration retryAfter() {
        return retryAfter;
    }

    /**
     * {@code code (status): message}, as every MFX SDK prints an API error; {@link #getMessage()} is
     * the platform's sentence alone, so it is shown to people unchanged.
     *
     * @return {@code code (status): message}
     */
    @Override
    public String toString() {
        return code + " (" + status + "): " + getMessage();
    }

    /**
     * A text field of {@link #details()}.
     *
     * @param name the field
     * @return its text; null when absent or not text
     */
    protected @Nullable String detailText(String name) {
        JsonNode value = details().get(name);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    /**
     * A moment in {@link #details()}.
     *
     * @param name the field
     * @return the moment, with its offset; null when absent or not one
     */
    protected @Nullable OffsetDateTime detailTime(String name) {
        String text = detailText(name);
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException notAMoment) {
            return null;
        }
    }
}
