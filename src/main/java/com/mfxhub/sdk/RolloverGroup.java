package com.mfxhub.sdk;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The optional settings of {@link Copy#rolloverGroup}: which template fields, and from which member.
 */
public final class RolloverGroup {
    private static final RolloverGroup NONE = builder().build();

    private final @Nullable List<RolloverField> fields;
    private final @Nullable UUID after;
    private final @Nullable Integer limit;

    private RolloverGroup(Builder builder) {
        this.fields = builder.fields;
        this.after = builder.after;
        this.limit = builder.limit;
    }

    /**
     * Nothing set: nothing of it is sent.
     *
     * @return no options
     */
    public static RolloverGroup none() {
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

    @Nullable List<RolloverField> fields() {
        return fields;
    }

    @Nullable UUID after() {
        return after;
    }

    @Nullable Integer limit() {
        return limit;
    }

    /** Builds a {@link RolloverGroup}. */
    public static final class Builder {
        private @Nullable List<RolloverField> fields;
        private @Nullable UUID after;
        private @Nullable Integer limit;

        private Builder() {
        }

        /**
         * The template fields to set; every field the template sets when not given.
         *
         * @param fields the value
         * @return this builder
         */
        public Builder fields(List<RolloverField> fields) {
            this.fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
            return this;
        }

        /**
         * The member to continue after, from a run's {@code next_after}.
         *
         * @param after the value
         * @return this builder
         */
        public Builder after(UUID after) {
            this.after = Objects.requireNonNull(after, "after");
            return this;
        }

        /**
         * How many members one run takes.
         *
         * @param limit the value
         * @return this builder
         */
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * The options.
         *
         * @return options that cannot be changed
         */
        public RolloverGroup build() {
            return new RolloverGroup(this);
        }
    }
}
