package com.mfxhub.sdk;

import com.mfxhub.sdk.models.OrderPlace;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The optional arguments of {@link Execution#placeOrder(java.util.UUID, String, OrderPlace.SideEnum,
 * BigDecimal, String, PlaceOrder)}: a market order unless the type is set. Every price is a
 * {@link BigDecimal}, never a double.
 *
 * <pre>{@code
 * PlaceOrder.builder().orderType(OrderTypeEnum.LIMIT).price(new BigDecimal("1.10450")).sl(new BigDecimal("1.10200")).build()
 * }</pre>
 */
public final class PlaceOrder {
    private static final PlaceOrder MARKET = builder().build();

    private final OrderPlace.OrderTypeEnum orderType;
    private final @Nullable BigDecimal price;
    private final @Nullable BigDecimal stopLimitPrice;
    private final @Nullable BigDecimal sl;
    private final @Nullable BigDecimal tp;
    private final @Nullable Integer deviationPoints;
    private final @Nullable OffsetDateTime expiresAt;
    private final @Nullable Integer magic;
    private final @Nullable String comment;

    private PlaceOrder(Builder builder) {
        this.orderType = builder.orderType;
        this.price = builder.price;
        this.stopLimitPrice = builder.stopLimitPrice;
        this.sl = builder.sl;
        this.tp = builder.tp;
        this.deviationPoints = builder.deviationPoints;
        this.expiresAt = builder.expiresAt;
        this.magic = builder.magic;
        this.comment = builder.comment;
    }

    /**
     * A market order with nothing else set.
     *
     * @return the defaults
     */
    public static PlaceOrder market() {
        return MARKET;
    }

    /**
     * Options to build.
     *
     * @return a builder for a market order
     */
    public static Builder builder() {
        return new Builder();
    }

    OrderPlace.OrderTypeEnum orderType() {
        return orderType;
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

    @Nullable Integer deviationPoints() {
        return deviationPoints;
    }

    @Nullable OffsetDateTime expiresAt() {
        return expiresAt;
    }

    @Nullable Integer magic() {
        return magic;
    }

    @Nullable String comment() {
        return comment;
    }

    /** Builds a {@link PlaceOrder}. */
    public static final class Builder {
        private OrderPlace.OrderTypeEnum orderType = OrderPlace.OrderTypeEnum.MARKET;
        private @Nullable BigDecimal price;
        private @Nullable BigDecimal stopLimitPrice;
        private @Nullable BigDecimal sl;
        private @Nullable BigDecimal tp;
        private @Nullable Integer deviationPoints;
        private @Nullable OffsetDateTime expiresAt;
        private @Nullable Integer magic;
        private @Nullable String comment;

        private Builder() {
        }

        /**
         * The order's type.
         *
         * @param orderType market unless set
         * @return this builder
         */
        public Builder orderType(OrderPlace.OrderTypeEnum orderType) {
            this.orderType = Objects.requireNonNull(orderType, "orderType");
            return this;
        }

        /**
         * A pending order's price; a stop-limit order's stop price.
         *
         * @param price the price, exactly
         * @return this builder
         */
        public Builder price(BigDecimal price) {
            this.price = Objects.requireNonNull(price, "price");
            return this;
        }

        /**
         * A stop-limit order's limit price, placed when its stop wakes; refused with any other type.
         *
         * @param stopLimitPrice the price, exactly
         * @return this builder
         */
        public Builder stopLimitPrice(BigDecimal stopLimitPrice) {
            this.stopLimitPrice = Objects.requireNonNull(stopLimitPrice, "stopLimitPrice");
            return this;
        }

        /**
         * The stop loss.
         *
         * @param sl the level, exactly
         * @return this builder
         */
        public Builder sl(BigDecimal sl) {
            this.sl = Objects.requireNonNull(sl, "sl");
            return this;
        }

        /**
         * The take profit.
         *
         * @param tp the level, exactly
         * @return this builder
         */
        public Builder tp(BigDecimal tp) {
            this.tp = Objects.requireNonNull(tp, "tp");
            return this;
        }

        /**
         * The price deviation allowed, in points.
         *
         * @param deviationPoints the platform's default unless set
         * @return this builder
         */
        public Builder deviationPoints(int deviationPoints) {
            this.deviationPoints = deviationPoints;
            return this;
        }

        /**
         * When a pending order expires.
         *
         * @param expiresAt the moment
         * @return this builder
         */
        public Builder expiresAt(OffsetDateTime expiresAt) {
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            return this;
        }

        /**
         * The expert's magic number.
         *
         * @param magic the number
         * @return this builder
         */
        public Builder magic(int magic) {
            this.magic = magic;
            return this;
        }

        /**
         * The order's comment, at most {@link Execution#COMMENT_MAX} characters (Unicode code points): the
         * terminal cuts a longer one without saying so, so {@link Execution#placeOrder} refuses it with
         * {@link IllegalArgumentException} before anything is sent.
         *
         * @param comment the comment
         * @return this builder
         */
        public Builder comment(String comment) {
            this.comment = Objects.requireNonNull(comment, "comment");
            return this;
        }

        /**
         * The options.
         *
         * @return options that cannot be changed
         */
        public PlaceOrder build() {
            return new PlaceOrder(this);
        }
    }
}
