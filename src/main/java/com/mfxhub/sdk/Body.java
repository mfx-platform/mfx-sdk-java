package com.mfxhub.sdk;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A request body in the API's own field names, written by {@link MfxJson}. A field the caller did not
 * set is left out of the body, never sent as null or as a default the caller did not choose.
 */
final class Body {
    private final Map<String, Object> fields = new LinkedHashMap<>();

    static Body of() {
        return new Body();
    }

    /** A field, left out when null. */
    Body put(String name, @Nullable Object value) {
        if (value instanceof java.time.OffsetDateTime moment) {
            fields.put(name, Times.iso(moment));
        } else if (value != null) {
            fields.put(name, value);
        }
        return this;
    }

    Map<String, Object> build() {
        return fields;
    }
}
