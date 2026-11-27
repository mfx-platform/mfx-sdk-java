package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 409 {@code terms_not_accepted}: a version of the Terms of Service or the MetaTrader 5 licence the
 * organisation must accept is past its grace period unaccepted, so adding a terminal and attaching an
 * expert advisor are refused. Nothing running is touched.
 *
 * <p>It is <b>not retryable</b> unchanged: an owner or admin of the organisation accepts the version in the
 * customer portal, then the request is sent again. {@link #documents()} names each version due.
 */
public class TermsNotAcceptedException extends ConflictException {
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
    public TermsNotAcceptedException(String message, int status, String code, @Nullable Map<String, JsonNode> details,
            @Nullable Duration retryAfter) {
        super(message, status, code, details, retryAfter);
    }

    /**
     * Each document version due, from {@code details.documents}.
     *
     * @return the documents, in the platform's order; empty if it named none
     */
    public List<DueDocument> documents() {
        return documentsOf(details());
    }

    /** Reads {@code details.documents}; an entry without its key and number is left out, never a failure. */
    static List<DueDocument> documentsOf(Map<String, JsonNode> details) {
        JsonNode raw = details.get("documents");
        if (raw == null || !raw.isArray()) {
            return List.of();
        }
        List<DueDocument> documents = new ArrayList<>();
        for (JsonNode item : raw) {
            JsonNode document = item.get("document");
            JsonNode number = item.get("number");
            if (document == null || !document.isTextual() || number == null || !number.canConvertToInt()) {
                continue;
            }
            JsonNode plan = item.get("plan");
            JsonNode grace = item.get("grace_ends_at");
            documents.add(new DueDocument(document.textValue(), number.intValue(),
                    plan != null && plan.isTextual() && !plan.textValue().isEmpty() ? plan.textValue() : null,
                    grace != null && grace.isTextual() && !grace.textValue().isEmpty() ? EventData.instantOf(grace.textValue()) : null));
        }
        return List.copyOf(documents);
    }
}
