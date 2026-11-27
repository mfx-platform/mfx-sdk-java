package com.mfxhub.sdk;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Which of the leaders' signals {@link Copy#signals(String, ListCopySignals)} lists, inside a window (the last 24 hours without a start). Kinds and actions each go as one comma-separated value.
 */
public final class ListCopySignals {
    private static final ListCopySignals ALL = builder().build();

    private final @Nullable Integer limit;
    private final @Nullable UUID leaderId;
    private final Set<Kind> kind;
    private final Set<Action> action;
    private final @Nullable OffsetDateTime from;
    private final @Nullable OffsetDateTime to;

    private ListCopySignals(Builder builder) {
        this.limit = builder.limit;
        this.leaderId = builder.leaderId;
        this.kind = Collections.unmodifiableSet(new LinkedHashSet<>(builder.kind));
        this.action = Collections.unmodifiableSet(new LinkedHashSet<>(builder.action));
        this.from = builder.from;
        this.to = builder.to;
        if (builder.from != null && builder.to != null && builder.to.isBefore(builder.from)) {
            throw new IllegalArgumentException("the signals's window ends before it starts");
        }
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListCopySignals all() {
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
        return Query.of().add("cursor", cursor).add("limit", limit).add("leader_id", leaderId).joined("kind", kind).joined("action", action).add("from", from).add("to", to).build();
    }

    /** Where a signal came from. */
    public enum Kind implements LowerWire {
        /** A leader account's fill. */
        FILL,
        /** An external leader's own signal. */
        EXTERNAL
    }

    /** What a signal does. */
    public enum Action implements LowerWire {
        /** Open. */
        OPEN,
        /** Modify. */
        MODIFY,
        /** Close. */
        CLOSE,
        /** Close part. */
        CLOSE_PART,
        /** Place a pending order. */
        PENDING_PLACE,
        /** Modify a pending order. */
        PENDING_MODIFY,
        /** Cancel a pending order. */
        PENDING_CANCEL
    }

    /** Builds a {@link ListCopySignals}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private @Nullable UUID leaderId;
        private final Set<Kind> kind = new LinkedHashSet<>();
        private final Set<Action> action = new LinkedHashSet<>();
        private @Nullable OffsetDateTime from;
        private @Nullable OffsetDateTime to;

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
         * One leader's signals.
         *
         * @param leaderId the value
         * @return this builder
         */
        public Builder leaderId(UUID leaderId) {
            this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
            return this;
        }

        /**
         * The kinds to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder kind(Kind... values) {
            for (Kind one : values) {
                kind.add(Objects.requireNonNull(one, "kind"));
            }
            return this;
        }

        /**
         * The actions to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder action(Action... values) {
            for (Action one : values) {
                action.add(Objects.requireNonNull(one, "action"));
            }
            return this;
        }

        /**
         * The window's start.
         *
         * @param from the value
         * @return this builder
         */
        public Builder from(OffsetDateTime from) {
            this.from = Objects.requireNonNull(from, "from");
            return this;
        }

        /**
         * The window's end, not before its start.
         *
         * @param to the value
         * @return this builder
         */
        public Builder to(OffsetDateTime to) {
            this.to = Objects.requireNonNull(to, "to");
            return this;
        }

        /**
         * The filter.
         *
         * @return a filter that cannot be changed
         */
        public ListCopySignals build() {
            return new ListCopySignals(this);
        }
    }
}
