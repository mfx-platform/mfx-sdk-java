package com.mfxhub.sdk;

import org.jspecify.annotations.Nullable;

/**
 * Every failure this SDK throws. It is unchecked: a {@link java.util.concurrent.CompletableFuture} that
 * fails carries it as its cause, so {@code join()} throws a {@link java.util.concurrent.CompletionException}
 * whose {@code getCause()} is this.
 */
public class MfxException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * A failure described by {@code message}.
     *
     * @param message what failed
     */
    public MfxException(String message) {
        super(message);
    }

    /**
     * A failure described by {@code message}, caused by {@code cause}.
     *
     * @param message what failed
     * @param cause what made it fail
     */
    public MfxException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
