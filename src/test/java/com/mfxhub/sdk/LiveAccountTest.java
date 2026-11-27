package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The live account view: the stream's ordering rules for an account channel. */
class LiveAccountTest {
    private static final UUID TERMINAL = TerminalWritesTest.ID;
    private static final String CHANNEL = Channel.account(TERMINAL);

    private static StreamEvent.Message message(Long seq, String type, String data, Long base) {
        Map<String, JsonNode> fields = MfxJson.MAPPER.convertValue(read(data), new TypeReference<Map<String, JsonNode>>() { });
        return new StreamEvent.Message(CHANNEL, seq, type, fields, base);
    }

    private static JsonNode read(String json) {
        try {
            return MfxJson.MAPPER.readTree(json);
        } catch (java.io.IOException bad) {
            throw new IllegalArgumentException(bad);
        }
    }

    private static final String SNAPSHOT = "{\"terminal_id\": \"" + TERMINAL + "\", \"as_of\": \"2026-11-17T09:00:00Z\", \"balance\": \"1000.00\","
            + " \"equity\": \"1001.50\", \"positions\": [{\"ticket\": 11, \"symbol\": \"EURUSD\", \"profit\": \"1.00\"},"
            + " {\"ticket\": 12, \"symbol\": \"GBPUSD\", \"profit\": \"0.50\"}], \"pending\": [{\"ticket\": 20}], \"open_positions_count\": 2}";

    /**
     * Given a snapshot, then marks on its base in seq order, then a keep-alive / When each is applied /
     * Then the snapshot draws the whole view with its positions by ticket in their order and its figures
     * apart from the position set, each mark sets a position's price, profit and swap, the figures a
     * message carries replace the ones held, and an empty marks list keeps the view current.
     */
    @Test
    void aSnapshotIsDrawnAndItsMarksAppliedInOrder() {
        LiveAccount live = new LiveAccount(TERMINAL);
        assertNull(live.view());

        AccountView drawn = live.apply(message(5L, LiveAccount.SNAPSHOT, SNAPSHOT, null));
        assertNotNull(drawn);
        assertEquals(List.of(11L, 12L), List.copyOf(drawn.positions().keySet()));
        assertEquals(5L, drawn.base());
        assertEquals("1000.00", drawn.figures().get("balance").asText());
        assertFalse(drawn.figures().containsKey("positions"));
        assertFalse(drawn.figures().containsKey("open_positions_count"));
        assertEquals(1, drawn.pending().size());

        AccountView marked = live.apply(message(6L, LiveAccount.MARKS, "{\"as_of\": \"2026-11-17T09:00:01Z\", \"equity\": \"1003.10\","
                + " \"marks\": [[12, \"1.27010\", \"2.10\", \"-0.05\"]]}", 5L));
        assertNotNull(marked);
        assertEquals("2.10", marked.positions().get(12L).get("profit").asText());
        assertEquals("-0.05", marked.positions().get(12L).get("swap").asText());
        assertEquals("1.00", marked.positions().get(11L).get("profit").asText());
        assertEquals("1003.10", marked.figures().get("equity").asText());
        assertEquals("1000.00", marked.figures().get("balance").asText());
        assertEquals("2026-11-17T09:00:01Z", marked.asOf());

        AccountView kept = live.apply(message(7L, LiveAccount.MARKS, "{\"marks\": []}", 5L));
        assertNotNull(kept);
        assertEquals(7L, kept.seq());
        assertFalse(live.behind());
    }

    /**
     * Given marks that skip a seq, name another base, name a ticket not held, or are not four-element
     * arrays, and a resync, a refusal and another terminal's message / When each is applied / Then nothing
     * is applied and the view falls behind until the next snapshot draws it again, a resync or a refusal of
     * this channel drops the view, and another terminal's message is ignored.
     */
    @Test
    void aMarkThatCannotApplyLeavesTheViewBehindUntilTheNextSnapshot() {
        List<List<String>> refused = List.of(List.of("{\"marks\": [[11, \"1\", \"2\", \"3\"]]}", "9", "5"),
                List.of("{\"marks\": [[11, \"1\", \"2\", \"3\"]]}", "6", "4"), List.of("{\"marks\": [[99, \"1\", \"2\", \"3\"]]}", "6", "5"),
                List.of("{\"marks\": [[11, \"1\", \"2\"]]}", "6", "5"), List.of("{\"marks\": \"none\"}", "6", "5"));
        for (List<String> parts : refused) {
            String one = parts.get(0);
            LiveAccount live = new LiveAccount(TERMINAL);
            live.apply(message(5L, LiveAccount.SNAPSHOT, SNAPSHOT, null));
            assertNull(live.apply(message(Long.valueOf(parts.get(1)), LiveAccount.MARKS, one, Long.valueOf(parts.get(2)))), one);
            assertTrue(live.behind(), one);
            assertNotNull(live.apply(message(10L, LiveAccount.SNAPSHOT, SNAPSHOT, null)));
            assertFalse(live.behind(), one);
        }

        LiveAccount live = new LiveAccount(TERMINAL);
        assertNull(live.apply(message(1L, LiveAccount.MARKS, "{\"marks\": []}", null)));
        assertTrue(live.behind());
        live.apply(message(5L, LiveAccount.SNAPSHOT, SNAPSHOT, null));
        String other = "{\"terminal_id\": \"" + UUID.randomUUID() + "\", \"marks\": []}";
        assertNull(live.apply(message(6L, LiveAccount.MARKS, other, 5L)));
        assertFalse(live.behind());
        assertNull(live.apply(new StreamEvent.Resync(CHANNEL)));
        assertNull(live.view());
        assertTrue(live.behind());
        live.apply(message(5L, LiveAccount.SNAPSHOT, SNAPSHOT, null));
        assertNull(live.apply(new StreamEvent.ChannelRefused(CHANNEL, "not_found", "gone", Map.of())));
        assertNull(live.view());
        assertNull(new LiveAccount(TERMINAL).apply(new StreamEvent.Resync(Channel.account(UUID.randomUUID()))));
    }
}
