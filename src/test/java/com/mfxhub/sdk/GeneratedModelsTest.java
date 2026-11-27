package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.mfxhub.sdk.models.CopyBounds;
import com.mfxhub.sdk.models.TenantTerminal;
import com.mfxhub.sdk.models.TenantTerminalPage;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The models are generated from the public contract at build, in Java's own names. */
class GeneratedModelsTest {

    private static final String TERMINAL = """
            {"id": "0191f0c2-aaaa-7bbb-8ccc-000000000001",
             "farm": {"id": "1c2d3e4f-5a6b-7c8d-9e0f-102030405060", "label": "test-farm"},
             "broker": {"id": "2c2d3e4f-5a6b-7c8d-9e0f-102030405060", "label": "Test Broker"},
             "server": {"id": "3c2d3e4f-5a6b-7c8d-9e0f-102030405060", "label": "TestBroker-Demo"},
             "server_kind": "demo", "same_login_terminals": 2, "streaming": false, "execution": true,
             "state": "LOGGING_IN", "instruments": ["EURUSD"], "created_at": "2026-10-23T09:00:00Z",
             "a_field_from_later": 7}
            """;

    /**
     * Given a terminal as the API writes it, with a field this version does not know / When it is
     * read and written again / Then its fields have Java's names in code (getSameLoginTerminals,
     * StateEnum.LOGGING_IN) and the contract's names on the wire (same_login_terminals, LOGGING_IN),
     * its time is an OffsetDateTime, and the unknown field is read past rather than refused.
     */
    @Test
    void aTerminalReadsWithJavaNamesAndWritesTheContracts() throws Exception {
        TenantTerminal terminal = MfxJson.MAPPER.readValue(TERMINAL, TenantTerminal.class);

        assertEquals(TenantTerminal.StateEnum.LOGGING_IN, terminal.getState());
        assertEquals(TenantTerminal.ServerKindEnum.DEMO, terminal.getServerKind());
        assertEquals(2, terminal.getSameLoginTerminals());
        assertEquals("test-farm", terminal.getFarm().getLabel());
        assertEquals(OffsetDateTime.of(2026, 10, 23, 9, 0, 0, 0, ZoneOffset.UTC), terminal.getCreatedAt());

        JsonNode written = MfxJson.MAPPER.valueToTree(terminal);
        assertEquals("LOGGING_IN", written.get("state").asText());
        assertEquals("demo", written.get("server_kind").asText());
        assertEquals(2, written.get("same_login_terminals").asInt());
        assertEquals("2026-10-23T09:00:00Z", written.get("created_at").asText());
        List<String> names = new ArrayList<>();
        written.fieldNames().forEachRemaining(names::add);
        names.forEach(name -> assertEquals(name.toLowerCase(java.util.Locale.ROOT), name, "a wire name"));
    }

    /**
     * Every model is a component of the contract, under the contract's own name: none is named after
     * an operation's answer (CreateTerminal202Response), as the generator names a dereferenced copy,
     * and a nullable reference such as {@code oneOf: [CopyDecimal, null]} is the referenced type, not
     * an empty class that could not hold it (tools/GenerateModels.java).
     */
    @Test
    void everyModelIsAComponentUnderItsOwnName() throws Exception {
        Path models = Path.of("target", "generated-sources", "openapi", "src", "main", "java", "com", "mfxhub", "sdk", "models");
        List<String> named;
        try (Stream<Path> files = Files.list(models)) {
            named = files.map(file -> file.getFileName().toString()).filter(name -> name.matches(".*[0-9]{3}Response.*")).toList();
        }
        assertEquals(List.of(), named);

        ParameterizedType items = (ParameterizedType) TenantTerminalPage.class.getDeclaredField("items").getGenericType();
        assertEquals(TenantTerminal.class, items.getActualTypeArguments()[0]);
        assertEquals(String.class, CopyBounds.class.getDeclaredField("maxVolume").getType());
    }
}
