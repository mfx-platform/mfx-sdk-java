package com.mfxhub.sdk;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Which exit plans {@link Risk#plansPage(String, ListRiskPlans)} and {@link Risk#plans(ListRiskPlans)} list, newest first unless set. Accounts and states are sent as repeated parameters.
 */
public final class ListRiskPlans {
    private static final ListRiskPlans ALL = builder().build();

    private final @Nullable Integer limit;
    private final Set<UUID> accountId;
    private final Set<State> state;
    private final TimeSort sort;

    private ListRiskPlans(Builder builder) {
        this.limit = builder.limit;
        this.accountId = Collections.unmodifiableSet(new LinkedHashSet<>(builder.accountId));
        this.state = Collections.unmodifiableSet(new LinkedHashSet<>(builder.state));
        this.sort = builder.sort;
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListRiskPlans all() {
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
        return Query.of().add("cursor", cursor).add("limit", limit).repeated("account_id", accountId).repeated("state", state).add("sort", sort).build();
    }

    /** An exit plan's state. */
    public enum State implements LowerWire {
        /** Draft. */
        DRAFT,
        /** Armed. */
        ARMED,
        /** Managing a position. */
        MANAGING,
        /** Paused. */
        PAUSED,
        /** Completed. */
        COMPLETED,
        /** Failed. */
        FAILED,
        /** Ended. */
        ENDED
    }

    /** Builds a {@link ListRiskPlans}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private final Set<UUID> accountId = new LinkedHashSet<>();
        private final Set<State> state = new LinkedHashSet<>();
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
         * The accounts whose plans to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder accountId(UUID... values) {
            for (UUID one : values) {
                accountId.add(Objects.requireNonNull(one, "accountId"));
            }
            return this;
        }

        /**
         * The plan states to list.
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
        public ListRiskPlans build() {
            return new ListRiskPlans(this);
        }
    }
}
