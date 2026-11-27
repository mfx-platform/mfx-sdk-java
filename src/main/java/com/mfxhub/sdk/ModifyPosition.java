package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A position's new stop loss, take profit, or both: the change {@link Execution#modifyPosition} sends. */
public final class ModifyPosition {
    private final @Nullable BigDecimal sl;
    private final @Nullable BigDecimal tp;

    private ModifyPosition(Builder builder) {
        this.sl = builder.sl;
        this.tp = builder.tp;
    }

    /**
     * A change to build; at least one of the two is required.
     *
     * @return a builder with nothing set
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nullable BigDecimal sl() {
        return sl;
    }

    @Nullable BigDecimal tp() {
        return tp;
    }

    /** Builds a {@link ModifyPosition}. */
    public static final class Builder {
        private @Nullable BigDecimal sl;
        private @Nullable BigDecimal tp;

        private Builder() {
        }

        /**
         * The new stop loss; {@code 0} clears it.
         *
         * @param sl the level, exactly
         * @return this builder
         */
        public Builder sl(BigDecimal sl) {
            this.sl = Objects.requireNonNull(sl, "sl");
            return this;
        }

        /**
         * The new take profit; {@code 0} clears it.
         *
         * @param tp the level, exactly
         * @return this builder
         */
        public Builder tp(BigDecimal tp) {
            this.tp = Objects.requireNonNull(tp, "tp");
            return this;
        }

        /**
         * The change.
         *
         * @return a change that cannot be changed
         */
        public ModifyPosition build() {
            return new ModifyPosition(this);
        }
    }
}
