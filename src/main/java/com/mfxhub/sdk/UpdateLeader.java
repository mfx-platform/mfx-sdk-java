package com.mfxhub.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What changes on a leader ({@link Copy#updateLeader}); only what is set is sent and changes.
 */
public final class UpdateLeader {
    private static final UpdateLeader NONE = builder().build();

    private final @Nullable Map<String, ?> filters;
    private final @Nullable Map<String, ?> copies;
    private final @Nullable Integer signalLifetimeS;
    private final @Nullable Map<String, ?> limits;
    private final @Nullable Map<String, ?> whoMayJoin;

    private UpdateLeader(Builder builder) {
        this.filters = builder.filters;
        this.copies = builder.copies;
        this.signalLifetimeS = builder.signalLifetimeS;
        this.limits = builder.limits;
        this.whoMayJoin = builder.whoMayJoin;
    }

    /**
     * Nothing set: nothing of it is sent.
     *
     * @return no options
     */
    public static UpdateLeader none() {
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

    @Nullable Map<String, ?> filters() {
        return filters;
    }

    @Nullable Map<String, ?> copies() {
        return copies;
    }

    @Nullable Integer signalLifetimeS() {
        return signalLifetimeS;
    }

    @Nullable Map<String, ?> limits() {
        return limits;
    }

    @Nullable Map<String, ?> whoMayJoin() {
        return whoMayJoin;
    }

    /** Builds a {@link UpdateLeader}. */
    public static final class Builder {
        private @Nullable Map<String, ?> filters;
        private @Nullable Map<String, ?> copies;
        private @Nullable Integer signalLifetimeS;
        private @Nullable Map<String, ?> limits;
        private @Nullable Map<String, ?> whoMayJoin;

        private Builder() {
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
         * How long a signal may still be copied, 1 to 3600 seconds.
         *
         * @param signalLifetimeS the value
         * @return this builder
         */
        public Builder signalLifetimeS(int signalLifetimeS) {
            this.signalLifetimeS = signalLifetimeS;
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
         * Who may follow it; narrowing it does not end a follow already made.
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
        public UpdateLeader build() {
            return new UpdateLeader(this);
        }
    }
}
