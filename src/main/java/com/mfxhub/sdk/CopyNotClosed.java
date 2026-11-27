package com.mfxhub.sdk;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * A copied position a requested close did not close.
 *
 * @param symbol the follower's symbol
 * @param volume the volume left
 * @param why {@code netting}, {@code not_copied}, {@code copy_mismatch} or {@code gave_up}; null when not known
 */
public record CopyNotClosed(String symbol, @Nullable BigDecimal volume, @Nullable String why) {
    static CopyNotClosed read(EventData d) {
        return new CopyNotClosed(d.text("symbol"), d.decimalOrNull("volume"), d.textOrNull("why"));
    }
}
