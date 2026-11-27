package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code position.opened}: a position appeared on the account.
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
 * @param orderTicket the pending order it filled from; null for a market order
 */
public record PositionOpened(String schemaVersion, UUID terminalId, String eventId, long ticket, String symbol, String side, BigDecimal volume, BigDecimal openPrice, @Nullable BigDecimal priceCurrent, @Nullable BigDecimal sl, @Nullable BigDecimal tp, @Nullable BigDecimal profit, @Nullable BigDecimal swap, OffsetDateTime openTime, long magic, String comment, OffsetDateTime ts, @Nullable String idempotencyKey, @Nullable Long orderTicket) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "position.opened";

    @Override
    public String kind() {
        return KIND;
    }

    static PositionOpened read(EventData d) {
        return new PositionOpened(d.text("schema_version"), d.uuid("terminal_id"), d.text("event_id"), d.integer("ticket"), d.text("symbol"), d.text("side"), d.decimal("volume"), d.decimal("open_price"), d.decimalOrNull("price_current"), d.decimalOrNull("sl"), d.decimalOrNull("tp"), d.decimalOrNull("profit"), d.decimalOrNull("swap"), d.instant("open_time"), d.integer("magic"), d.text("comment"), d.instant("ts"), d.textOrNull("idempotency_key"), d.integerOrNull("order_ticket"));
    }
}
