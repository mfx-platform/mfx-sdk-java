package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A symbol's whole order book as one terminal read it ({@link MarketData#bookOf}): replace yours with every
 * one. Prices and volumes are exact as the platform sent them. Not a reconstruction: MFX Hub builds no book
 * from ticks or from several terminals.
 *
 * @param channel the {@code book:} channel it came on
 * @param seq its sequence number on the channel
 * @param bids buy orders, highest price first
 * @param asks sell orders, lowest price first
 * @param brokerTs when the terminal read it, on the broker's clock, as a tick's {@code broker_ts}
 * @param levels the most a side the platform reads: a side shorter than it is the broker's whole side
 */
public record Book(String channel, @Nullable Long seq, List<Level> bids, List<Level> asks, String brokerTs, int levels) {
    /** The book, its sides copied so they cannot be changed. */
    public Book {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }

    /**
     * One price of one side.
     *
     * @param price the price
     * @param volume the volume at it
     */
    public record Level(BigDecimal price, BigDecimal volume) {
    }

    /**
     * The highest buy price.
     *
     * @return the price; null when the book has no bids
     */
    public @Nullable BigDecimal bestBid() {
        return bids.isEmpty() ? null : bids.get(0).price();
    }

    /**
     * The lowest sell price.
     *
     * @return the price; null when the book has no asks
     */
    public @Nullable BigDecimal bestAsk() {
        return asks.isEmpty() ? null : asks.get(0).price();
    }
}
