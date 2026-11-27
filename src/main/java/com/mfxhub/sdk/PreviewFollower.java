package com.mfxhub.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The optional settings of {@link Copy#previewFollower}, as a follow would have them.
 */
public final class PreviewFollower {
    private static final PreviewFollower NONE = builder().build();

    private final @Nullable Map<String, ?> bounds;
    private final @Nullable Map<String, String> symbolMap;
    private final @Nullable Map<String, ?> options;

    private PreviewFollower(Builder builder) {
        this.bounds = builder.bounds;
        this.symbolMap = builder.symbolMap;
        this.options = builder.options;
    }

    /**
     * Nothing set: nothing of it is sent.
     *
     * @return no options
     */
    public static PreviewFollower none() {
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

    @Nullable Map<String, ?> bounds() {
        return bounds;
    }

    @Nullable Map<String, String> symbolMap() {
        return symbolMap;
    }

    @Nullable Map<String, ?> options() {
        return options;
    }

    /** Builds a {@link PreviewFollower}. */
    public static final class Builder {
        private @Nullable Map<String, ?> bounds;
        private @Nullable Map<String, String> symbolMap;
        private @Nullable Map<String, ?> options;

        private Builder() {
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
         * The copy options.
         *
         * @param options the value
         * @return this builder
         */
        public Builder options(Map<String, ?> options) {
            this.options = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(options, "options")));
            return this;
        }

        /**
         * The options.
         *
         * @return options that cannot be changed
         */
        public PreviewFollower build() {
            return new PreviewFollower(this);
        }
    }
}
