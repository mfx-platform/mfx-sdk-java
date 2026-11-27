package com.mfxhub.sdk;

import com.mfxhub.sdk.models.CopyOutcome;
import com.mfxhub.sdk.models.CopyReason;
import com.mfxhub.sdk.models.Venue;
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
 * Which rows of the copy log {@link Copy#log(String, ListCopyLog)} lists, inside a window of at most 31 days. Outcomes, reasons and venues each go as one comma-separated value; a reason is one of the closed list's.
 */
public final class ListCopyLog {
    private static final ListCopyLog ALL = builder().build();

    private final @Nullable Integer limit;
    private final @Nullable UUID leaderId;
    private final @Nullable UUID followerId;
    private final @Nullable UUID accountId;
    private final @Nullable UUID groupId;
    private final @Nullable UUID signalId;
    private final Set<CopyOutcome> outcome;
    private final Set<CopyReason> reason;
    private final Set<Venue> venue;
    private final @Nullable OffsetDateTime from;
    private final @Nullable OffsetDateTime to;
    private final @Nullable Sort sort;

    private ListCopyLog(Builder builder) {
        this.limit = builder.limit;
        this.leaderId = builder.leaderId;
        this.followerId = builder.followerId;
        this.accountId = builder.accountId;
        this.groupId = builder.groupId;
        this.signalId = builder.signalId;
        this.outcome = Collections.unmodifiableSet(new LinkedHashSet<>(builder.outcome));
        this.reason = Collections.unmodifiableSet(new LinkedHashSet<>(builder.reason));
        this.venue = Collections.unmodifiableSet(new LinkedHashSet<>(builder.venue));
        this.from = builder.from;
        this.to = builder.to;
        this.sort = builder.sort;
        if (builder.from != null && builder.to != null && builder.to.isBefore(builder.from)) {
            throw new IllegalArgumentException("the copy log's window ends before it starts");
        }
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListCopyLog all() {
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
        return Query.of().add("cursor", cursor).add("limit", limit).add("leader_id", leaderId).add("follower_id", followerId).add("account_id", accountId).add("group_id", groupId).add("signal_id", signalId).joined("outcome", outcome).joined("reason", reason).joined("venue", venue).add("from", from).add("to", to).add("sort", sort).build();
    }

    /** The log's order. */
    public enum Sort implements LowerWire {
        /** Newest first. */
        CREATED_AT,
        /** The slowest of MFX Hub's part first. */
        MFX_MS
    }

    /** Builds a {@link ListCopyLog}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private @Nullable UUID leaderId;
        private @Nullable UUID followerId;
        private @Nullable UUID accountId;
        private @Nullable UUID groupId;
        private @Nullable UUID signalId;
        private final Set<CopyOutcome> outcome = new LinkedHashSet<>();
        private final Set<CopyReason> reason = new LinkedHashSet<>();
        private final Set<Venue> venue = new LinkedHashSet<>();
        private @Nullable OffsetDateTime from;
        private @Nullable OffsetDateTime to;
        private @Nullable Sort sort;

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
         * One leader's copies.
         *
         * @param leaderId the value
         * @return this builder
         */
        public Builder leaderId(UUID leaderId) {
            this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
            return this;
        }

        /**
         * One follower's copies.
         *
         * @param followerId the value
         * @return this builder
         */
        public Builder followerId(UUID followerId) {
            this.followerId = Objects.requireNonNull(followerId, "followerId");
            return this;
        }

        /**
         * One account's copies.
         *
         * @param accountId the value
         * @return this builder
         */
        public Builder accountId(UUID accountId) {
            this.accountId = Objects.requireNonNull(accountId, "accountId");
            return this;
        }

        /**
         * One group's copies.
         *
         * @param groupId the value
         * @return this builder
         */
        public Builder groupId(UUID groupId) {
            this.groupId = Objects.requireNonNull(groupId, "groupId");
            return this;
        }

        /**
         * One signal's copies.
         *
         * @param signalId the value
         * @return this builder
         */
        public Builder signalId(UUID signalId) {
            this.signalId = Objects.requireNonNull(signalId, "signalId");
            return this;
        }

        /**
         * The outcomes to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder outcome(CopyOutcome... values) {
            for (CopyOutcome one : values) {
                outcome.add(Objects.requireNonNull(one, "outcome"));
            }
            return this;
        }

        /**
         * The refusal reasons to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder reason(CopyReason... values) {
            for (CopyReason one : values) {
                reason.add(Objects.requireNonNull(one, "reason"));
            }
            return this;
        }

        /**
         * The follower venues to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder venue(Venue... values) {
            for (Venue one : values) {
                venue.add(Objects.requireNonNull(one, "venue"));
            }
            return this;
        }

        /**
         * The window's start.
         *
         * @param from the value
         * @return this builder
         */
        public Builder from(OffsetDateTime from) {
            this.from = Objects.requireNonNull(from, "from");
            return this;
        }

        /**
         * The window's end, not before its start.
         *
         * @param to the value
         * @return this builder
         */
        public Builder to(OffsetDateTime to) {
            this.to = Objects.requireNonNull(to, "to");
            return this;
        }

        /**
         * Newest first, or the slowest of MFX Hub's part first.
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
        public ListCopyLog build() {
            return new ListCopyLog(this);
        }
    }
}
