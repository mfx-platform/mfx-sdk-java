package com.mfxhub.sdk;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * What a copy became on the follower account.
 *
 * @param symbol the follower's symbol
 * @param side {@code buy} or {@code sell}
 * @param volume in the follower venue's unit
 * @param price the fill price
 */
public record CopyAs(String symbol, String side, BigDecimal volume, @Nullable BigDecimal price) {
    static CopyAs read(EventData d) {
        return new CopyAs(d.text("symbol"), d.text("side"), d.decimal("volume"), d.decimalOrNull("price"));
    }
}
