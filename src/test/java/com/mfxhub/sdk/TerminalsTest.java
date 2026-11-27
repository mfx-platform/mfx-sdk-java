package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mfxhub.sdk.models.TenantTerminal;
import com.mfxhub.sdk.models.TenantTerminal.StateEnum;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Core's one domain call: a terminal by id, one page, and every terminal by cursor.
 */
class TerminalsTest {
    private static final String ONE = "0191f0c2-aaaa-7bbb-8ccc-000000000001";
    private static final String TWO = "0191f0c2-aaaa-7bbb-8ccc-000000000002";

    private static String terminal(String id, String state) {
        return "{\"id\": \"" + id + "\", \"farm\": {\"id\": \"" + id + "\"}, \"broker\": {\"id\": \"" + id + "\"},"
                + " \"server\": {\"id\": \"" + id + "\"}, \"streaming\": false, \"execution\": true, \"state\": \""
                + state + "\", \"instruments\": [], \"created_at\": \"2026-10-23T09:00:00Z\"}";
    }

    private static String pageOf(@Nullable String cursor, String... terminals) {
        return "{\"items\": [" + String.join(",", terminals) + "], \"next_cursor\": "
                + (cursor == null ? "null" : "\"" + cursor + "\"") + ", \"returned\": " + terminals.length + "}";
    }

    private static MfxClient client(Fakes.Recording transport) {
        return MfxClient.builder(Fakes.BASE, "mfxk_1", Fakes.key())
                .transport(transport)
                .pause(wait -> CompletableFuture.completedFuture(null))
                .build();
    }

    private static Fakes.Answer ok(String body) {
        return Fakes.answer(200, body);
    }

    /**
     * Given a terminal's id / When it is read / Then one GET of /terminals/{id} is made and the terminal
     * comes back as the generated model, its state the enum's; an unknown id is a NotFoundException.
     */
    @Test
    void aTerminalIsReadByItsId() {
        Fakes.Recording transport = new Fakes.Recording(ok(terminal(ONE, "QUEUED")),
                Fakes.answer(404, "{\"error\":{\"code\":\"not_found\",\"message\":\"no such terminal\"}}"));
        try (MfxClient client = client(transport)) {
            TenantTerminal terminal = client.terminals().get(UUID.fromString(ONE)).join();

            assertEquals("/v1/terminals/" + ONE, transport.requests.get(0).pathWithQuery());
            assertEquals(UUID.fromString(ONE), terminal.getId());
            assertEquals(StateEnum.QUEUED, terminal.getState());
            assertInstanceOf(NotFoundException.class, Fakes.failure(client.terminals().get(UUID.fromString(TWO))));
        }
    }

    /**
     * Given a cursor, a limit, labels and states / When one page is read / Then one request carries each
     * as the contract reads it (each label as key:value, the states as one comma-separated value of the
     * contract's words), and the page has its items, its cursor and its count, with no total; an empty
     * cursor on the last page reads as none.
     */
    @Test
    void onePageIsOneRequestWithItsFiltersAsTheContractReadsThem() {
        Fakes.Recording transport = new Fakes.Recording(ok(pageOf("c-3", terminal(ONE, "ACTIVE"), terminal(TWO, "ACTIVE"))),
                ok(pageOf("")));
        try (MfxClient client = client(transport)) {
            Page<TenantTerminal> page = client.terminals().page("c-2", ListTerminals.builder()
                    .limit(2).label("desk", "fx").state(StateEnum.ACTIVE, StateEnum.LOGGING_IN).build()).join();
            Page<TenantTerminal> last = client.terminals().page().join();

            assertEquals("/v1/terminals?cursor=c-2&limit=2&label=desk%3Afx&state=ACTIVE%2CLOGGING_IN",
                    transport.requests.get(0).pathWithQuery());
            assertEquals(2, page.size());
            assertEquals(2, page.returned());
            assertEquals("c-3", page.nextCursor());
            assertEquals(List.of(UUID.fromString(ONE), UUID.fromString(TWO)), page.items().stream().map(TenantTerminal::getId).toList());
            assertEquals("/v1/terminals", transport.requests.get(1).pathWithQuery());
            assertNull(last.nextCursor());
            assertTrue(last.items().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> page.items().clear());
        }
    }

    /**
     * Given three pages joined by cursors / When every terminal is listed / Then the list follows
     * next_cursor until it is null, one request a page, asking each page for the same size and filters,
     * and yields the terminals in order; a failed page is thrown as its typed error.
     */
    @Test
    void everyTerminalIsListedByFollowingTheCursor() {
        Fakes.Recording transport = new Fakes.Recording(ok(pageOf("c-2", terminal(ONE, "ACTIVE"))), ok(pageOf("c-3")),
                ok(pageOf(null, terminal(TWO, "ACTIVE"))),
                Fakes.answer(503, "{\"error\":{\"code\":\"dependency_unavailable\",\"message\":\"later\"}}"));
        try (MfxClient client = client(transport)) {
            List<UUID> ids = new ArrayList<>();
            for (TenantTerminal terminal : client.terminals().list(ListTerminals.builder().limit(1).state(StateEnum.ACTIVE).build())) {
                ids.add(terminal.getId());
            }

            assertEquals(List.of(UUID.fromString(ONE), UUID.fromString(TWO)), ids);
            assertEquals(Arrays.asList("/v1/terminals?limit=1&state=ACTIVE", "/v1/terminals?cursor=c-2&limit=1&state=ACTIVE",
                    "/v1/terminals?cursor=c-3&limit=1&state=ACTIVE"),
                    transport.requests.stream().map(Fakes.Sent::pathWithQuery).toList());
            assertThrows(DependencyUnavailableException.class, () -> client.terminals().list().iterator().hasNext());
        }
    }
}
