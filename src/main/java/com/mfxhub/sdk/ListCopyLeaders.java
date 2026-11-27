package com.mfxhub.sdk;

import com.mfxhub.sdk.models.CopyLeaderSource;
import com.mfxhub.sdk.models.CopyLeaderState;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Which leaders {@link Copy#leaders(String, ListCopyLeaders)} lists, newest first. States go as one comma-separated value.
 */
public final class ListCopyLeaders {
    private static final ListCopyLeaders ALL = builder().build();

    private final @Nullable Integer limit;
    private final Set<CopyLeaderState> state;
    private final @Nullable CopyLeaderSource source;
    private final @Nullable UUID accountId;

    private ListCopyLeaders(Builder builder) {
        this.limit = builder.limit;
        this.state = Collections.unmodifiableSet(new LinkedHashSet<>(builder.state));
        this.source = builder.source;
        this.accountId = builder.accountId;
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListCopyLeaders all() {
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
        return Query.of().add("cursor", cursor).add("limit", limit).joined("state", state).add("source", source).add("account_id", accountId).build();
    }

    /** Builds a {@link ListCopyLeaders}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private final Set<CopyLeaderState> state = new LinkedHashSet<>();
        private @Nullable CopyLeaderSource source;
        private @Nullable UUID accountId;

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
        public Builder state(CopyLeaderState... values) {
            for (CopyLeaderState one : values) {
                state.add(Objects.requireNonNull(one, "state"));
            }
            return this;
        }

        /**
         * Account or external leaders only.
         *
         * @param source the value
         * @return this builder
         */
        public Builder source(CopyLeaderSource source) {
            this.source = Objects.requireNonNull(source, "source");
            return this;
        }

        /**
         * The leader on this account.
         *
         * @param accountId the value
         * @return this builder
         */
        public Builder accountId(UUID accountId) {
            this.accountId = Objects.requireNonNull(accountId, "accountId");
            return this;
        }

        /**
         * The filter.
         *
         * @return a filter that cannot be changed
         */
        public ListCopyLeaders build() {
            return new ListCopyLeaders(this);
        }
    }
}
