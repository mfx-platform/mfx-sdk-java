package com.mfxhub.sdk;

import com.mfxhub.sdk.models.CopyStopoutState;
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
 * Which of every follower's stop-outs {@link Copy#tenantStopouts(String, ListTenantStopouts)} lists, inside a window on their created_at (the last 24 hours without a start). States go as one comma-separated value.
 */
public final class ListTenantStopouts {
    private static final ListTenantStopouts ALL = builder().build();

    private final @Nullable Integer limit;
    private final Set<CopyStopoutState> state;
    private final @Nullable UUID followerId;
    private final @Nullable UUID groupId;
    private final @Nullable OffsetDateTime from;
    private final @Nullable OffsetDateTime to;

    private ListTenantStopouts(Builder builder) {
        this.limit = builder.limit;
        this.state = Collections.unmodifiableSet(new LinkedHashSet<>(builder.state));
        this.followerId = builder.followerId;
        this.groupId = builder.groupId;
        this.from = builder.from;
        this.to = builder.to;
        if (builder.from != null && builder.to != null && builder.to.isBefore(builder.from)) {
            throw new IllegalArgumentException("the stop-outs's window ends before it starts");
        }
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListTenantStopouts all() {
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
        return Query.of().add("cursor", cursor).add("limit", limit).joined("state", state).add("follower_id", followerId).add("group_id", groupId).add("from", from).add("to", to).build();
    }

    /** Builds a {@link ListTenantStopouts}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private final Set<CopyStopoutState> state = new LinkedHashSet<>();
        private @Nullable UUID followerId;
        private @Nullable UUID groupId;
        private @Nullable OffsetDateTime from;
        private @Nullable OffsetDateTime to;

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
        public Builder state(CopyStopoutState... values) {
            for (CopyStopoutState one : values) {
                state.add(Objects.requireNonNull(one, "state"));
            }
            return this;
        }

        /**
         * One follower's stop-outs.
         *
         * @param followerId the value
         * @return this builder
         */
        public Builder followerId(UUID followerId) {
            this.followerId = Objects.requireNonNull(followerId, "followerId");
            return this;
        }

        /**
         * A group's followers' stop-outs.
         *
         * @param groupId the value
         * @return this builder
         */
        public Builder groupId(UUID groupId) {
            this.groupId = Objects.requireNonNull(groupId, "groupId");
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
         * The filter.
         *
         * @return a filter that cannot be changed
         */
        public ListTenantStopouts build() {
            return new ListTenantStopouts(this);
        }
    }
}
