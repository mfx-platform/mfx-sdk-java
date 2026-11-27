package com.mfxhub.sdk;

import com.mfxhub.sdk.models.TradingAccount;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which trading accounts to list, and how many a page may hold: the arguments of
 * {@link TradingAccounts#page(String, ListTradingAccounts)} and {@link TradingAccounts#list(ListTradingAccounts)}.
 * Venues and states are each sent as one comma-separated value.
 */
public final class ListTradingAccounts {
    private static final ListTradingAccounts ALL = builder().build();

    private final @Nullable Integer limit;
    private final Set<TradingAccount.VenueEnum> venue;
    private final TradingAccount.@Nullable EnvironmentEnum environment;
    private final Set<TradingAccount.StateEnum> state;

    private ListTradingAccounts(Builder builder) {
        this.limit = builder.limit;
        this.venue = Collections.unmodifiableSet(new LinkedHashSet<>(builder.venue));
        this.environment = builder.environment;
        this.state = Collections.unmodifiableSet(new LinkedHashSet<>(builder.state));
    }

    /**
     * No filter.
     *
     * @return every account, in the platform's default page size
     */
    public static ListTradingAccounts all() {
        return ALL;
    }

    /**
     * A filter to build.
     *
     * @return a builder with nothing set
     */
    public static Builder builder() {
        return new Builder();
    }

    List<Map.Entry<String, String>> query(@Nullable String cursor) {
        return Query.page(cursor, limit).joined("venue", venue).add("environment", environment).joined("state", state).build();
    }

    /** Builds a {@link ListTradingAccounts}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private final Set<TradingAccount.VenueEnum> venue = new LinkedHashSet<>();
        private TradingAccount.@Nullable EnvironmentEnum environment;
        private final Set<TradingAccount.StateEnum> state = new LinkedHashSet<>();

        private Builder() {
        }

        /**
         * How many a page may hold.
         *
         * @param limit the size the platform is asked for
         * @return this builder
         */
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * The venues to list: an account is listed when it is on any of them.
         *
         * @param venues mt5, ctrader
         * @return this builder
         */
        public Builder venue(TradingAccount.VenueEnum... venues) {
            for (TradingAccount.VenueEnum one : venues) {
                venue.add(Objects.requireNonNull(one, "venue"));
            }
            return this;
        }

        /**
         * Live or demo accounts only.
         *
         * @param environment live or demo
         * @return this builder
         */
        public Builder environment(TradingAccount.EnvironmentEnum environment) {
            this.environment = Objects.requireNonNull(environment, "environment");
            return this;
        }

        /**
         * The states to list, in words that hold on every venue.
         *
         * @param states the states
         * @return this builder
         */
        public Builder state(TradingAccount.StateEnum... states) {
            for (TradingAccount.StateEnum one : states) {
                state.add(Objects.requireNonNull(one, "state"));
            }
            return this;
        }

        /**
         * The filter.
         *
         * @return a filter that cannot be changed
         */
        public ListTradingAccounts build() {
            return new ListTradingAccounts(this);
        }
    }
}
