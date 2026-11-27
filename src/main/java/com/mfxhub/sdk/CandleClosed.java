package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code md.candle_closed} on a {@code candles:} channel: a closed bar, sent once and never replayed. {@code prevTs} is the bar before it: if it is not the last one you have, you missed one.
 *
 * @param schemaVersion the event's schema version
 * @param serverId the server
 * @param symbol the broker's symbol
 * @param timeframe the bar's length
 * @param ts its open time
 * @param o the open
 * @param h the high
 * @param l the low
 * @param c the close
 * @param v tick volume
 * @param prevTs the bar before it
 */
public record CandleClosed(String schemaVersion, UUID serverId, String symbol, String timeframe, OffsetDateTime ts, BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c, long v, OffsetDateTime prevTs) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "md.candle_closed";

    @Override
    public String kind() {
        return KIND;
    }

    static CandleClosed read(EventData d) {
        return new CandleClosed(d.text("schema_version"), d.uuid("server_id"), d.text("symbol"), d.text("timeframe"), d.instant("ts"), d.decimal("o"), d.decimal("h"), d.decimal("l"), d.decimal("c"), d.integer("v"), d.instant("prev_ts"));
    }
}
