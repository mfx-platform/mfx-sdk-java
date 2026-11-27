package com.mfxhub.sdk;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which accounts {@link Risk#accountsPage(String, ListRiskAccounts)} and {@link Risk#accounts(ListRiskAccounts)} list, by their daily loss used unless sorted otherwise. Coverage and restriction are sent as repeated parameters.
 */
public final class ListRiskAccounts {
    private static final ListRiskAccounts ALL = builder().build();

    private final @Nullable Integer limit;
    private final Set<Coverage> coverage;
    private final Set<Restriction> restriction;
    private final Sort sort;

    private ListRiskAccounts(Builder builder) {
        this.limit = builder.limit;
        this.coverage = Collections.unmodifiableSet(new LinkedHashSet<>(builder.coverage));
        this.restriction = Collections.unmodifiableSet(new LinkedHashSet<>(builder.restriction));
        this.sort = builder.sort;
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListRiskAccounts all() {
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
        return Query.of().add("cursor", cursor).add("limit", limit).repeated("coverage", coverage).repeated("restriction", restriction).add("sort", sort).build();
    }

    /** An account's coverage. */
    public enum Coverage implements LowerWire {
        /** Healthy. */
        HEALTHY,
        /** Degraded. */
        DEGRADED,
        /** Disconnected. */
        DISCONNECTED,
        /** Not known. */
        UNKNOWN,
        /** No rules in force. */
        NO_RULES
    }

    /** An account's restriction. */
    public enum Restriction implements LowerWire {
        /** None. */
        NONE,
        /** New entries are blocked. */
        ENTRIES_BLOCKED
    }

    /** The figure the accounts are sorted by. */
    public enum Sort implements LowerWire {
        /** Daily loss used, most first. */
        DAILY_LOSS_USED,
        /** Drawdown used. */
        DRAWDOWN_USED,
        /** Exposure. */
        EXPOSURE,
        /** The age of the inputs. */
        INPUTS_AGE,
        /** Exit plans. */
        PLANS
    }

    /** Builds a {@link ListRiskAccounts}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private final Set<Coverage> coverage = new LinkedHashSet<>();
        private final Set<Restriction> restriction = new LinkedHashSet<>();
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
         * The coverage states to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder coverage(Coverage... values) {
            for (Coverage one : values) {
                coverage.add(Objects.requireNonNull(one, "coverage"));
            }
            return this;
        }

        /**
         * The restrictions to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder restriction(Restriction... values) {
            for (Restriction one : values) {
                restriction.add(Objects.requireNonNull(one, "restriction"));
            }
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
        public ListRiskAccounts build() {
            return new ListRiskAccounts(this);
        }
    }
}
