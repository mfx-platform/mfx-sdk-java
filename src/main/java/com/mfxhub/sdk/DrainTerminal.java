package com.mfxhub.sdk;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** The optional arguments of {@link Terminals#drain(java.util.UUID, String, DrainTerminal)}. */
public final class DrainTerminal {
    private static final DrainTerminal DEFAULTS = builder().build();

    private final @Nullable String reason;
    private final boolean confirmOpenPositions;

    private DrainTerminal(Builder builder) {
        this.reason = builder.reason;
        this.confirmOpenPositions = builder.confirmOpenPositions;
    }

    /**
     * Nothing set.
     *
     * @return no reason, open positions not confirmed
     */
    public static DrainTerminal defaults() {
        return DEFAULTS;
    }

    /**
     * Options to build.
     *
     * @return a builder with nothing set
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nullable String reason() {
        return reason;
    }

    boolean confirmOpenPositions() {
        return confirmOpenPositions;
    }

    /** Builds a {@link DrainTerminal}. */
    public static final class Builder {
        private @Nullable String reason;
        private boolean confirmOpenPositions;

        private Builder() {
        }

        /**
         * Why, for the audit log and the terminal's history; left out, it is recorded as not given.
         *
         * @param reason the reason
         * @return this builder
         */
        public Builder reason(String reason) {
            this.reason = Objects.requireNonNull(reason, "reason");
            return this;
        }

        /**
         * Required true when the account has positions open; they stay open at the broker while drained.
         *
         * @param confirmOpenPositions false unless set
         * @return this builder
         */
        public Builder confirmOpenPositions(boolean confirmOpenPositions) {
            this.confirmOpenPositions = confirmOpenPositions;
            return this;
        }

        /**
         * The options.
         *
         * @return options that cannot be changed
         */
        public DrainTerminal build() {
            return new DrainTerminal(this);
        }
    }
}
