package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One terminal's account as the stream last said it was: a snapshot, with the marks applied.
 *
 * <p>{@link #positions()} is keyed by ticket, because that is how a mark names the position it moves, in
 * the snapshot's own order, so drawing it twice reads the same way.
 *
 * @param terminalId the terminal
 * @param asOf the {@code as_of} of the last message applied, not of the snapshot: the figures are that fresh
 * @param seq the {@code seq} of the last message applied; a jump to the next message's is a gap
 * @param base the {@code seq} of the snapshot this view is drawn from, which every marks message names
 * @param figures every other field the messages carried (balance, equity and the like), as the platform
 *     wrote them, a field added after this SDK shipped included
 * @param positions each open position's fields, by ticket
 * @param pending each pending order's fields
 */
public record AccountView(String terminalId, @Nullable String asOf, @Nullable Long seq, @Nullable Long base,
        Map<String, JsonNode> figures, Map<Long, Map<String, JsonNode>> positions, List<Map<String, JsonNode>> pending) {

    /** The view, copied so it cannot be changed, the order of every map kept. */
    public AccountView {
        figures = Collections.unmodifiableMap(new LinkedHashMap<>(figures));
        Map<Long, Map<String, JsonNode>> rows = new LinkedHashMap<>();
        positions.forEach((ticket, row) -> rows.put(ticket, Collections.unmodifiableMap(new LinkedHashMap<>(row))));
        positions = Collections.unmodifiableMap(rows);
        pending = pending.stream().map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();
    }
}
