package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The optional settings of {@link Copy#createGroup}.
 */
public final class CreateGroup {
    private static final CreateGroup NONE = builder().build();

    private final @Nullable Map<String, ?> template;
    private final @Nullable BigDecimal grossExposureCap;
    private final @Nullable String placementPolicy;

    private CreateGroup(Builder builder) {
        this.template = builder.template;
        this.grossExposureCap = builder.grossExposureCap;
        this.placementPolicy = builder.placementPolicy;
    }

    /**
     * Nothing set: nothing of it is sent.
     *
     * @return no options
     */
    public static CreateGroup none() {
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

    @Nullable Map<String, ?> template() {
        return template;
    }

    @Nullable BigDecimal grossExposureCap() {
        return grossExposureCap;
    }

    @Nullable String placementPolicy() {
        return placementPolicy;
    }

    /** Builds a {@link CreateGroup}. */
    public static final class Builder {
        private @Nullable Map<String, ?> template;
        private @Nullable BigDecimal grossExposureCap;
        private @Nullable String placementPolicy;

        private Builder() {
        }

        /**
         * The mandate a follower created into the group starts from.
         *
         * @param template the value
         * @return this builder
         */
        public Builder template(Map<String, ?> template) {
            this.template = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(template, "template")));
            return this;
        }

        /**
         * The group's gross exposure cap in USD, an equal share for each live member; never below zero.
         *
         * @param grossExposureCap the value
         * @return this builder
         */
        public Builder grossExposureCap(BigDecimal grossExposureCap) {
            this.grossExposureCap = Objects.requireNonNull(grossExposureCap, "grossExposureCap");
            return this;
        }

        /**
         * Deprecated: stored and returned, and placement never reads it.
         *
         * @param placementPolicy the value
         * @return this builder
         */
        public Builder placementPolicy(String placementPolicy) {
            this.placementPolicy = Objects.requireNonNull(placementPolicy, "placementPolicy");
            return this;
        }

        /**
         * The options.
         *
         * @return options that cannot be changed
         */
        public CreateGroup build() {
            return new CreateGroup(this);
        }
    }
}
