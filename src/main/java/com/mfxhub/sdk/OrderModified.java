package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code order.modified}: a pending order's price, stops, expiry or volume changed.
 *
 * @param schemaVersion the event's schema version
 * @param terminalId the terminal
 * @param eventId the same for a replay of the same change: drop one you have seen
 * @param ticket the order's ticket
 * @param symbol the broker's symbol
 * @param side {@code buy} or {@code sell}
 * @param orderType {@code limit}, {@code stop} or {@code stop_limit}
 * @param volume lots still to fill
 * @param price the price; on a stop-limit order, the stop price
 * @param stopLimitPrice a stop-limit order's limit, placed when the market reaches the price
 * @param sl the stop loss
 * @param tp the take profit
 * @param placedTime when it was placed
 * @param expiresAt null when good till cancelled
 * @param magic the expert's magic number
 * @param comment the order's comment
 * @param ts when the change was seen
 * @param idempotencyKey the Idempotency-Key of the platform command that made this change; null for a change the platform did not make
 */
public record OrderModified(String schemaVersion, UUID terminalId, String eventId, long ticket, String symbol, String side, String orderType, BigDecimal volume, BigDecimal price, @Nullable BigDecimal stopLimitPrice, @Nullable BigDecimal sl, @Nullable BigDecimal tp, OffsetDateTime placedTime, @Nullable OffsetDateTime expiresAt, long magic, String comment, OffsetDateTime ts, @Nullable String idempotencyKey) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "order.modified";

    @Override
    public String kind() {
        return KIND;
    }

    static OrderModified read(EventData d) {
        return new OrderModified(d.text("schema_version"), d.uuid("terminal_id"), d.text("event_id"), d.integer("ticket"), d.text("symbol"), d.text("side"), d.text("order_type"), d.decimal("volume"), d.decimal("price"), d.decimalOrNull("stop_limit_price"), d.decimalOrNull("sl"), d.decimalOrNull("tp"), d.instant("placed_time"), d.instantOrNull("expires_at"), d.integer("magic"), d.text("comment"), d.instant("ts"), d.textOrNull("idempotency_key"));
    }
}
