package com.mfxhub.sdk;

import com.mfxhub.sdk.models.CopyFollowerState;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Which followers {@link Copy#followers(String, ListCopyFollowers)} lists, newest first unless sorted otherwise (the sort is sent on every page). States go as one comma-separated value.
 */
public final class ListCopyFollowers {
    private static final ListCopyFollowers ALL = builder().build();

    private final @Nullable Integer limit;
    private final Set<CopyFollowerState> state;
    private final @Nullable UUID leaderId;
    private final @Nullable UUID accountId;
    private final @Nullable UUID groupId;
    private final @Nullable Boolean outOfStep;
    private final TimeSort sort;

    private ListCopyFollowers(Builder builder) {
        this.limit = builder.limit;
        this.state = Collections.unmodifiableSet(new LinkedHashSet<>(builder.state));
        this.leaderId = builder.leaderId;
        this.accountId = builder.accountId;
        this.groupId = builder.groupId;
        this.outOfStep = builder.outOfStep;
        this.sort = builder.sort;
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListCopyFollowers all() {
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
        return Query.of().add("cursor", cursor).add("limit", limit).joined("state", state).add("leader_id", leaderId).add("account_id", accountId).add("group_id", groupId).add("out_of_step", outOfStep).add("sort", sort).build();
    }

    /** Builds a {@link ListCopyFollowers}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private final Set<CopyFollowerState> state = new LinkedHashSet<>();
        private @Nullable UUID leaderId;
        private @Nullable UUID accountId;
        private @Nullable UUID groupId;
        private @Nullable Boolean outOfStep;
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
         * The states to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder state(CopyFollowerState... values) {
            for (CopyFollowerState one : values) {
                state.add(Objects.requireNonNull(one, "state"));
            }
            return this;
        }

        /**
         * The followers of this leader.
         *
         * @param leaderId the value
         * @return this builder
         */
        public Builder leaderId(UUID leaderId) {
            this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
            return this;
        }

        /**
         * The follower on this account.
         *
         * @param accountId the value
         * @return this builder
         */
        public Builder accountId(UUID accountId) {
            this.accountId = Objects.requireNonNull(accountId, "accountId");
            return this;
        }

        /**
         * A group's followers.
         *
         * @param groupId the value
         * @return this builder
         */
        public Builder groupId(UUID groupId) {
            this.groupId = Objects.requireNonNull(groupId, "groupId");
            return this;
        }

        /**
         * True for the followers whose copy host does not hold their latest mandate.
         *
         * @param outOfStep the value
         * @return this builder
         */
        public Builder outOfStep(boolean outOfStep) {
            this.outOfStep = outOfStep;
            return this;
        }

        /**
         * Newest first unless set.
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
        public ListCopyFollowers build() {
            return new ListCopyFollowers(this);
        }
    }
}
