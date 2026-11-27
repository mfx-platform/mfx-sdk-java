package com.mfxhub.sdk;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Which Risk Management policies {@link Risk#policies(ListRiskPolicies)} lists, newest first unless set.
 */
public final class ListRiskPolicies {
    private static final ListRiskPolicies ALL = builder().build();

    private final @Nullable Integer limit;
    private final @Nullable State state;
    private final TimeSort sort;

    private ListRiskPolicies(Builder builder) {
        this.limit = builder.limit;
        this.state = builder.state;
        this.sort = builder.sort;
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListRiskPolicies all() {
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
        return Query.of().add("cursor", cursor).add("limit", limit).add("state", state).add("sort", sort).build();
    }

    /** A policy's state. */
    public enum State implements LowerWire {
        /** In use or usable. */
        ACTIVE,
        /** Archived: no longer activated or versioned. */
        ARCHIVED
    }

    /** Builds a {@link ListRiskPolicies}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private @Nullable State state;
        private TimeSort sort = TimeSort.NEWEST;

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
         * Active or archived policies only.
         *
         * @param state the value
         * @return this builder
         */
        public Builder state(State state) {
            this.state = Objects.requireNonNull(state, "state");
            return this;
        }

        /**
         * Newest first unless set; sent on every page.
         *
         * @param sort the value
         * @return this builder
         */
        public Builder sort(TimeSort sort) {
            this.sort = Objects.requireNonNull(sort, "sort");
            return this;
        }

        /**
         * The filter.
         *
         * @return a filter that cannot be changed
         */
        public ListRiskPolicies build() {
            return new ListRiskPolicies(this);
        }
    }
}
