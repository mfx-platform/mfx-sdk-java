package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 402 {@code billing_payment_required}: a payment is missing on the billing profile the request falls
 * under, and nothing was done.
 *
 * <p>It is <b>not retryable</b>: sending the same request again changes nothing until the payment is made.
 * At {@code stage} {@code reminder} it comes only from a request that adds something billed. Once
 * {@code stage} is {@code suspended} it can come from <b>any write</b> on the tenant or the profile, whatever
 * route it was sent to. <b>Reads keep working</b> throughout, the billing reads included.
 *
 * <p>What a caller does: show {@link #getMessage()} and {@link #action()} to a person; once paid, send the
 * request again (a write under the same idempotency key). Execution never fails: an order refused for
 * billing completes as a refused {@link ExecutionResult} whose {@code detail} carries this error's text.
 * The cause of the standing is {@link #billingCause()}, never {@code getCause()}, which is the Java
 * exception's own.
 */
public class BillingPaymentRequiredException extends PaymentRequiredException {
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
    public BillingPaymentRequiredException(String message, int status, String code, @Nullable Map<String, JsonNode> details,
            @Nullable Duration retryAfter) {
        super(message, status, code, details, retryAfter);
    }

    private @Nullable String nonEmpty(String name) {
        JsonNode value = details().get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.isTextual() ? value.textValue() : value.toString();
        return text.isEmpty() ? null : text;
    }

    private @Nullable OffsetDateTime moment(String name) {
        String text = detailText(name);
        return text == null || text.isEmpty() ? null : EventData.instantOf(text);
    }

    /**
     * The tenant the refusal concerns.
     *
     * @return its id; null if the platform did not say
     */
    public @Nullable String tenantId() {
        return nonEmpty("tenant_id");
    }

    /**
     * The billing profile the request falls under; on Standard, its one profile.
     *
     * @return its id; null if the platform did not say
     */
    public @Nullable String billingProfileId() {
        return nonEmpty("billing_profile_id");
    }

    /**
     * {@code reminder} (only what adds something billed is refused) or {@code suspended} (every write on
     * the profile's accounts is). Read a word you do not know as text.
     *
     * @return the stage; null if the platform did not say
     */
    public @Nullable String stage() {
        return detailText("stage");
    }

    /**
     * Why: {@code balance_used_up} or {@code invoice_overdue}. Named so it cannot be read as
     * {@link Throwable#getCause()}.
     *
     * @return the cause; null if the platform did not say
     */
    public @Nullable String billingCause() {
        return detailText("cause");
    }

    /**
     * When the stage began.
     *
     * @return the moment, with its offset (UTC when the platform wrote none); null if not said
     */
    public @Nullable OffsetDateTime since() {
        return moment("since");
    }

    /**
     * The first suspended moment.
     *
     * @return the moment; null if not said
     */
    public @Nullable OffsetDateTime suspendsAt() {
        return moment("suspends_at");
    }

    /**
     * The last moment a payment avoids the suspension.
     *
     * @return the moment; null if not said
     */
    public @Nullable OffsetDateTime payBy() {
        return moment("pay_by");
    }

    /**
     * What clears it, with the portal page that does it.
     *
     * @return the action; null if the platform named none, or named one without its kind and page
     */
    public @Nullable PaymentAction action() {
        JsonNode raw = details().get("action");
        if (raw == null || !raw.isObject()) {
            return null;
        }
        JsonNode kind = raw.get("kind");
        JsonNode path = raw.get("portal_path");
        if (kind == null || !kind.isTextual() || path == null || !path.isTextual()) {
            return null;
        }
        BigDecimal amount = null;
        JsonNode figure = raw.get("amount_usd");
        if (figure != null && figure.isTextual() && !figure.textValue().isEmpty()) {
            try {
                amount = new BigDecimal(figure.textValue());
            } catch (NumberFormatException notDecimal) {
                amount = null;
            }
        }
        JsonNode invoice = raw.get("invoice_id");
        String invoiceId = invoice == null || invoice.isNull() ? null : invoice.isTextual() ? invoice.textValue() : invoice.toString();
        return new PaymentAction(kind.textValue(), path.textValue(), amount, invoiceId == null || invoiceId.isEmpty() ? null : invoiceId);
    }
}
