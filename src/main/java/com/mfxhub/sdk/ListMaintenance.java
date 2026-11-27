package com.mfxhub.sdk;

import com.mfxhub.sdk.models.TenantMaintenance;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which maintenance windows to list, and how many a page may hold: the arguments of
 * {@link Maintenance#page(String, ListMaintenance)} and {@link Maintenance#list(ListMaintenance)}.
 * A window as a tenant sees it: {@code in_progress} covers running, paused and verifying.
 */
public final class ListMaintenance {
    private static final ListMaintenance ALL = builder().build();

    private final @Nullable Integer limit;
    private final Set<TenantMaintenance.StateEnum> state;

    private ListMaintenance(Builder builder) {
        this.limit = builder.limit;
        this.state = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(builder.state));
    }

    /**
     * No filter: scheduled and in progress, in the platform's default page size.
     *
     * @return no filter
     */
    public static ListMaintenance all() {
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

    List<Map.Entry<String, String>> query(@Nullable String cursor) {
        return Query.page(cursor, limit).joined("state", state).build();
    }

    /** Builds a {@link ListMaintenance}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private final Set<TenantMaintenance.StateEnum> state = new LinkedHashSet<>();

        private Builder() {
        }

        /**
         * How many a page may hold.
         *
         * @param limit the size the platform is asked for
         * @return this builder
         */
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * The states to list, sent as one comma-separated value.
         *
         * @param states the states
         * @return this builder
         */
        public Builder state(TenantMaintenance.StateEnum... states) {
            for (TenantMaintenance.StateEnum one : states) {
                state.add(Objects.requireNonNull(one, "state"));
            }
            return this;
        }

        /**
         * The filter.
         *
         * @return a filter that cannot be changed
         */
        public ListMaintenance build() {
            return new ListMaintenance(this);
        }
    }
}
