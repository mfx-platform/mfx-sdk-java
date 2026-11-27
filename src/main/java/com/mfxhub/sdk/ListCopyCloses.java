package com.mfxhub.sdk;

import com.mfxhub.sdk.models.CopyCloseState;
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
 * Which requested closes {@link Copy#closes(String, ListCopyCloses)} lists, inside a window. States go as one comma-separated value.
 */
public final class ListCopyCloses {
    private static final ListCopyCloses ALL = builder().build();

    private final @Nullable Integer limit;
    private final @Nullable UUID followerId;
    private final @Nullable UUID leaderId;
    private final @Nullable UUID groupId;
    private final @Nullable UUID batchId;
    private final Set<CopyCloseState> state;
    private final @Nullable OffsetDateTime from;
    private final @Nullable OffsetDateTime to;

    private ListCopyCloses(Builder builder) {
        this.limit = builder.limit;
        this.followerId = builder.followerId;
        this.leaderId = builder.leaderId;
        this.groupId = builder.groupId;
        this.batchId = builder.batchId;
        this.state = Collections.unmodifiableSet(new LinkedHashSet<>(builder.state));
        this.from = builder.from;
        this.to = builder.to;
        if (builder.from != null && builder.to != null && builder.to.isBefore(builder.from)) {
            throw new IllegalArgumentException("the closes's window ends before it starts");
        }
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListCopyCloses all() {
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
        return Query.of().add("cursor", cursor).add("limit", limit).add("follower_id", followerId).add("leader_id", leaderId).add("group_id", groupId).add("batch_id", batchId).joined("state", state).add("from", from).add("to", to).build();
    }

    /** Builds a {@link ListCopyCloses}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private @Nullable UUID followerId;
        private @Nullable UUID leaderId;
        private @Nullable UUID groupId;
        private @Nullable UUID batchId;
        private final Set<CopyCloseState> state = new LinkedHashSet<>();
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
         * One follower's closes.
         *
         * @param followerId the value
         * @return this builder
         */
        public Builder followerId(UUID followerId) {
            this.followerId = Objects.requireNonNull(followerId, "followerId");
            return this;
        }

        /**
         * One leader's closes.
         *
         * @param leaderId the value
         * @return this builder
         */
        public Builder leaderId(UUID leaderId) {
            this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
            return this;
        }

        /**
         * One group's closes.
         *
         * @param groupId the value
         * @return this builder
         */
        public Builder groupId(UUID groupId) {
            this.groupId = Objects.requireNonNull(groupId, "groupId");
            return this;
        }

        /**
         * One batch's closes.
         *
         * @param batchId the value
         * @return this builder
         */
        public Builder batchId(UUID batchId) {
            this.batchId = Objects.requireNonNull(batchId, "batchId");
            return this;
        }

        /**
         * The states to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder state(CopyCloseState... values) {
            for (CopyCloseState one : values) {
                state.add(Objects.requireNonNull(one, "state"));
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
         * The filter.
         *
         * @return a filter that cannot be changed
         */
        public ListCopyCloses build() {
            return new ListCopyCloses(this);
        }
    }
}
