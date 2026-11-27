package com.mfxhub.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mfxhub.sdk.models.PlanChangeSummary;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 409 {@code service_agreement_not_accepted}: the organisation is moving to another plan and the new
 * plan's Service Agreement is not accepted yet, or a version of its plan's Service Agreement that must be
 * accepted is past its grace period unaccepted. Until it is accepted, adding a terminal, attaching an expert
 * advisor, generating or replacing an API key and asking for higher limits are refused.
 *
 * <p>It is <b>not retryable</b> unchanged. <b>Nothing running is touched</b>: every read, order and position
 * call works. What a caller does: show {@link #getMessage()} and {@link #acceptUrl()} to an owner or admin
 * of the organisation, who accepts in the customer portal; then send the request again.
 * {@code client.tenant().serviceAgreement()} says what is due. Where the Terms are held as well, this error
 * answers and {@link #documents()} lists every document due.
 */
public class ServiceAgreementNotAcceptedException extends ConflictException {
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
    public ServiceAgreementNotAcceptedException(String message, int status, String code, @Nullable Map<String, JsonNode> details,
            @Nullable Duration retryAfter) {
        super(message, status, code, details, retryAfter);
    }

    /**
     * The plan change awaiting acceptance, from {@code details.plan_change}.
     *
     * @return the change; null when the refusal is a version past its grace period with no change, or when
     *     the platform's description of it cannot be read
     */
    public @Nullable PlanChangeSummary planChange() {
        JsonNode raw = details().get("plan_change");
        if (raw == null || !raw.isObject()) {
            return null;
        }
        try {
            return MfxJson.MAPPER.treeToValue(raw, PlanChangeSummary.class);
        } catch (JsonProcessingException | IllegalArgumentException unreadable) {
            return null;
        }
    }

    /**
     * Each document version due, from {@code details.documents}: the agreement, and the Terms or the licence
     * too where they are held as well.
     *
     * @return the documents, in the platform's order; empty if it named none
     */
    public List<DueDocument> documents() {
        return TermsNotAcceptedException.documentsOf(details());
    }

    /**
     * Where an owner or admin accepts, in the customer portal, from {@code details.accept_url}.
     *
     * @return the page; null if the platform gave none, or gave one that is not a URL
     */
    public @Nullable URI acceptUrl() {
        String text = detailText("accept_url");
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return new URI(text);
        } catch (URISyntaxException notAUrl) {
            return null;
        }
    }
}
