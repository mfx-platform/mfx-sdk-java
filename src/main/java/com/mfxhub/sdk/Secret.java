package com.mfxhub.sdk;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/**
 * A value that cannot reach a log by being printed, such as a broker password. It renders as {@code ***}
 * in {@link #toString()}, so revealing it takes the one explicit call that is easy to see in review:
 * {@link #reveal()}. It is not {@link java.io.Serializable}, and Jackson writes it as {@code ***}.
 */
public final class Secret {
    private final String value;

    private Secret(String value) {
        this.value = value;
    }

    /**
     * A secret.
     *
     * @param value the value, read from your own secret store
     * @return the secret
     */
    public static Secret of(String value) {
        return new Secret(Objects.requireNonNull(value, "value"));
    }

    /**
     * The value itself. Every call site is a place a secret can escape: keep them few.
     *
     * @return the value
     */
    public String reveal() {
        return value;
    }

    /** Written as {@code ***}, so a secret that reaches a serialiser by accident is not written. */
    @JsonValue
    String masked() {
        return "***";
    }

    @Override
    public String toString() {
        return "Secret(***)";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Secret secret && secret.value.equals(value);
    }

    @Override
    public int hashCode() {
        return Objects.hash("com.mfxhub.sdk.Secret", value);
    }
}
