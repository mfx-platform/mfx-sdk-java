package com.mfxhub.sdk;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * The leader's deal a copy event is about.
 *
 * @param action {@code open}, {@code modify}, {@code close}, {@code close_part}, {@code pending_place}, {@code pending_modify} or {@code pending_cancel}
 * @param side {@code buy} or {@code sell}
 * @param units the leader's volume in base units
 * @param price the price
 * @param instrumentKey the venue-neutral instrument key ({@code fx:EURUSD}), when the leader's symbol has one
 */
public record CopyDeal(String action, String side, BigDecimal units, @Nullable BigDecimal price, @Nullable String instrumentKey) {
    static CopyDeal read(EventData d) {
        return new CopyDeal(d.text("action"), d.text("side"), d.decimal("units"), d.decimalOrNull("price"), d.textOrNull("instrument_key"));
    }
}
