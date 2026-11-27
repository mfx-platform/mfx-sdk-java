package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfxhub.sdk.models.CopyClose;
import com.mfxhub.sdk.models.CopyCloseCause;
import com.mfxhub.sdk.models.CopyCloseState;
import com.mfxhub.sdk.models.CopyLeader;
import com.mfxhub.sdk.models.CopyLeaderSource;
import com.mfxhub.sdk.models.CopyLeaderState;
import com.mfxhub.sdk.models.TenantTerminal;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A word a newer API adds to a list it answers with, such as a close's cause, reads as the enum's
 * UNKNOWN_DEFAULT_OPEN_API instead of failing the call; a request never carries that member.
 */
class UnknownEnumWordTest {
    private static final String LATER = "a_word_from_a_later_version";

    /** Every generated enum: the models package's enums and the enums nested in its classes. */
    private static List<Class<?>> generatedEnums() throws Exception {
        Path root = Path.of("target", "generated-sources", "openapi", "src", "main", "java", "com", "mfxhub", "sdk", "models");
        List<Class<?>> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                Class<?> type = Class.forName("com.mfxhub.sdk.models." + name);
                if (type.isEnum()) {
                    out.add(type);
                }
                for (Class<?> nested : type.getDeclaredClasses()) {
                    if (nested.isEnum()) {
                        out.add(nested);
                    }
                }
            }
        }
        return out;
    }

    /** The enum's one constant that stands for a word it does not know. */
    private static Object unknownOf(Class<?> type) {
        List<Object> unknown = Stream.<Object>of(type.getEnumConstants()).filter(MfxJson::isUnknown).toList();
        assertEquals(1, unknown.size(), type.getName());
        return unknown.get(0);
    }

    /**
     * Given every enum generated from the contract, nullable ones among them / When a word none of them lists is
     * read, through its fromValue and through the SDK's mapper, and each of its own words / Then each has
     * UNKNOWN_DEFAULT_OPEN_API (an enum of numbers its NUMBER_ form) and the unknown word or number reads as it,
     * never as null; every word the enum lists still reads as its own constant.
     */
    @Test
    void everyGeneratedEnumReadsAWordItDoesNotKnowAsUnknown() throws Exception {
        List<Class<?>> enums = generatedEnums();
        assertTrue(enums.size() > 100, enums.size() + " enums");
        ObjectMapper mapper = MfxJson.mapper();
        for (Class<?> type : enums) {
            Object unknown = unknownOf(type);
            Method fromValue = type.getMethod("fromValue", type.getMethod("getValue").getReturnType());
            if (fromValue.getParameterTypes()[0] == String.class) {
                assertSame(unknown, fromValue.invoke(null, LATER), type.getName());
                assertSame(unknown, mapper.readValue("\"" + LATER + "\"", type), type.getName());
            } else {
                assertSame(unknown, fromValue.invoke(null, 987654), type.getName());
                assertSame(unknown, mapper.readValue("987654", type), type.getName());
            }
            for (Object constant : type.getEnumConstants()) {
                if (!MfxJson.isUnknown(constant)) {
                    Object word = type.getMethod("getValue").invoke(constant);
                    assertSame(constant, fromValue.invoke(null, word), type.getName() + " " + word);
                }
            }
        }
    }

    /**
     * Given a requested close whose cause is a word the SDK does not know, and a terminal in a state it does not
     * know / When each is read with the SDK's mapper / Then the cause and the state are UNKNOWN_DEFAULT_OPEN_API,
     * the known fields read as before, and nothing fails.
     */
    @Test
    void aModelWithAnUnknownWordReads() throws Exception {
        ObjectMapper mapper = MfxJson.mapper();
        CopyClose close = mapper.readValue("{\"id\": \"06abfa00-0000-7000-8000-0000000000a1\", \"cause\": \"" + LATER
                + "\", \"state\": \"pending\"}", CopyClose.class);
        assertEquals(CopyCloseCause.UNKNOWN_DEFAULT_OPEN_API, close.getCause());
        assertEquals(CopyCloseState.PENDING, close.getState());
        TenantTerminal terminal = mapper.readValue("{\"state\": \"HIBERNATING\"}", TenantTerminal.class);
        assertEquals("UNKNOWN_DEFAULT_OPEN_API", terminal.getState().name());
        assertFalse(Terminals.isUsable(terminal));
    }

    /**
     * Given the platform answers a page of leaders, one with a source and a state the SDK does not know / When
     * the caller lists them, and then sends UNKNOWN_DEFAULT_OPEN_API as a filter, as a body field and in a
     * setting / Then the page is returned whole with the unknown words read as UNKNOWN_DEFAULT_OPEN_API; each
     * request is refused before anything is sent, saying the member is never sent.
     */
    @Test
    void aPageWithUnknownWordsReadsAndTheUnknownMemberIsNeverSent() {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(200, "{\"items\": [{\"id\": \"06abfa00-0000-7000-8000-0000000000c1\","
                + " \"source\": \"account\", \"state\": \"active\"}, {\"id\": \"06abfa00-0000-7000-8000-0000000000c2\", \"source\": \""
                + LATER + "\", \"state\": \"" + LATER + "\"}], \"next_cursor\": null}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            Copy copy = client.copy();
            List<CopyLeader> leaders = copy.leaders(null, ListCopyLeaders.all()).join().items();
            assertEquals(List.of(CopyLeaderSource.ACCOUNT, CopyLeaderSource.UNKNOWN_DEFAULT_OPEN_API),
                    leaders.stream().map(CopyLeader::getSource).toList());
            assertEquals(List.of(CopyLeaderState.ACTIVE, CopyLeaderState.UNKNOWN_DEFAULT_OPEN_API),
                    leaders.stream().map(CopyLeader::getState).toList());
            assertEquals(1, transport.requests.size());

            IllegalArgumentException filter = assertThrows(IllegalArgumentException.class, () -> copy.leaders(null,
                    ListCopyLeaders.builder().source(CopyLeaderSource.UNKNOWN_DEFAULT_OPEN_API).build()).join());
            assertTrue(filter.getMessage().contains("never sent"), filter.getMessage());
            IllegalArgumentException body = assertThrows(IllegalArgumentException.class,
                    () -> copy.createLeader(CopyLeaderSource.UNKNOWN_DEFAULT_OPEN_API, 30, "k").join());
            assertTrue(body.getMessage().contains("CopyLeaderSource.UNKNOWN_DEFAULT_OPEN_API"), body.getMessage());
            assertThrows(IllegalArgumentException.class, () -> copy.createLeader(CopyLeaderSource.EXTERNAL, 30, "k",
                    CreateLeader.builder().filters(Map.of("side", LATER)).build()).join());
            assertThrows(IllegalArgumentException.class,
                    () -> MfxRequest.write("POST", "/x", List.of(), Map.of("cause", CopyCloseCause.UNKNOWN_DEFAULT_OPEN_API), "k"));
            assertEquals(1, transport.requests.size());
        }
    }
}
