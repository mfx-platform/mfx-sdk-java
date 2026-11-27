package com.mfxhub.sdk;

import com.mfxhub.sdk.models.TenantTerminal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which terminals to list, and how many a page may hold: the arguments of
 * {@link Terminals#page(String, ListTerminals)} and {@link Terminals#list(ListTerminals)}.
 *
 * <pre>{@code
 * ListTerminals.builder().limit(50).label("desk", "fx").state(StateEnum.ACTIVE, StateEnum.LOGGING_IN).build()
 * }</pre>
 */
public final class ListTerminals {
    private static final ListTerminals ALL = builder().build();

    private final @Nullable Integer limit;
    private final Map<String, String> label;
    private final Set<TenantTerminal.StateEnum> state;

    private ListTerminals(Builder builder) {
        this.limit = builder.limit;
        // Copied in the order they were given, so the query is the same every time it is built.
        this.label = Collections.unmodifiableMap(new LinkedHashMap<>(builder.label));
        this.state = Collections.unmodifiableSet(new LinkedHashSet<>(builder.state));
    }

    /**
     * Every terminal, in pages of the platform's default size.
     *
     * @return no filter
     */
    public static ListTerminals all() {
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

    /**
     * How many a page may hold.
     *
     * @return the size; null for the platform's default
     */
    public @Nullable Integer limit() {
        return limit;
    }

    /**
     * The labels a terminal must carry, every one.
     *
     * @return the labels, keys to values
     */
    public Map<String, String> label() {
        return label;
    }

    /**
     * The states to list: a terminal is listed when it is in any of them.
     *
     * @return the states; empty for no filter
     */
    public Set<TenantTerminal.StateEnum> state() {
        return state;
    }

    /** The query this filter is, in the order it was built, with the cursor first. */
    List<Map.Entry<String, String>> query(@Nullable String cursor) {
        List<Map.Entry<String, String>> query = new ArrayList<>();
        if (cursor != null) {
            query.add(Map.entry("cursor", cursor));
        }
        if (limit != null) {
            query.add(Map.entry("limit", Integer.toString(limit)));
        }
        for (Map.Entry<String, String> pair : label.entrySet()) {
            query.add(Map.entry("label", pair.getKey() + ":" + pair.getValue()));
        }
        if (!state.isEmpty()) {
            // One comma-separated value of the contract's words, as the contract reads a filter of several.
            query.add(Map.entry("state", String.join(",", state.stream().map(TenantTerminal.StateEnum::getValue).toList())));
        }
        return query;
    }

    /** Builds a {@link ListTerminals}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private final Map<String, String> label = new LinkedHashMap<>();
        private final Set<TenantTerminal.StateEnum> state = new LinkedHashSet<>();

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
         * A label a terminal must carry: exact, case-sensitive key and value. Applied on the platform
         * before the page is cut, so a filtered page is never short for being filtered.
         *
         * @param key the label's key
         * @param value its value
         * @return this builder
         */
        public Builder label(String key, String value) {
            label.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * States to list: a terminal is listed when it is in any of them.
         *
         * @param states the states
         * @return this builder
         */
        public Builder state(TenantTerminal.StateEnum... states) {
            for (TenantTerminal.StateEnum one : states) {
                state.add(Objects.requireNonNull(one, "state"));
            }
            return this;
        }

        /**
         * The filter.
         *
         * @return a filter that cannot be changed
         */
        public ListTerminals build() {
            return new ListTerminals(this);
        }
    }
}
