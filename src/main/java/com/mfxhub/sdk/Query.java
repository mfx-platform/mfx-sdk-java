package com.mfxhub.sdk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A query string in the order it is built. What the caller did not name is left out of the request
 * altogether, never sent empty: an unset parameter is left out.
 */
final class Query {
    private final List<Map.Entry<String, String>> pairs = new ArrayList<>();

    static Query of() {
        return new Query();
    }

    /** The cursor and the page size, first, as every list sends them. */
    static Query page(@Nullable String cursor, @Nullable Integer limit) {
        return of().add("cursor", cursor).add("limit", limit);
    }

    Query add(String name, @Nullable Object value) {
        if (value != null) {
            pairs.add(Map.entry(name, text(value)));
        }
        return this;
    }

    /** Several values as one comma-separated parameter, as the contract reads a filter of several. */
    Query joined(String name, @Nullable Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            pairs.add(Map.entry(name, String.join(",", values.stream().map(Query::text).toList())));
        }
        return this;
    }

    /** Each value as its own parameter of the same name. */
    Query repeated(String name, @Nullable Collection<?> values) {
        if (values != null) {
            for (Object value : values) {
                pairs.add(Map.entry(name, text(value)));
            }
        }
        return this;
    }

    List<Map.Entry<String, String>> build() {
        return List.copyOf(pairs);
    }

    /** A value in the API's own words: an enum's wire value, a decimal plainly, a moment as ISO 8601. */
    static String text(Object value) {
        if (value instanceof LowerWire && value instanceof Enum<?> lower) {
            return lower.name().toLowerCase(java.util.Locale.ROOT);
        }
        if (value instanceof Enum<?> constant) {
            if (MfxJson.isUnknown(constant)) {
                throw new IllegalArgumentException(MfxJson.unsentMessage(constant));
            }
            return MfxJson.MAPPER.convertValue(constant, String.class);
        }
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof java.time.OffsetDateTime moment) {
            return Times.iso(moment);
        }
        return value.toString();
    }
}
