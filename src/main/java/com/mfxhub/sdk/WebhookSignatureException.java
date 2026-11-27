package com.mfxhub.sdk;

/**
 * A webhook delivery is not provably from MFX Hub: refuse it ({@link Webhooks#verify}).
 */
public class WebhookSignatureException extends MfxException {
    private static final long serialVersionUID = 1L;

    private final String reason;

    /**
     * A delivery that failed its check.
     *
     * @param reason {@code malformed} (no {@code t} or no {@code v1}), {@code stale} (outside the
     *     tolerance) or {@code mismatch}
     * @param message what was wrong, for a person
     */
    public WebhookSignatureException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Why, in one word.
     *
     * @return {@code malformed}, {@code stale} or {@code mismatch}
     */
    public String reason() {
        return reason;
    }
}
