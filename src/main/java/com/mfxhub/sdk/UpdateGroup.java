package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What changes on a group ({@link Copy#updateGroup}); only what is set is sent. The exposure cap and the
 * placement policy have three states: not set leaves it, a value sets it, and
 * {@code clearGrossExposureCap()} or {@code clearPlacementPolicy()} sends null, which clears it.
 */
public final class UpdateGroup {
    private final @Nullable String name;
    private final @Nullable Map<String, ?> template;
    private final boolean capChanges;
    private final @Nullable BigDecimal grossExposureCap;
    private final boolean policyChanges;
    private final @Nullable String placementPolicy;

    private UpdateGroup(Builder builder) {
        this.name = builder.name;
        this.template = builder.template;
        this.capChanges = builder.capChanges;
        this.grossExposureCap = builder.grossExposureCap;
        this.policyChanges = builder.policyChanges;
        this.placementPolicy = builder.placementPolicy;
    }

    /**
     * A change to build.
     *
     * @return a builder with nothing set
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nullable String name() {
        return name;
    }

    @Nullable Map<String, ?> template() {
        return template;
    }

    boolean capChanges() {
        return capChanges;
    }

    @Nullable BigDecimal grossExposureCap() {
        return grossExposureCap;
    }

    boolean policyChanges() {
        return policyChanges;
    }

    @Nullable String placementPolicy() {
        return placementPolicy;
    }

    /** Builds an {@link UpdateGroup}. */
    public static final class Builder {
        private @Nullable String name;
        private @Nullable Map<String, ?> template;
        private boolean capChanges;
        private @Nullable BigDecimal grossExposureCap;
        private boolean policyChanges;
        private @Nullable String placementPolicy;

        private Builder() {
        }

        /**
         * A new name.
         *
         * @param name the name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        /**
         * A new template, for followers created into the group afterwards; {@link Copy#rolloverGroup}
         * sets it on the members already in it.
         *
         * @param template in the contract's names
         * @return this builder
         */
        public Builder template(Map<String, ?> template) {
            this.template = Collections.unmodifiableMap(new LinkedHashMap<>(template));
            return this;
        }

        /**
         * A new gross exposure cap, in USD; never below zero.
         *
         * @param grossExposureCap the cap
         * @return this builder
         */
        public Builder grossExposureCap(BigDecimal grossExposureCap) {
            this.grossExposureCap = Objects.requireNonNull(grossExposureCap, "grossExposureCap");
            this.capChanges = true;
            return this;
        }

        /**
         * No cap any more: sent as null.
         *
         * @return this builder
         */
        public Builder clearGrossExposureCap() {
            this.grossExposureCap = null;
            this.capChanges = true;
            return this;
        }

        /**
         * A new placement policy. Deprecated: stored and returned, and placement never reads it.
         *
         * @param placementPolicy the policy
         * @return this builder
         */
        public Builder placementPolicy(String placementPolicy) {
            this.placementPolicy = Objects.requireNonNull(placementPolicy, "placementPolicy");
            this.policyChanges = true;
            return this;
        }

        /**
         * No placement policy any more: sent as null.
         *
         * @return this builder
         */
        public Builder clearPlacementPolicy() {
            this.placementPolicy = null;
            this.policyChanges = true;
            return this;
        }

        /**
         * The change.
         *
         * @return a change that cannot be changed
         */
        public UpdateGroup build() {
            return new UpdateGroup(this);
        }
    }
}
