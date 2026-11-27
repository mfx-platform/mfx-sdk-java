package com.mfxhub.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A follower's mandate: the optional settings of {@link Copy#createFollower} and what changes in {@link Copy#updateFollower}, each a mapping in the contract's names where it is one. A group is named only when a follower is made.
 */
public final class FollowerMandate {
    private static final FollowerMandate NONE = builder().build();

    private final @Nullable Map<String, ?> scaling;
    private final @Nullable Map<String, ?> bounds;
    private final @Nullable Map<String, String> symbolMap;
    private final @Nullable Map<String, ?> options;
    private final @Nullable Map<String, ?> riskLimits;
    private final @Nullable Boolean closeOnStopout;
    private final @Nullable UUID groupId;

    private FollowerMandate(Builder builder) {
        this.scaling = builder.scaling;
        this.bounds = builder.bounds;
        this.symbolMap = builder.symbolMap;
        this.options = builder.options;
        this.riskLimits = builder.riskLimits;
        this.closeOnStopout = builder.closeOnStopout;
        this.groupId = builder.groupId;
    }

    /**
     * Nothing set: nothing of it is sent.
     *
     * @return no options
     */
    public static FollowerMandate none() {
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

    @Nullable Map<String, ?> scaling() {
        return scaling;
    }

    @Nullable Map<String, ?> bounds() {
        return bounds;
    }

    @Nullable Map<String, String> symbolMap() {
        return symbolMap;
    }

    @Nullable Map<String, ?> options() {
        return options;
    }

    @Nullable Map<String, ?> riskLimits() {
        return riskLimits;
    }

    @Nullable Boolean closeOnStopout() {
        return closeOnStopout;
    }

    @Nullable UUID groupId() {
        return groupId;
    }

    /** Builds a {@link FollowerMandate}. */
    public static final class Builder {
        private @Nullable Map<String, ?> scaling;
        private @Nullable Map<String, ?> bounds;
        private @Nullable Map<String, String> symbolMap;
        private @Nullable Map<String, ?> options;
        private @Nullable Map<String, ?> riskLimits;
        private @Nullable Boolean closeOnStopout;
        private @Nullable UUID groupId;

        private Builder() {
        }

        /**
         * How the leader's volume is scaled; required unless the group's template has it.
         *
         * @param scaling the value
         * @return this builder
         */
        public Builder scaling(Map<String, ?> scaling) {
            this.scaling = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(scaling, "scaling")));
            return this;
        }

        /**
         * The volume bounds.
         *
         * @param bounds the value
         * @return this builder
         */
        public Builder bounds(Map<String, ?> bounds) {
            this.bounds = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(bounds, "bounds")));
            return this;
        }

        /**
         * The leader's symbols to the follower's.
         *
         * @param symbolMap the value
         * @return this builder
         */
        public Builder symbolMap(Map<String, String> symbolMap) {
            this.symbolMap = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(symbolMap, "symbolMap")));
            return this;
        }

        /**
         * The copy options, {@code when_risk_unchecked} among them.
         *
         * @param options the value
         * @return this builder
         */
        public Builder options(Map<String, ?> options) {
            this.options = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(options, "options")));
            return this;
        }

        /**
         * The follower's copy limits.
         *
         * @param riskLimits the value
         * @return this builder
         */
        public Builder riskLimits(Map<String, ?> riskLimits) {
            this.riskLimits = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(riskLimits, "riskLimits")));
            return this;
        }

        /**
         * Whether a stop-out closes the copied volume.
         *
         * @param closeOnStopout the value
         * @return this builder
         */
        public Builder closeOnStopout(boolean closeOnStopout) {
            this.closeOnStopout = closeOnStopout;
            return this;
        }

        /**
         * The group the follower joins, when it is made.
         *
         * @param groupId the value
         * @return this builder
         */
        public Builder groupId(UUID groupId) {
            this.groupId = Objects.requireNonNull(groupId, "groupId");
            return this;
        }

        /**
         * The options.
         *
         * @return options that cannot be changed
         */
        public FollowerMandate build() {
            return new FollowerMandate(this);
        }
    }
}
