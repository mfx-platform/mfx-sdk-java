package com.mfxhub.sdk;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Builds the channel keys the platform accepts, from ids rather than names.
 *
 * <p>Brokers rename servers and symbols, and a subscription keyed on a name silently stops matching when
 * they do, so a channel is keyed by id and built here rather than formatted by hand.
 */
public final class Channel {
    /** Every platform event for this tenant. */
    public static final String EVENTS = "events";

    private static final Pattern SHAPE = Pattern.compile(
            "^(md:[0-9a-f-]{36}:[^:]+|candles:[0-9a-f-]{36}:[^:]+:(M1|M5|M15|M30|H1|H4|D1|W1|MN1)|events"
                    + "|results:[0-9a-f-]{36}|account:[0-9a-f-]{36}|ea:[0-9a-f-]{36}|book:[0-9a-f-]{36}:[^:]+)$");

    private Channel() {
    }

    /**
     * Ticks for one symbol on one server, in the broker's own name for it.
     *
     * @param serverId the server
     * @param symbol the broker's symbol
     * @return the key
     */
    public static String md(UUID serverId, String symbol) {
        return checked("md:" + serverId + ":" + symbol);
    }

    /**
     * Closed candles; only closed ones are published.
     *
     * @param serverId the server
     * @param symbol the broker's symbol
     * @param timeframe M1, M5, M15, M30, H1, H4, D1, W1 or MN1
     * @return the key
     */
    public static String candles(UUID serverId, String symbol, String timeframe) {
        return checked("candles:" + serverId + ":" + symbol + ":" + timeframe);
    }

    /**
     * Execution results for one terminal: where a command is finally answered.
     *
     * @param terminalId the terminal
     * @return the key
     */
    public static String results(UUID terminalId) {
        return checked("results:" + terminalId);
    }

    /**
     * One terminal's live account: a snapshot, then marks.
     *
     * @param terminalId the terminal
     * @return the key
     */
    public static String account(UUID terminalId) {
        return checked("account:" + terminalId);
    }

    /**
     * What the tenant's own expert on this terminal writes. Replayed up to 15 minutes on resume.
     *
     * @param terminalId the terminal
     * @return the key
     */
    public static String ea(UUID terminalId) {
        return checked("ea:" + terminalId);
    }

    /**
     * The symbol's order book as one of the tenant's terminals reads it.
     *
     * @param serverId the server
     * @param symbol the broker's symbol
     * @return the key
     */
    public static String book(UUID serverId, String symbol) {
        return checked("book:" + serverId + ":" + symbol);
    }

    /**
     * The key itself, refused when the platform would refuse it.
     *
     * @param channel the key
     * @return the same key
     * @throws IllegalArgumentException not a channel key the platform accepts
     */
    public static String checked(String channel) {
        Objects.requireNonNull(channel, "channel");
        if (!SHAPE.matcher(channel).matches()) {
            throw new IllegalArgumentException("channel: '" + channel
                    + "' is not a channel key the platform accepts: ids are uuids and a symbol carries no colon");
        }
        return channel;
    }

    /**
     * These replay on resume. Ticks and candles are deliberately not replayed (a stale tick is worse than
     * none), and a book answers every subscribe with the newest one.
     */
    static boolean replays(String channel) {
        return channel.equals(EVENTS) || channel.startsWith("results:") || channel.startsWith("account:")
                || channel.startsWith("ea:");
    }
}
