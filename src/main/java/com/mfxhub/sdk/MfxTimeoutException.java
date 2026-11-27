package com.mfxhub.sdk;

import org.jspecify.annotations.Nullable;

/** The request got no answer in time. A write that timed out is never retried: it may have arrived. */
public class MfxTimeoutException extends MfxTransportException {
    private static final long serialVersionUID = 1L;

    /**
     * A timeout, saying whether the request could have arrived.
     *
     * @param message what timed out
     * @param mayHaveReached false only when the connection never opened
     * @param cause the timeout as the HTTP client reported it
     */
    public MfxTimeoutException(String message, boolean mayHaveReached, @Nullable Throwable cause) {
        super(message, mayHaveReached, cause);
    }
}
