package com.mfxhub.sdk;

import com.mfxhub.sdk.models.RiskActivityKind;
import com.mfxhub.sdk.models.RiskOutcome;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Which activity {@link Risk#activityPage(String, ListRiskActivity)} and {@link Risk#activity(ListRiskActivity)} list, unresolved first unless sorted otherwise. Kinds, accounts and outcomes are sent as repeated parameters.
 */
public final class ListRiskActivity {
    private static final ListRiskActivity ALL = builder().build();

    private final @Nullable Integer limit;
    private final Set<RiskActivityKind> kind;
    private final Set<UUID> accountId;
    private final Set<RiskOutcome> outcome;
    private final @Nullable OffsetDateTime start;
    private final @Nullable OffsetDateTime end;
    private final Sort sort;

    private ListRiskActivity(Builder builder) {
        this.limit = builder.limit;
        this.kind = Collections.unmodifiableSet(new LinkedHashSet<>(builder.kind));
        this.accountId = Collections.unmodifiableSet(new LinkedHashSet<>(builder.accountId));
        this.outcome = Collections.unmodifiableSet(new LinkedHashSet<>(builder.outcome));
        this.start = builder.start;
        this.end = builder.end;
        this.sort = builder.sort;
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListRiskActivity all() {
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
        return Query.of().add("cursor", cursor).add("limit", limit).repeated("kind", kind).repeated("account_id", accountId).repeated("outcome", outcome).add("from", start).add("to", end).add("sort", sort).build();
    }

    /** The order of the activity. */
    public enum Sort implements LowerWire {
        /** Unresolved first. */
        UNRESOLVED_FIRST,
        /** Newest first. */
        NEWEST,
        /** Oldest first. */
        OLDEST
    }

    /** Builds a {@link ListRiskActivity}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private final Set<RiskActivityKind> kind = new LinkedHashSet<>();
        private final Set<UUID> accountId = new LinkedHashSet<>();
        private final Set<RiskOutcome> outcome = new LinkedHashSet<>();
        private @Nullable OffsetDateTime start;
        private @Nullable OffsetDateTime end;
        private Sort sort = Sort.UNRESOLVED_FIRST;

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
         * The kinds of activity to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder kind(RiskActivityKind... values) {
            for (RiskActivityKind one : values) {
                kind.add(Objects.requireNonNull(one, "kind"));
            }
            return this;
        }

        /**
         * The accounts whose activity to list.
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
         * The outcomes to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder outcome(RiskOutcome... values) {
            for (RiskOutcome one : values) {
                outcome.add(Objects.requireNonNull(one, "outcome"));
            }
            return this;
        }

        /**
         * The window's start.
         *
         * @param start the value
         * @return this builder
         */
        public Builder start(OffsetDateTime start) {
            this.start = Objects.requireNonNull(start, "start");
            return this;
        }

        /**
         * The window's end.
         *
         * @param end the value
         * @return this builder
         */
        public Builder end(OffsetDateTime end) {
            this.end = Objects.requireNonNull(end, "end");
            return this;
        }

        /**
         * Unresolved first unless set; sent on every page.
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
        public ListRiskActivity build() {
            return new ListRiskActivity(this);
        }
    }
}
