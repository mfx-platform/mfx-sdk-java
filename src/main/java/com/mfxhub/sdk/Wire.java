package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A body part the caller gave in the API's own names, as the platform reads it:
 * a {@link BigDecimal} as exact plain text, a {@code double} or {@code float} refused because it has
 * already been rounded ({@code 0.07 * 3} is {@code 0.21000000000000002}), maps and lists walked, and the
 * rest as given.
 */
final class Wire {
    private Wire() {
    }

    /**
     * The value ready to be written.
     *
     * @throws IllegalArgumentException a figure anywhere in it is a double or a float
     */
    static @Nullable Object of(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Double || value instanceof Float) {
            throw new IllegalArgumentException("a figure must be a BigDecimal or a string, never a double: " + value);
        }
        if (value instanceof LowerWire && value instanceof Enum<?> lower) {
            return lower.name().toLowerCase(java.util.Locale.ROOT);
        }
        if (value instanceof java.time.OffsetDateTime moment) {
            return Times.iso(moment);
        }
        if (value instanceof JsonNode node) {
            refuseFloats(node);
            return node;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, @Nullable Object> out = new LinkedHashMap<>();
            map.forEach((key, part) -> out.put(String.valueOf(key), of(part)));
            return out;
        }
        if (value instanceof Collection<?> list) {
            List<@Nullable Object> out = new ArrayList<>();
            list.forEach(part -> out.add(of(part)));
            return out;
        }
        return value;
    }

    private static void refuseFloats(JsonNode node) {
        if (node.isFloatingPointNumber()) {
            throw new IllegalArgumentException("a figure must be a decimal string, never a JSON number with a fraction: " + node);
        }
        node.forEach(Wire::refuseFloats);
    }
}
