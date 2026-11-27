package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The stream's ordering rules for an {@code account:} channel, applied to the events you hand it.
 *
 * <p>Not a subscriber: it holds no socket and asks for nothing. The caller drives the stream, which lets
 * one connection follow several terminals. Not thread-safe: hand it the events of one stream in order.
 *
 * <pre>{@code
 * LiveAccount live = new LiveAccount(terminalId);
 * for (StreamEvent event : stream) {
 *     AccountView view = live.apply(event);
 *     if (view != null) draw(view); else if (live.behind()) stream.resume(...);
 * }
 * }</pre>
 */
public final class LiveAccount {
    /** A snapshot message's type: the whole account. */
    public static final String SNAPSHOT = "account.snapshot";
    /** A marks message's type: the open positions' prices since the snapshot. */
    public static final String MARKS = "account.marks";

    /**
     * Carried by a message but not part of the account: protocol, a field of the view already, or the
     * delta itself. Everything else is a figure, one the platform adds later included.
     */
    private static final Set<String> NOT_A_FIGURE = Set.of("kind", "type", "seq", "base", "as_of", "terminal_id", "schema_version", "marks");
    /** A snapshot's fields that belong to the position set: only another snapshot replaces them. */
    private static final Set<String> SET = Set.of("positions", "pending", "pending_as_of", "open_positions_count");
    /** What a mark carries after its ticket, in order. */
    private static final List<String> MARKED = List.of("price_current", "profit", "swap");

    private final String terminalId;
    private @Nullable AccountView view;
    private boolean behind;

    /**
     * Follows one terminal's account.
     *
     * @param terminalId the terminal
     */
    public LiveAccount(UUID terminalId) {
        this.terminalId = Objects.requireNonNull(terminalId, "terminalId").toString();
    }

    /**
     * The account, or null until the first snapshot arrives: an account with no positions and a zero
     * balance is a statement about a tenant's money, and "we have not been told yet" is not that statement.
     *
     * @return the view; null before the first snapshot or after it was dropped
     */
    public @Nullable AccountView view() {
        return view;
    }

    /**
     * True when a message could not be applied, so this view is not the account. The remedy is
     * {@code resume}, which answers with a snapshot; until one comes (within 60 s by itself), do not show
     * these figures as live.
     *
     * @return whether the view is behind
     */
    public boolean behind() {
        return behind;
    }

    /**
     * Takes one stream event. Null covers several things a caller treats alike (draw nothing new, then
     * check {@link #behind()}): an event for another channel, terminal or kind; a marks message that does
     * not apply; a resync; a sequence gap; a refused channel.
     *
     * @param event what the stream delivered
     * @return the new view; null when nothing was applied
     */
    public @Nullable AccountView apply(StreamEvent event) {
        if (event instanceof StreamEvent.ChannelRefused refused) {
            if (mine(refused.channel(), null)) {
                // The channel this view is fed by is gone: nothing will correct what we hold again.
                view = null;
                behind = true;
            }
            return null;
        }
        if (event instanceof StreamEvent.Resync resync) {
            if (mine(resync.channel(), null)) {
                // The replay window has passed: only a snapshot can help.
                view = null;
                behind = true;
            }
            return null;
        }
        StreamEvent.Message message = (StreamEvent.Message) event;
        if (!mine(message.channel(), present(message.data().get("terminal_id")))) {
            return null;
        }
        String kind = message.type() != null && !message.type().isEmpty() ? message.type() : text(message.data().get("kind"));
        if (SNAPSHOT.equals(kind)) {
            return draw(message);
        }
        if (!MARKS.equals(kind)) {
            return null;
        }
        return mark(message);
    }

    /**
     * Is this about this terminal? A message that names a different terminal is not ours whatever the
     * channel said: a mark can never be applied to the wrong tenant's positions.
     */
    private boolean mine(String channel, @Nullable JsonNode named) {
        if (named != null && !textOf(named).equals(terminalId)) {
            return false;
        }
        return channel.isEmpty() || channel.endsWith(terminalId);
    }

    /** Rule 1: every snapshot replaces the whole view, its base taken from the message, never from seq. */
    private AccountView draw(StreamEvent.Message message) {
        Map<String, JsonNode> data = message.data();
        behind = false;
        Long base = baseOf(message);
        Map<Long, Map<String, JsonNode>> positions = new LinkedHashMap<>();
        for (Map<String, JsonNode> row : rows(data.get("positions"))) {
            if (row.containsKey("ticket")) {
                positions.put(whole(row.get("ticket")), row);
            }
        }
        AccountView drawn = new AccountView(terminalId, nullableText(data.get("as_of")), message.seq(), base == null ? message.seq() : base,
                figures(data), positions, rows(data.get("pending")));
        view = drawn;
        return drawn;
    }

