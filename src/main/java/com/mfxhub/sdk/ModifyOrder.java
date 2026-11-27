package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What changes on a pending order: the change {@link Execution#modifyOrder} sends. What is not set keeps
 * the order's present value. The expiry has three states: not set keeps the order's own,
 * {@link Builder#goodTillCancelled()} clears it, and {@link Builder#expiresAt(OffsetDateTime)} sets it.
 */
public final class ModifyOrder {
    private final @Nullable BigDecimal price;
    private final @Nullable BigDecimal stopLimitPrice;
    private final @Nullable BigDecimal sl;
    private final @Nullable BigDecimal tp;
    private final boolean expiryChanges;
    private final @Nullable OffsetDateTime expiresAt;

    private ModifyOrder(Builder builder) {
        this.price = builder.price;
        this.stopLimitPrice = builder.stopLimitPrice;
        this.sl = builder.sl;
        this.tp = builder.tp;
        this.expiryChanges = builder.expiryChanges;
        this.expiresAt = builder.expiresAt;
    }

    /**
     * A change to build; at least one thing must change.
     *
     * @return a builder with nothing set
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nullable BigDecimal price() {
        return price;
    }

    @Nullable BigDecimal stopLimitPrice() {
        return stopLimitPrice;
    }

    @Nullable BigDecimal sl() {
        return sl;
    }

    @Nullable BigDecimal tp() {
        return tp;
    }

    boolean expiryChanges() {
        return expiryChanges;
    }

    @Nullable OffsetDateTime expiresAt() {
        return expiresAt;
    }

    /** Builds a {@link ModifyOrder}. */
    public static final class Builder {
        private @Nullable BigDecimal price;
        private @Nullable BigDecimal stopLimitPrice;
        private @Nullable BigDecimal sl;
        private @Nullable BigDecimal tp;
        private boolean expiryChanges;
        private @Nullable OffsetDateTime expiresAt;

        private Builder() {
        }

        /**
         * The order's new price.
         *
         * @param price exactly
         * @return this builder
         */
        public Builder price(BigDecimal price) {
            this.price = Objects.requireNonNull(price, "price");
            return this;
        }

        /**
         * A stop-limit order's new limit price; on any other order the terminal refuses it, as a result.
         *
         * @param stopLimitPrice exactly
         * @return this builder
         */
        public Builder stopLimitPrice(BigDecimal stopLimitPrice) {
            this.stopLimitPrice = Objects.requireNonNull(stopLimitPrice, "stopLimitPrice");
            return this;
        }

        /**
         * The new stop loss; {@code 0} clears it.
         *
         * @param sl exactly
         * @return this builder
         */
        public Builder sl(BigDecimal sl) {
            this.sl = Objects.requireNonNull(sl, "sl");
            return this;
        }

        /**
         * The new take profit; {@code 0} clears it.
         *
         * @param tp exactly
         * @return this builder
         */
        public Builder tp(BigDecimal tp) {
            this.tp = Objects.requireNonNull(tp, "tp");
            return this;
        }

        /**
         * A new expiry.
         *
         * @param expiresAt the moment
         * @return this builder
         */
        public Builder expiresAt(OffsetDateTime expiresAt) {
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            this.expiryChanges = true;
            return this;
        }

        /**
         * No expiry: good till cancelled (sent as {@code expires_at: null}).
         *
         * @return this builder
         */
        public Builder goodTillCancelled() {
            this.expiresAt = null;
            this.expiryChanges = true;
            return this;
        }

        /**
         * The change.
         *
         * @return a change that cannot be changed
         */
        public ModifyOrder build() {
            return new ModifyOrder(this);
        }
    }
}
