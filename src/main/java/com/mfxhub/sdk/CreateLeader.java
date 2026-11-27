package com.mfxhub.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The optional settings of {@link Copy#createLeader}, each a mapping in the contract's names where it is one.
 */
public final class CreateLeader {
    private static final CreateLeader NONE = builder().build();

    private final @Nullable UUID accountId;
    private final @Nullable Map<String, ?> filters;
    private final @Nullable Map<String, ?> copies;
    private final @Nullable Map<String, ?> limits;
    private final @Nullable Map<String, ?> whoMayJoin;

    private CreateLeader(Builder builder) {
        this.accountId = builder.accountId;
        this.filters = builder.filters;
        this.copies = builder.copies;
        this.limits = builder.limits;
        this.whoMayJoin = builder.whoMayJoin;
    }

    /**
     * Nothing set: nothing of it is sent.
     *
     * @return no options
     */
    public static CreateLeader none() {
        return NONE;
    }

    /**
     * Options to build.
     *
     * @return a builder with nothing set
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nullable UUID accountId() {
        return accountId;
    }

    @Nullable Map<String, ?> filters() {
        return filters;
    }

    @Nullable Map<String, ?> copies() {
        return copies;
    }

    @Nullable Map<String, ?> limits() {
        return limits;
    }

    @Nullable Map<String, ?> whoMayJoin() {
        return whoMayJoin;
    }

    /** Builds a {@link CreateLeader}. */
    public static final class Builder {
        private @Nullable UUID accountId;
        private @Nullable Map<String, ?> filters;
        private @Nullable Map<String, ?> copies;
        private @Nullable Map<String, ?> limits;
        private @Nullable Map<String, ?> whoMayJoin;

        private Builder() {
        }

        /**
         * The account that leads, for an {@code account} leader.
         *
         * @param accountId the value
         * @return this builder
         */
        public Builder accountId(UUID accountId) {
            this.accountId = Objects.requireNonNull(accountId, "accountId");
            return this;
        }

        /**
         * What the leader's followers receive.
         *
         * @param filters the value
         * @return this builder
         */
        public Builder filters(Map<String, ?> filters) {
            this.filters = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(filters, "filters")));
            return this;
        }

        /**
         * How its deals are copied.
         *
         * @param copies the value
         * @return this builder
         */
        public Builder copies(Map<String, ?> copies) {
            this.copies = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(copies, "copies")));
            return this;
        }

        /**
         * Its limits.
         *
         * @param limits the value
         * @return this builder
         */
        public Builder limits(Map<String, ?> limits) {
            this.limits = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(limits, "limits")));
            return this;
        }

        /**
         * Who may follow it.
         *
         * @param whoMayJoin the value
         * @return this builder
         */
        public Builder whoMayJoin(Map<String, ?> whoMayJoin) {
            this.whoMayJoin = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(whoMayJoin, "whoMayJoin")));
            return this;
        }

        /**
         * The options.
         *
         * @return options that cannot be changed
         */
        public CreateLeader build() {
            return new CreateLeader(this);
        }
    }
}
