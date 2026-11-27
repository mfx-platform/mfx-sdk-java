package com.mfxhub.sdk;

import com.mfxhub.sdk.models.CopyStopoutState;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which of one follower's stop-outs {@link Copy#stopouts(java.util.UUID, String, ListCopyStopouts)} lists. States go as one comma-separated value.
 */
public final class ListCopyStopouts {
    private static final ListCopyStopouts ALL = builder().build();

    private final @Nullable Integer limit;
    private final Set<CopyStopoutState> state;

    private ListCopyStopouts(Builder builder) {
        this.limit = builder.limit;
        this.state = Collections.unmodifiableSet(new LinkedHashSet<>(builder.state));
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListCopyStopouts all() {
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
        return Query.of().add("cursor", cursor).add("limit", limit).joined("state", state).build();
    }

    /** Builds a {@link ListCopyStopouts}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private final Set<CopyStopoutState> state = new LinkedHashSet<>();

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
         * The filter.
         *
         * @return a filter that cannot be changed
         */
        public ListCopyStopouts build() {
            return new ListCopyStopouts(this);
        }
    }
}