    /**
     * Rules 2 and 3: applies a marks message, or drops it whole and falls behind when nothing is held, its
     * base is not the snapshot held, its seq skips one, it names a ticket not held, or a mark is not the
     * four-element array the contract defines. A message is one account at one instant: applying half of
     * it would draw an account that never existed.
     */
    private @Nullable AccountView mark(StreamEvent.Message message) {
        AccountView held = view;
        if (held == null) {
            behind = true;
            return null;
        }
        Long base = baseOf(message);
        List<Map.Entry<Long, List<JsonNode>>> marks = marks(message.data().get("marks"));
        boolean refuse = (base != null && !base.equals(held.base())) || gapped(held.seq(), message.seq()) || marks == null
                || marks.stream().anyMatch(m -> !held.positions().containsKey(m.getKey()));
        if (refuse || marks == null) {
            behind = true;
            return null;
        }
        Map<Long, Map<String, JsonNode>> positions = new LinkedHashMap<>();
        held.positions().forEach((ticket, row) -> positions.put(ticket, new LinkedHashMap<>(row)));
        for (Map.Entry<Long, List<JsonNode>> one : marks) {
            Map<String, JsonNode> row = positions.get(one.getKey());
            for (int i = 0; i < MARKED.size(); i++) {
                row.put(MARKED.get(i), one.getValue().get(i));
            }
        }
        Map<String, JsonNode> figures = new LinkedHashMap<>(held.figures());
        // The figures a message carries replace the ones held; a keep-alive carries them too.
        figures.putAll(figures(message.data()));
        String asOf = nullableText(message.data().get("as_of"));
        AccountView marked = new AccountView(held.terminalId(), asOf == null || asOf.isEmpty() ? held.asOf() : asOf,
                message.seq() == null ? held.seq() : message.seq(), held.base(), figures, positions, held.pending());
        view = marked;
        return marked;
    }

    /** {@code marks} as (ticket, [price_current, profit, swap]); null when unreadable. An empty list is a keep-alive. */
    private static @Nullable List<Map.Entry<Long, List<JsonNode>>> marks(@Nullable JsonNode value) {
        if (value == null || !value.isArray()) {
            return null;
        }
        List<Map.Entry<Long, List<JsonNode>>> out = new ArrayList<>();
        for (JsonNode mark : value) {
            if (!mark.isArray() || mark.size() != 1 + MARKED.size()) {
                return null;
            }
            long ticket = whole(mark.get(0));
            if (ticket < 0) {
                return null;
            }
            List<JsonNode> values = new ArrayList<>();
            for (int i = 1; i < mark.size(); i++) {
                values.add(mark.get(i));
            }
            out.add(Map.entry(ticket, values));
        }
        return out;
    }

    /**
     * The seq of the snapshot this message applies to: the envelope's {@code base}, else the payload's;
     * null when the platform did not say, which is not a mismatch.
     */
    private static @Nullable Long baseOf(StreamEvent.Message message) {
        if (message.base() != null) {
            return message.base();
        }
        JsonNode inData = present(message.data().get("base"));
        return inData == null ? null : whole(inData);
    }

    private static Map<String, JsonNode> figures(Map<String, JsonNode> data) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        data.forEach((name, value) -> {
            if (!NOT_A_FIGURE.contains(name) && !SET.contains(name)) {
                out.put(name, value);
            }
        });
        return out;
    }

    /** The object rows of a list-valued field, and nothing from a field that is not a list of them. */
    private static List<Map<String, JsonNode>> rows(@Nullable JsonNode value) {
        List<Map<String, JsonNode>> out = new ArrayList<>();
        if (value != null && value.isArray()) {
            for (JsonNode row : value) {
                if (row.isObject()) {
                    Map<String, JsonNode> fields = new LinkedHashMap<>();
                    row.fields().forEachRemaining(f -> fields.put(f.getKey(), f.getValue()));
                    out.add(fields);
                }
            }
        }
        return out;
    }

    /** Unknown on either side is not a gap: a stream that numbers nothing cannot prove a loss. */
    private static boolean gapped(@Nullable Long held, @Nullable Long arriving) {
        return held != null && arriving != null && arriving > held + 1;
    }

    /** A ticket as a number, from a number or its digits; -1 when it is neither. */
    private static long whole(@Nullable JsonNode value) {
        if (value == null) {
            return -1;
        }
        if (value.isIntegralNumber()) {
            return value.longValue();
        }
        if (value.isNumber()) {
            return (long) value.doubleValue();
        }
        if (value.isTextual()) {
            try {
                return Long.parseLong(value.textValue().strip());
            } catch (NumberFormatException notWhole) {
                return -1;
            }
        }
        return -1;
    }

    private static @Nullable JsonNode present(@Nullable JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode() ? null : value;
    }

    private static String textOf(JsonNode value) {
        return value.isTextual() ? value.textValue() : value.toString();
    }

    private static @Nullable String nullableText(@Nullable JsonNode value) {
        JsonNode there = present(value);
        return there == null ? null : textOf(there);
    }

    private static String text(@Nullable JsonNode value) {
        String text = nullableText(value);
        return text == null ? "" : text;
    }
}
