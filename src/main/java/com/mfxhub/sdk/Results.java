package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Reading the fields of a result the SDK builds itself, leniently: a field of the wrong kind reads as none. */
final class Results {
    private Results() {
    }

    static @Nullable String text(@Nullable JsonNode value) {
        return value != null && value.isTextual() && !value.textValue().isEmpty() ? value.textValue() : null;
    }

    /** A text field as it arrived, empty included; null when absent or not text. */
    static @Nullable String raw(@Nullable JsonNode value) {
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    static @Nullable Long whole(@Nullable JsonNode value) {
        return value != null && value.isIntegralNumber() ? value.longValue() : null;
    }

    static @Nullable Integer integer(@Nullable JsonNode value) {
        return value != null && value.isInt() ? value.intValue() : null;
    }

    /**
     * Prices, volumes and money are decimals as strings, read exactly. A number token is read from its
     * text, never through a double.
     */
    static @Nullable BigDecimal decimal(@Nullable JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual()) {
            try {
                return new BigDecimal(value.textValue());
            } catch (NumberFormatException notDecimal) {
                return null;
            }
        }
        return null;
    }

    static @Nullable UUID uuid(@Nullable JsonNode value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }

    static @Nullable OffsetDateTime time(@Nullable JsonNode value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException notTime) {
            return null;
        }
    }
}
