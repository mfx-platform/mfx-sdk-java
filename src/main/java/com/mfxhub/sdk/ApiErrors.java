package com.mfxhub.sdk;

import static java.util.Map.entry;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** One mapping from the platform's description of a failure to its type. */
final class ApiErrors {
    @FunctionalInterface
    interface Make {
        MfxApiException make(String message, int status, String code, @Nullable Map<String, JsonNode> details,
                @Nullable Duration retryAfter);
    }

    record Kind(Class<? extends MfxApiException> type, Make make) {
    }

    private static Kind kind(Class<? extends MfxApiException> type, Make make) {
        return new Kind(type, make);
    }

    /** By the platform's code. */
    static final Map<String, Kind> BY_CODE = Map.ofEntries(
            entry("validation_error", kind(ValidationException.class, ValidationException::new)),
            entry("auth_error", kind(AuthException.class, AuthException::new)),
            entry("forbidden", kind(ForbiddenException.class, ForbiddenException::new)),
            entry("tenant_disabled", kind(TenantDisabledException.class, TenantDisabledException::new)),
            entry("tenant_suspended", kind(TenantSuspendedException.class, TenantSuspendedException::new)),
            entry("maintenance", kind(MaintenanceException.class, MaintenanceException::new)),
            entry("not_found", kind(NotFoundException.class, NotFoundException::new)),
            entry("conflict", kind(ConflictException.class, ConflictException::new)),
            entry("state_error", kind(StateException.class, StateException::new)),
            entry("placement_error", kind(PlacementException.class, PlacementException::new)),
            entry("risk_refused", kind(RiskRefusedException.class, RiskRefusedException::new)),
            entry("risk_override_required", kind(RiskOverrideRequiredException.class, RiskOverrideRequiredException::new)),
            entry("broker_not_supported", kind(UnsupportedBrokerException.class, UnsupportedBrokerException::new)),
            entry("rate_limited", kind(RateLimitException.class, RateLimitException::new)),
            entry("upstream_error", kind(UpstreamException.class, UpstreamException::new)),
            // 503 as well, told apart by its code alone: a store of the platform's own, with
            // Retry-After, where upstream_error is the tenant's terminal or broker.
            entry("dependency_unavailable", kind(DependencyUnavailableException.class, DependencyUnavailableException::new)),
            entry("internal_error", kind(InternalException.class, InternalException::new)),
            // 402, about neither the key nor the request: a payment is missing on the billing profile.
            entry("billing_payment_required", kind(BillingPaymentRequiredException.class, BillingPaymentRequiredException::new)),
            // 409s that hold additions until a legal document is accepted; nothing running is ever refused.
            entry("terms_not_accepted", kind(TermsNotAcceptedException.class, TermsNotAcceptedException::new)),
            entry("service_agreement_not_accepted", kind(ServiceAgreementNotAcceptedException.class,
                    ServiceAgreementNotAcceptedException::new)));

    private static final Kind EDGE = kind(EdgeException.class, EdgeException::new);

    /** By the HTTP status, when the code is unknown. */
    static final Map<Integer, Kind> BY_STATUS = Map.ofEntries(
            entry(400, BY_CODE.get("validation_error")),
            entry(401, BY_CODE.get("auth_error")),
            entry(402, kind(PaymentRequiredException.class, PaymentRequiredException::new)),
            entry(403, BY_CODE.get("forbidden")),
            entry(404, BY_CODE.get("not_found")),
            entry(409, BY_CODE.get("conflict")),
            entry(422, BY_CODE.get("validation_error")),
            entry(429, BY_CODE.get("rate_limited")),
            entry(500, BY_CODE.get("internal_error")),
            // The platform answers neither 502 nor 504; either is the edge's own page, reached only
            // without an MFX code.
            entry(502, EDGE),
            entry(503, BY_CODE.get("upstream_error")),
            entry(504, EDGE));

    private ApiErrors() {
    }

    /**
     * The typed error for a failure the platform described, on a call's answer or the stream. The code
     * decides; the status only when the code is one this version does not know.
     */
    static MfxApiException of(String code, String message, int status, @Nullable Map<String, JsonNode> details,
            @Nullable Duration retryAfter) {
        Kind kind = BY_CODE.get(code);
        if (kind == null) {
            kind = BY_STATUS.get(status);
        }
        if (kind == null) {
            return new MfxApiException(message, status, code.isEmpty() ? "MfxApiException" : code, details, retryAfter);
        }
        return kind.make().make(message, status, code.isEmpty() ? kind.type().getSimpleName() : code, details, retryAfter);
    }

    /**
     * The typed error for a failed answer; null for a successful one. {@code what} is the call in the
     * caller's words, used when the body carries no message of its own.
     */
    static @Nullable MfxApiException of(MfxAnswer answer, String what, @Nullable Duration retryAfter) {
        if (answer.status() >= 200 && answer.status() < 400) {
            return null;
        }
        String code = "";
        String message = "";
        Map<String, JsonNode> details = null;
        try {
            JsonNode error = MfxJson.MAPPER.readTree(answer.body()).path("error");
            if (error.isObject()) {
                code = text(error, "code");
                message = text(error, "message");
                JsonNode raw = error.get("details");
                if (raw != null && raw.isObject()) {
                    Map<String, JsonNode> read = new LinkedHashMap<>();
                    raw.fields().forEachRemaining(field -> read.put(field.getKey(), field.getValue()));
                    details = read;
                }
            }
        } catch (IOException notJson) {
            // An answer that is not JSON (an edge's HTML page) is still a typed failure, by status.
        }
        return of(code, message.isEmpty() ? what + " failed" : message, answer.status(), details, retryAfter);
    }

    private static String text(JsonNode error, String name) {
        JsonNode value = error.get(name);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.isTextual() ? value.textValue() : value.toString();
    }
}
