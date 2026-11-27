package com.mfxhub.sdk;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which accounts of a challenge {@link Risk#challengeAccountsPage(java.util.UUID, String, ListChallengeAccounts)} and {@link Risk#challengeAccounts(java.util.UUID, ListChallengeAccounts)} list, by daily loss used unless sorted otherwise. States and rules are sent as repeated parameters.
 */
public final class ListChallengeAccounts {
    private static final ListChallengeAccounts ALL = builder().build();

    private final @Nullable Integer limit;
    private final Set<State> state;
    private final Set<Rule> rule;
    private final @Nullable Boolean belowMinDays;
    private final Sort sort;

    private ListChallengeAccounts(Builder builder) {
        this.limit = builder.limit;
        this.state = Collections.unmodifiableSet(new LinkedHashSet<>(builder.state));
        this.rule = Collections.unmodifiableSet(new LinkedHashSet<>(builder.rule));
        this.belowMinDays = builder.belowMinDays;
        this.sort = builder.sort;
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListChallengeAccounts all() {
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

    /** The query, the cursor first. */
    List<Map.Entry<String, String>> query(@Nullable String cursor) {
        return Query.of().add("cursor", cursor).add("limit", limit).repeated("state", state).repeated("rule", rule).add("below_min_days", belowMinDays).add("sort", sort).build();
    }

    /** A challenge account's state. */
    public enum State implements LowerWire {
        /** In progress. */
        IN_PROGRESS,
        /** Passed. */
        PASSED,
        /** Failed. */
        FAILED,
        /** Ended. */
        ENDED
    }

    /** A challenge's rule. */
    public enum Rule implements LowerWire {
        /** The daily loss limit. */
        DAILY_LOSS,
        /** The maximum drawdown. */
        MAX_DRAWDOWN
    }

    /** The figure the accounts are sorted by. */
    public enum Sort implements LowerWire {
        /** Daily loss used. */
        DAILY_LOSS_USED,
        /** Drawdown. */
        DRAWDOWN,
        /** Profit. */
        PROFIT,
        /** Trading days. */
        TRADING_DAYS,
        /** When the period ends. */
        PERIOD_ENDS_AT
    }

    /** Builds a {@link ListChallengeAccounts}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private final Set<State> state = new LinkedHashSet<>();
        private final Set<Rule> rule = new LinkedHashSet<>();
        private @Nullable Boolean belowMinDays;
        private Sort sort = Sort.DAILY_LOSS_USED;

        private Builder() {
        }

        /**
         * How many a page may hold; the platform's default unless set.
         *
         * @param limit the value
         * @return this builder
         */
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * The account states to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder state(State... values) {
            for (State one : values) {
                state.add(Objects.requireNonNull(one, "state"));
            }
            return this;
        }

        /**
         * The rules an account is failing on.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder rule(Rule... values) {
            for (Rule one : values) {
                rule.add(Objects.requireNonNull(one, "rule"));
            }
            return this;
        }

        /**
         * Only accounts below, or not below, the minimum trading days.
         *
         * @param belowMinDays the value
         * @return this builder
         */
        public Builder belowMinDays(boolean belowMinDays) {
            this.belowMinDays = belowMinDays;
            return this;
        }

        /**
         * The figure to sort by; daily loss used unless set; sent on every page.
         *
         * @param sort the value
         * @return this builder
         */
        public Builder sort(Sort sort) {
            this.sort = Objects.requireNonNull(sort, "sort");
            return this;
        }

        /**
         * The filter.
         *
         * @return a filter that cannot be changed
         */
        public ListChallengeAccounts build() {
            return new ListChallengeAccounts(this);
        }
    }
}
