package com.mfxhub.sdk;

import org.jspecify.annotations.Nullable;

/** The stream's connection ended. {@link #code()} is the WebSocket close code, {@link #reason()} the platform's words. */
public class StreamClosedException extends MfxException {
    private static final long serialVersionUID = 1L;

    /** The code of a connection that dropped without a close frame. */
    public static final int DROPPED = 1006;

    private final int code;
    private final String reason;

    /**
     * A close with its code and the platform's reason.
     *
     * @param code the WebSocket close code
     * @param reason the platform's reason, as it was written; empty when it gave none
     */
    public StreamClosedException(int code, String reason) {
        this(code, reason, null);
    }

    /**
     * A close with its code, the platform's reason, and what reported it.
     *
     * @param code the WebSocket close code
     * @param reason the platform's reason; empty when it gave none
     * @param cause what reported the close
     */
    public StreamClosedException(int code, String reason, @Nullable Throwable cause) {
        super(reason.isEmpty() ? "stream closed " + code : "stream closed " + code + ": " + reason, cause);
        this.code = code;
        this.reason = reason;
    }

    /**
     * The WebSocket close code.
     *
     * @return the code; 1006 when the connection dropped without one
     */
    public int code() {
        return code;
    }

    /**
     * The platform's reason, as it was written.
     *
     * @return the reason; empty when it gave none
     */
    public String reason() {
        return reason;
    }
}
