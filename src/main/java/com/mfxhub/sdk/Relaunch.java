package com.mfxhub.sdk;

import org.jspecify.annotations.Nullable;

/**
 * How a governed relaunch of a terminal goes ahead: {@link Terminals#restart}, and attaching or
 * detaching an expert advisor. {@code queue} left unset is each call's own default (true for a restart,
 * false for an expert advisor); {@code confirmOpenPositions} is false unless set.
 */
public final class Relaunch {
    private static final Relaunch DEFAULTS = builder().build();

    private final @Nullable Boolean queue;
    private final boolean confirmOpenPositions;

    private Relaunch(Builder builder) {
        this.queue = builder.queue;
        this.confirmOpenPositions = builder.confirmOpenPositions;
    }

    /**
     * Nothing set.
     *
     * @return each call's defaults
     */
    public static Relaunch defaults() {
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

    boolean queueOr(boolean otherwise) {
        return queue == null ? otherwise : queue;
    }

    boolean confirmOpenPositions() {
        return confirmOpenPositions;
    }

    /** Builds a {@link Relaunch}. */
    public static final class Builder {
        private @Nullable Boolean queue;
        private boolean confirmOpenPositions;

        private Builder() {
        }

        /**
         * Whether a valid relaunch refused only because the platform is busy waits its turn, answered 202
         * with the operation {@code QUEUED}, rather than being refused with {@code Retry-After}.
         *
         * @param queue whether to queue
         * @return this builder
         */
        public Builder queue(boolean queue) {
            this.queue = queue;
            return this;
        }

        /**
         * Says the caller knows the account has positions open: a relaunch drops the broker session for a
         * few seconds. Positions live at the broker and are not closed by it.
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
        public Relaunch build() {
            return new Relaunch(this);
        }
    }
}
