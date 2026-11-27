package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code position.closed}: a position left the account.
 *
 * @param schemaVersion the event's schema version
 * @param terminalId the terminal
 * @param eventId the same for a replay of the same change: drop one you have seen
 * @param ticket the position's ticket
 * @param symbol the broker's symbol
 * @param side {@code buy} or {@code sell}
 * @param volume lots
 * @param openPrice the open price
 * @param priceCurrent the current price
 * @param sl the stop loss
 * @param tp the take profit
 * @param profit the profit, in the account's currency
 * @param swap the swap
 * @param openTime when it opened
 * @param magic the expert's magic number
 * @param comment the position's comment
 * @param ts when the change was seen
 * @param idempotencyKey the Idempotency-Key of the platform command that made this change; null for a change the platform did not make
 * @param closePrice the close price
 * @param closeReason {@code client}, {@code mobile}, {@code web}, {@code expert}, {@code sl}, {@code tp}, {@code stop_out}, {@code rollover}, {@code vmargin}, {@code split} or {@code unknown} today
 * @param dealReasonRaw MetaTrader's own word for the closing deal's reason
 */
public record PositionClosed(String schemaVersion, UUID terminalId, String eventId, long ticket, String symbol, String side, BigDecimal volume, BigDecimal openPrice, @Nullable BigDecimal priceCurrent, @Nullable BigDecimal sl, @Nullable BigDecimal tp, @Nullable BigDecimal profit, @Nullable BigDecimal swap, OffsetDateTime openTime, long magic, String comment, OffsetDateTime ts, @Nullable String idempotencyKey, @Nullable BigDecimal closePrice, String closeReason, @Nullable String dealReasonRaw) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "position.closed";

    @Override
    public String kind() {
        return KIND;
    }

    static PositionClosed read(EventData d) {
        return new PositionClosed(d.text("schema_version"), d.uuid("terminal_id"), d.text("event_id"), d.integer("ticket"), d.text("symbol"), d.text("side"), d.decimal("volume"), d.decimal("open_price"), d.decimalOrNull("price_current"), d.decimalOrNull("sl"), d.decimalOrNull("tp"), d.decimalOrNull("profit"), d.decimalOrNull("swap"), d.instant("open_time"), d.integer("magic"), d.text("comment"), d.instant("ts"), d.textOrNull("idempotency_key"), d.decimalOrNull("close_price"), d.text("close_reason"), d.textOrNull("deal_reason_raw"));
    }
}
