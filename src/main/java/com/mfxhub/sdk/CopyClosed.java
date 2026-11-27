package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Copied volume a stop-out or a requested close closed.
 *
 * @param symbol the follower's symbol
 * @param volume the volume closed
 * @param commandId the close's command
 */
public record CopyClosed(String symbol, BigDecimal volume, @Nullable UUID commandId) {
    static CopyClosed read(EventData d) {
        return new CopyClosed(d.text("symbol"), d.decimal("volume"), d.uuidOrNull("command_id"));
    }
}
