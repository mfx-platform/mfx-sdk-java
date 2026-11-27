package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One message's data, read by the contract's rules: a required field that is
 * missing is an {@link IllegalArgumentException} naming it; an optional one that is absent or null is null.
 * On the stream every value may arrive as text, so each kind is read from its text as well.
 */
final class EventData {
    private final String kind;
    private final Map<String, JsonNode> data;

    EventData(String kind, Map<String, JsonNode> data) {
        this.kind = kind;
        this.data = data;
    }

    String kind() {
        return kind;
    }

    private @Nullable JsonNode raw(String key, boolean required) {
        JsonNode value = data.get(key);
        if (value == null || value.isNull() || value.isMissingNode()) {
            if (required) {
                throw new IllegalArgumentException(kind + " carries no `" + key + "`");
            }
            return null;
        }
        return value;
    }

    private static String asText(JsonNode value) {
        return value.isTextual() ? value.textValue() : value.toString();
    }

    private IllegalArgumentException bad(String key, String what, @Nullable Throwable cause) {
        IllegalArgumentException failure = new IllegalArgumentException(kind + " `" + key + "` is not " + what);
        if (cause != null) {
            failure.initCause(cause);
        }
        return failure;
    }

    String text(String key) {
        return asText(java.util.Objects.requireNonNull(raw(key, true)));
    }

    @Nullable String textOrNull(String key) {
        JsonNode value = raw(key, false);
        return value == null ? null : asText(value);
    }

    /** Text, where the platform sends an empty string for none (a flat event's field). */
    @Nullable String blankNull(String key) {
        String value = textOrNull(key);
        return value == null || value.isEmpty() ? null : value;
    }

    /** A nested part: the stream carries it as JSON text, a webhook body as JSON itself. */
    @Nullable JsonNode json(String key) {
        JsonNode value = raw(key, false);
        if (value != null && value.isTextual()) {
            if (value.textValue().isEmpty()) {
                return null;
            }
            try {
                return MfxJson.MAPPER.readTree(value.textValue());
            } catch (IOException notJson) {
                throw bad(key, "JSON", notJson);
            }
        }
        return value;
    }

    UUID uuid(String key) {
        return parseUuid(key, text(key));
    }

    @Nullable UUID uuidOrNull(String key) {
        String value = textOrNull(key);
        return value == null ? null : parseUuid(key, value);
    }

    private UUID parseUuid(String key, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notUuid) {
            throw bad(key, "a uuid", notUuid);
        }
    }

    long integer(String key) {
        return parseWhole(key, text(key));
    }

    @Nullable Long integerOrNull(String key) {
        String value = textOrNull(key);
        return value == null ? null : parseWhole(key, value);
    }

    private long parseWhole(String key, String value) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException notWhole) {
            throw bad(key, "a whole number", notWhole);
        }
    }

    BigDecimal decimal(String key) {
        return parseDecimal(key, text(key));
    }

    @Nullable BigDecimal decimalOrNull(String key) {
        String value = textOrNull(key);
        return value == null ? null : parseDecimal(key, value);
    }

    private BigDecimal parseDecimal(String key, String value) {
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException notDecimal) {
            throw bad(key, "a decimal", notDecimal);
        }
    }

    @Nullable Double numberOrNull(String key) {
        String value = textOrNull(key);
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.strip());
        } catch (NumberFormatException notNumber) {
            throw bad(key, "a number", notNumber);
        }
    }

    OffsetDateTime instant(String key) {
        return parseInstant(key, text(key));
    }

    @Nullable OffsetDateTime instantOrNull(String key) {
        String value = textOrNull(key);
        return value == null ? null : parseInstant(key, value);
    }

    /** An ISO 8601 instant; one written without an offset is read as UTC. */
    static @Nullable OffsetDateTime instantOf(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException withoutOffset) {
            try {
                return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException notInstant) {
                return null;
            }
        }
    }

    private OffsetDateTime parseInstant(String key, String value) {
        OffsetDateTime moment = instantOf(value);
        if (moment == null) {
            throw bad(key, "an ISO 8601 instant", null);
        }
        return moment;
    }

    /** A nested object the contract requires, read by the same rules. */
    EventData part(String key) {
        JsonNode value = json(key);
        if (value == null || !value.isObject()) {
            if (value == null) {
                throw new IllegalArgumentException(kind + " carries no `" + key + "`");
            }
            throw bad(key, "an object", null);
        }
        return new EventData(kind + " `" + key + "`", fields(value));
    }

    /** A list of nested objects; absent or null is an empty list. */
    List<EventData> parts(String key) {
        JsonNode value = json(key);
        List<EventData> out = new ArrayList<>();
        if (value != null && value.isArray()) {
            for (JsonNode item : value) {
                out.add(new EventData(kind + " `" + key + "`", fields(item)));
            }
        }
        return out;
    }

    /** A nested object of milliseconds by name; absent or null is empty, and a null value is left out. */
    Map<String, Double> timings(String key) {
        JsonNode value = json(key);
        Map<String, Double> out = new LinkedHashMap<>();
        if (value != null && value.isObject()) {
            value.fields().forEachRemaining(field -> {
                if (!field.getValue().isNull()) {
                    out.put(field.getKey(), Double.valueOf(asText(field.getValue()).strip()));
                }
            });
        }
        return out;
    }

    /** A uuid where the platform sends an empty string for none. */
    @Nullable UUID blankUuid(String key) {
        String value = blankNull(key);
        return value == null ? null : parseUuid(key, value);
    }

    /** A whole number where the platform sends an empty string for none. */
    @Nullable Long blankInteger(String key) {
        String value = blankNull(key);
        return value == null ? null : parseWhole(key, value);
    }

    /** A decimal where the platform sends an empty string for none. */
    @Nullable BigDecimal blankDecimal(String key) {
        String value = blankNull(key);
        return value == null ? null : parseDecimal(key, value);
    }

    /** A list of words, carried as JSON text on the stream; absent or null is empty. */
    List<String> words(String key) {
        JsonNode value = json(key);
        List<String> out = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(word -> out.add(asText(word)));
        }
        return out;
    }

    /** A nested object the contract requires, as the platform wrote it. */
    Map<String, JsonNode> object(String key) {
        JsonNode value = json(key);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(kind + " carries no `" + key + "`");
        }
        return fields(value);
    }

    static Map<String, JsonNode> fields(JsonNode object) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (object.isObject()) {
            object.fields().forEachRemaining(field -> out.put(field.getKey(), field.getValue()));
        }
        return out;
    }
}
