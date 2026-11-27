package com.mfxhub.sdk;

import org.jspecify.annotations.Nullable;

/**
 * The request never got an answer: DNS, TCP, TLS, a dropped connection.
 *
 * <p>{@link #mayHaveReached()} is false only when the connection never opened, so nothing was sent. A
 * connection that dropped after the request was written may have delivered it, and an order sent that
 * way may already be a position.
 */
public class MfxTransportException extends MfxException {
    private static final long serialVersionUID = 1L;

    private final boolean mayHaveReached;

    /**
     * A transport failure, saying whether the request could have arrived.
     *
     * @param message what failed
     * @param mayHaveReached false only when nothing was sent
     * @param cause what made it fail
     */
    public MfxTransportException(String message, boolean mayHaveReached, @Nullable Throwable cause) {
        super(message, cause);
        this.mayHaveReached = mayHaveReached;
    }

    /**
     * Whether the request could have arrived.
     *
     * @return false only when nothing was sent
     */
    public boolean mayHaveReached() {
        return mayHaveReached;
    }
}
