package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An external leader's open ({@link Copy#sendOpenSignal}): its size, exactly one of a volume in lots of
 * the catalogue's standard contract or units of the base, by construction, and its optional fields. The
 * size is never below zero; a stop loss or take profit may be, as some instruments trade below zero.
 *
 * <pre>{@code
 * OpenSignal.volume(new BigDecimal("0.10")).sl(new BigDecimal("1.0950")).build()
 * }</pre>
 */
public final class OpenSignal {
    private final @Nullable BigDecimal volume;
    private final @Nullable BigDecimal units;
    private final @Nullable String instrumentKey;
    private final @Nullable BigDecimal sl;
    private final @Nullable BigDecimal tp;
    private final @Nullable OffsetDateTime time;
    private final @Nullable Map<String, ?> account;

    private OpenSignal(Builder builder) {
        this.volume = builder.volume;
        this.units = builder.units;
        this.instrumentKey = builder.instrumentKey;
        this.sl = builder.sl;
        this.tp = builder.tp;
        this.time = builder.time;
        this.account = builder.account;
    }

    /**
     * An open of this many lots of the catalogue's standard contract.
     *
     * @param volume never below zero
     * @return a builder for the rest
     */
    public static Builder volume(BigDecimal volume) {
        return new Builder(Objects.requireNonNull(volume, "volume"), null);
    }

    /**
     * An open of this many units of the base.
     *
     * @param units never below zero
     * @return a builder for the rest
     */
    public static Builder units(BigDecimal units) {
        return new Builder(null, Objects.requireNonNull(units, "units"));
    }

    @Nullable BigDecimal volume() {
        return volume;
    }

    @Nullable BigDecimal units() {
        return units;
    }

    @Nullable String instrumentKey() {
        return instrumentKey;
    }

    @Nullable BigDecimal sl() {
        return sl;
    }

    @Nullable BigDecimal tp() {
        return tp;
    }

    @Nullable OffsetDateTime time() {
        return time;
    }

    @Nullable Map<String, ?> account() {
        return account;
    }

    /** Builds an {@link OpenSignal}. */
    public static final class Builder {
        private final @Nullable BigDecimal volume;
        private final @Nullable BigDecimal units;
        private @Nullable String instrumentKey;
        private @Nullable BigDecimal sl;
        private @Nullable BigDecimal tp;
        private @Nullable OffsetDateTime time;
        private @Nullable Map<String, ?> account;

        private Builder(@Nullable BigDecimal volume, @Nullable BigDecimal units) {
            this.volume = volume;
            this.units = units;
        }

        /**
         * The venue-neutral instrument key, such as {@code fx:EURUSD}.
         *
         * @param instrumentKey the key
         * @return this builder
         */
        public Builder instrumentKey(String instrumentKey) {
            this.instrumentKey = Objects.requireNonNull(instrumentKey, "instrumentKey");
            return this;
        }

        /**
         * The stop loss, a price; it may be below zero.
         *
         * @param sl exactly
         * @return this builder
         */
        public Builder sl(BigDecimal sl) {
            this.sl = Objects.requireNonNull(sl, "sl");
            return this;
        }

        /**
         * The take profit, a price; it may be below zero.
         *
         * @param tp exactly
         * @return this builder
         */
        public Builder tp(BigDecimal tp) {
            this.tp = Objects.requireNonNull(tp, "tp");
            return this;
        }

        /**
         * When the leader opened it.
         *
         * @param time the moment
         * @return this builder
         */
        public Builder time(OffsetDateTime time) {
            this.time = Objects.requireNonNull(time, "time");
            return this;
        }

        /**
         * The leader's account, in the contract's names.
         *
         * @param account the account's fields
         * @return this builder
         */
        public Builder account(Map<String, ?> account) {
            this.account = Collections.unmodifiableMap(new LinkedHashMap<>(account));
            return this;
        }

        /**
         * The open.
         *
         * @return an open that cannot be changed
         */
        public OpenSignal build() {
            return new OpenSignal(this);
        }
    }
}
