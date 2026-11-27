package com.mfxhub.sdk;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * An instant on the wire in ISO 8601: seconds
 * always, a fraction only when there is one and then six digits, and the offset as {@code +00:00}, so a
 * request is the same bytes from every SDK.
 */
final class Times {
    private static final DateTimeFormatter WHOLE = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxxx");
    private static final DateTimeFormatter FRACTION = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSxxx");

    private Times() {
    }

    static String iso(OffsetDateTime moment) {
        OffsetDateTime micros = moment.truncatedTo(ChronoUnit.MICROS);
        return micros.getNano() == 0 ? micros.format(WHOLE) : micros.format(FRACTION);
    }
}
