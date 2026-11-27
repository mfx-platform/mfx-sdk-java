package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mfxhub.sdk.models.BrokerCatalogue;
import com.mfxhub.sdk.models.MaintenanceWindowChanged;
import com.mfxhub.sdk.models.Symbol;
import com.mfxhub.sdk.models.SymbolQuote;
import com.mfxhub.sdk.models.TenantBroker;
import com.mfxhub.sdk.models.TenantFarm;
import com.mfxhub.sdk.models.TenantMaintenance;
import com.mfxhub.sdk.models.TenantMaintenanceTerminal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The catalogue, the farms, the usage, the tenant's contacts and maintenance. */
class CatalogReadsTest {
    private static final UUID BROKER = TerminalWritesTest.BROKER;
    private static final UUID SERVER = TerminalWritesTest.SERVER;
    private static final UUID WINDOW = UUID.fromString("0191f0c2-aaaa-7bbb-8ccc-0000000000ee");

    private static Fakes.Answer ok(String body) {
        return Fakes.answer(200, body);
    }

    /**
     * Given a symbol whose broker name needs escaping, a server and an account type / When its spec, its
     * quote and the symbol list are read / Then the name is one percent-encoded path segment, the server
     * and account type go as the query only when named, the quote's prices are the exact text the API
     * wrote and its age is said, and the symbol page says where its rows came from.
     */
    @Test
    void aSymbolIsReadByTheBrokersOwnNameOnTheServerNamedAndItsQuoteIsExact() {
        Fakes.Recording transport = new Fakes.Recording(ok("{\"symbol\": \"US30#\", \"pip_value_per_lot\": \"1.00\"}"),
                ok("{\"symbol\": \"US30#\", \"bid\": \"38123.10\", \"ask\": \"38124.30\", \"age_s\": 0.4, \"state\": \"open\"}"),
                ok("{\"items\": [{\"symbol\": \"EURUSD\"}], \"next_cursor\": null, \"returned\": 1, \"source\": \"last_known\"}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            WhichServer which = WhichServer.builder().serverId(SERVER).accountType("ecn pro").build();
            Symbol symbol = client.catalog().symbol(BROKER, "US30#", which).join();
            SymbolQuote quote = client.catalog().quote(BROKER, "US30#").join();
            Page<Symbol> page = client.catalog().symbols(BROKER, which, "c-1", 10).join();

            assertEquals("/v1/brokers/" + BROKER + "/symbols/US30%23?server_id=" + SERVER + "&account_type=ecn%20pro",
                    transport.requests.get(0).pathWithQuery());
            assertEquals("1.00", symbol.getPipValuePerLot());
            assertEquals("/v1/brokers/" + BROKER + "/symbols/US30%23/quote", transport.requests.get(1).pathWithQuery());
            assertEquals("38123.10", quote.getBid());
            assertEquals(0.4, quote.getAgeS());
            assertEquals("/v1/brokers/" + BROKER + "/symbols?server_id=" + SERVER + "&account_type=ecn%20pro&cursor=c-1&limit=10",
                    transport.requests.get(2).pathWithQuery());
            assertEquals("last_known", page.source());
            assertEquals(1, page.returned());
        }
    }

    /**
     * Given brokers on two pages, a broker's catalogues and its servers / When every broker is listed and
     * the rest are read / Then the list follows the cursor to its end, the catalogues are a plain list of
     * rows, and an empty page reads as the last.
     */
    @Test
    void everyBrokerIsListedByCursorAndTheCataloguesArePlainRows() {
        Fakes.Recording transport = new Fakes.Recording(ok("{\"items\": [{\"id\": \"" + BROKER + "\"}], \"next_cursor\": \"b-2\"}"),
                ok("{\"items\": [{\"id\": \"" + SERVER + "\"}], \"next_cursor\": \"\"}"),
                ok("{\"items\": [{\"server_id\": \"" + SERVER + "\", \"account_type\": \"ecn\", \"catalogue_hash\": \"ab12\", \"symbols\": 41}]}"),
                ok("{\"items\": [], \"next_cursor\": null}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            List<UUID> ids = new ArrayList<>();
            for (TenantBroker broker : client.catalog().allBrokers()) {
                ids.add(broker.getId());
            }
            List<BrokerCatalogue> catalogues = client.catalog().catalogues(BROKER).join();
            Page<?> servers = client.catalog().servers(BROKER).join();

            assertEquals(List.of(BROKER, SERVER), ids);
            assertEquals("/v1/brokers?cursor=b-2", transport.requests.get(1).pathWithQuery());
            assertEquals("ab12", catalogues.get(0).getCatalogueHash());
            assertEquals("/v1/brokers/" + BROKER + "/servers", transport.requests.get(3).pathWithQuery());
            assertNull(servers.nextCursor());
        }
    }

    /**
     * Given windows filtered by two states, a window's terminals on two pages, and the farms, usage and
     * contacts / When each is read / Then the states go as one comma-separated value of the contract's
     * words, the window's terminals follow the cursor, and each read is one GET of its own path.
     */
    @Test
    void theWindowsTheirTerminalsTheFarmsUsageAndContactsAreRead() {
        Fakes.Recording transport = new Fakes.Recording(ok("{\"items\": [{\"id\": \"" + WINDOW + "\", \"state\": \"in_progress\"}]}"),
                ok("{\"items\": [{\"terminal_id\": \"" + BROKER + "\"}], \"next_cursor\": \"t-2\"}"),
                ok("{\"items\": [{\"terminal_id\": \"" + SERVER + "\"}]}"),
                ok("{\"items\": [{\"id\": \"" + SERVER + "\"}]}"), ok("{\"id\": \"" + SERVER + "\"}"), ok("{}"), ok("{}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            Page<TenantMaintenance> windows = client.maintenance().page(null, ListMaintenance.builder()
                    .state(TenantMaintenance.StateEnum.SCHEDULED, TenantMaintenance.StateEnum.IN_PROGRESS).limit(5).build()).join();
            List<UUID> inWindow = new ArrayList<>();
            for (TenantMaintenanceTerminal one : client.maintenance().terminals(WINDOW, 1)) {
                inWindow.add(one.getTerminalId());
            }
            List<TenantFarm> farms = new ArrayList<>();
            client.farms().list().forEach(farms::add);
            client.farms().get(SERVER).join();
            client.usage().get().join();
            client.tenant().contacts().join();

            assertEquals("/v1/maintenance?limit=5&state=scheduled%2Cin_progress", transport.requests.get(0).pathWithQuery());
            assertEquals(TenantMaintenance.StateEnum.IN_PROGRESS, windows.get(0).getState());
            assertEquals(List.of(BROKER, SERVER), inWindow);
            assertEquals("/v1/maintenance/" + WINDOW + "/terminals?cursor=t-2&limit=1", transport.requests.get(2).pathWithQuery());
            assertEquals(1, farms.size());
            assertEquals(List.of("/v1/farms", "/v1/farms/" + SERVER, "/v1/usage", "/v1/tenant/contacts"),
                    transport.requests.subList(3, 7).stream().map(Fakes.Sent::pathWithQuery).toList());
        }
    }

    /**
     * Given a maintenance.window_changed message on events and any other message / When each is read as a
     * window change / Then the change is typed with its window, its state and its count written as text,
     * and every other message is null.
     */
    @Test
    void aWindowChangeIsATypedEventAndEveryOtherIsNull() {
        Map<String, JsonNode> data = Map.of("window_id", TextNode.valueOf(WINDOW.toString()), "change", TextNode.valueOf("started"),
                "state", TextNode.valueOf("in_progress"), "terminals", TextNode.valueOf("3"), "ends_at", TextNode.valueOf("2026-11-17T12:00:00Z"));
        MaintenanceWindowChanged change = Maintenance.windowEvent(new StreamEvent.Message("events", 4L, Maintenance.WINDOW_CHANGED, data, null));

        assertTrue(change != null);
        assertEquals(MaintenanceWindowChanged.ChangeEnum.STARTED, change.getChange());
        assertEquals(WINDOW, change.getWindowId());
        assertEquals("3", change.getTerminals());
        assertNull(Maintenance.windowEvent(new StreamEvent.Message("events", 5L, "terminal.state_changed", data, null)));
        assertNull(Maintenance.windowEvent(new StreamEvent.Resync("events")));
    }
}
