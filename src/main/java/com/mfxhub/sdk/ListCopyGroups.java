package com.mfxhub.sdk;

import com.mfxhub.sdk.models.CopyGroupState;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Which groups {@link Copy#groups(String, ListCopyGroups)} lists.
 */
public final class ListCopyGroups {
    private static final ListCopyGroups ALL = builder().build();

    private final @Nullable Integer limit;
    private final @Nullable CopyGroupState state;

    private ListCopyGroups(Builder builder) {
        this.limit = builder.limit;
        this.state = builder.state;
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListCopyGroups all() {
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
        return Query.of().add("cursor", cursor).add("limit", limit).add("state", state).build();
    }

    /** Builds a {@link ListCopyGroups}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private @Nullable CopyGroupState state;

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
         * Active or paused groups only.
         *
         * @param state the value
         * @return this builder
         */
        public Builder state(CopyGroupState state) {
            this.state = Objects.requireNonNull(state, "state");
            return this;
        }

        /**
         * The filter.
         *
         * @return a filter that cannot be changed
         */
        public ListCopyGroups build() {
            return new ListCopyGroups(this);
        }
    }
}
