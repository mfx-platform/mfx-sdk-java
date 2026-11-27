package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.mfxhub.sdk.sdkref.SdkRef;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The SDK reference, {@code docs/sdkref.json} in the shared format {@code mfx-sdkref/1}: generated from this
 * SDK's public API by the javadoc doclet in {@code tools/sdkref}, committed, and checked here against a fresh
 * generation, the format's rules and the contract. CI also runs the shared suite's checker on it.
 *
 * <p>To write it again after a change to the public API or its doc comments:
 * {@code ./mvnw test -Dtest=SdkRefTest -Dsdkref.write=true}.
 */
class SdkRefTest {
    static final Path COMMITTED = Path.of("docs/sdkref.json");
    static final List<String> PAGE_ORDER = List.of("client", "terminals", "execution", "trading-accounts", "accounts", "market-data",
            "stream", "risk", "copy", "billing", "tenant", "errors");
    /** What must never reach a published reference (the suite's checker refuses the same). */
    static final List<Pattern> FORBIDDEN = List.of(Pattern.compile("AB#", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bDEC-", Pattern.CASE_INSENSITIVE), Pattern.compile("\\bMT5\\b"),
            Pattern.compile("\\bcells?\\b", Pattern.CASE_INSENSITIVE), Pattern.compile("-" + "internal\\b", Pattern.CASE_INSENSITIVE));

    /** An error type's simple name to the API code the SDK maps it from: the SDK's own table, not a copy. */
    static Map<String, String> codes() {
        Map<String, String> out = new TreeMap<>();
        ApiErrors.BY_CODE.forEach((code, kind) -> out.put(kind.type().getSimpleName(), code));
        return out;
    }

    static Map<String, Object> header() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", SdkRef.FORMAT);
        out.put("language", "java");
        out.put("package", "com.mfxhub:mfx-sdk");
        out.put("version", SdkVersion.VALUE);
        out.put("install", "implementation(\"com.mfxhub:mfx-sdk:" + SdkVersion.VALUE + "\")");
        out.put("links", Map.of("source", "https://github.com/mfx-platform/mfx-sdk-java"));
        return out;
    }

    static JsonNode operationsFile() throws Exception {
        return MfxJson.MAPPER.readTree(Files.readString(Path.of("tools/sdkref/operations.json")));
    }

    static Map<String, String> section(JsonNode file, String name) {
        Map<String, String> out = new TreeMap<>();
        file.path(name).fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
        return out;
    }

    static final Path CONTRACT = Path.of("spec/openapi.yaml");

    static Map<String, Object> pages() throws Exception {
        return MfxJson.MAPPER.readValue(Files.readString(Path.of("tools/sdkref/pages.json")),
                new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    /** Every type pages.json lists, top level, in page order. */
    @SuppressWarnings("unchecked")
    static List<String> listed() throws Exception {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> page : (List<Map<String, Object>>) pages().get("pages")) {
            out.addAll((List<String>) page.get("types"));
        }
        return out;
    }

    /** The contract schema each documented type represents, derived from the contract and the generated models. */
    static Map<String, String> schemas() throws Exception {
        return SdkRefSchemas.derive(CONTRACT, SdkRefSchemas.documented(listed()), section(schemasFile(), "events"));
    }

    static JsonNode schemasFile() throws Exception {
        return MfxJson.MAPPER.readTree(Files.readString(Path.of("tools/sdkref/schemas.json")));
    }

    static String generate() throws Exception {
        JsonNode operations = operationsFile();
        Map<String, String> all = new TreeMap<>(section(operations, "called"));
        all.putAll(section(operations, "by_hand"));
        return SdkRef.generate(new SdkRef.Config(Path.of("src/main/java"), "com.mfxhub.sdk", System.getProperty("java.class.path"),
                pages(), all, codes(), schemas(), header()));
    }

    static JsonNode committed() throws Exception {
        return MfxJson.MAPPER.readTree(Files.readString(COMMITTED, StandardCharsets.UTF_8));
    }

    /** Every string in the file with the key it is under ("" for a list item). */
    static void strings(JsonNode node, String key, List<Map.Entry<String, String>> into) {
        if (node.isTextual()) {
            into.add(Map.entry(key, node.asText()));
        } else if (node.isObject()) {
            node.fields().forEachRemaining(e -> strings(e.getValue(), e.getKey(), into));
        } else if (node.isArray()) {
            node.forEach(item -> strings(item, "", into));
        }
    }

    static List<JsonNode> types(JsonNode root) {
        List<JsonNode> out = new ArrayList<>();
        root.path("pages").forEach(page -> page.path("types").forEach(out::add));
        return out;
    }

    /**
     * Given the SDK's source and the committed reference / When the doclet generates the reference twice /
     * Then both runs give the same bytes and they are exactly the committed file: UTF-8, two-space indent, LF,
     * one newline at the end. A public API or doc comment changed without regenerating fails here.
     */
    @Test
    void theCommittedReferenceIsWhatTheSourceGeneratesByteForByte() throws Exception {
        String first = generate();
        assertEquals(first, generate(), "two runs differ");
        if (Boolean.getBoolean("sdkref.write")) {
            Files.createDirectories(COMMITTED.getParent());
            Files.writeString(COMMITTED, first, StandardCharsets.UTF_8);
        }
        assertTrue(Files.exists(COMMITTED), "no docs/sdkref.json: ./mvnw test -Dtest=SdkRefTest -Dsdkref.write=true");
        assertEquals(first, Files.readString(COMMITTED, StandardCharsets.UTF_8),
                "docs/sdkref.json is not what the source generates: ./mvnw test -Dtest=SdkRefTest -Dsdkref.write=true");
        assertTrue(first.endsWith("}\n") && !first.contains("\r") && !first.contains("\t"));
    }

    /**
     * Given the committed reference / When its pages, types and members are read / Then the pages are in the
     * shared order, every anchor is unique and starts with its page (a member's with its type's), every
     * public type is listed, every error the SDK maps from a code carries that code and no other type has
     * one, every signature is one line and never elided, and no optional key is empty.
     */
    @Test
    void everyPublicTypeIsOnOnePageWithUniqueAnchorsAndTheErrorCodes() throws Exception {
        JsonNode root = committed();
        assertEquals("mfx-sdkref/1", root.path("format").asText());
        assertEquals("java", root.path("language").asText());
        assertEquals(SdkVersion.VALUE, root.path("version").asText());
        List<String> ids = new ArrayList<>();
        root.path("pages").forEach(p -> ids.add(p.path("id").asText()));
        assertEquals(PAGE_ORDER, ids, "every page, in the shared order");

        Set<String> anchors = new HashSet<>();
        Map<String, String> codes = codes();
        Set<String> names = new HashSet<>();
        for (JsonNode page : root.path("pages")) {
            String id = page.path("id").asText();
            for (JsonNode type : page.path("types")) {
                String anchor = type.path("anchor").asText();
                names.add(type.path("name").asText());
                assertTrue(anchors.add(anchor), anchor + " twice");
                assertTrue(anchor.matches(Pattern.quote(id) + "\\.[A-Za-z_][A-Za-z0-9_]*"), anchor);
                String simple = type.path("name").asText().replaceAll(".*\\.", "");
                if (type.path("kind").asText().equals("error")) {
                    assertEquals(codes.get(simple), type.path("code").isMissingNode() ? null : type.path("code").asText(), simple);
                } else {
                    assertTrue(type.path("code").isMissingNode(), simple + " is not an error");
                }
                for (JsonNode member : type.path("members")) {
                    String at = member.path("anchor").asText();
                    assertTrue(anchors.add(at), at + " twice");
                    assertTrue(at.matches(Pattern.quote(anchor) + "\\.[A-Za-z_][A-Za-z0-9_]*"), at);
                    String signature = member.path("signature").asText();
                    assertFalse(signature.isBlank() || signature.contains("\n") || signature.matches("(?s).*\\.\\.\\.\\s*([)\\]},>].*|$)"),
                            at + ": " + signature);
                    for (String optional : List.of("params", "raises", "returns", "operation", "deprecated")) {
                        JsonNode value = member.path(optional);
                        boolean empty = value.isNull() || (value.isContainerNode() && value.isEmpty())
                                || (value.isTextual() && value.asText().isEmpty());
                        assertFalse(empty, at + " has an empty " + optional);
                    }
                }
            }
        }
        for (String simple : codes.keySet()) {
            assertTrue(names.contains(simple), simple + " is not in the reference");
        }
        assertTrue(names.containsAll(List.of("MfxClient", "Execution", "PlaceOrder.Builder", "MfxStream", "Copy", "Risk")));
    }

    /**
     * Given the committed reference / When every string in it is read / Then none holds an internal word
     * (a work item or decision id, the upper-case platform name, the hosting word, an internal repository
     * name), no Markdown value has raw HTML or an angle bracket outside a code span or doc-tool markup left in,
     * and every cross-reference is exactly [Name](#anchor) naming an anchor in the file.
     */
    @Test
    void theReferenceHoldsNoInternalWordsNoHtmlAndOnlyLinksThatResolve() throws Exception {
        JsonNode root = committed();
        Set<String> anchors = new HashSet<>();
        for (JsonNode type : types(root)) {
            anchors.add(type.path("anchor").asText());
            type.path("members").forEach(m -> anchors.add(m.path("anchor").asText()));
        }
        List<Map.Entry<String, String>> all = new ArrayList<>();
        strings(root, "", all);
        List<String> problems = new ArrayList<>();
        Pattern link = Pattern.compile("\\[([^\\[\\]]*)\\]\\(([^()]*)\\)");
        for (Map.Entry<String, String> entry : all) {
            String value = entry.getValue();
            for (Pattern word : FORBIDDEN) {
                if (word.matcher(value).find()) {
                    problems.add("internal word " + word + " in: " + value);
                }
            }
            if (!List.of("doc", "intro", "deprecated").contains(entry.getKey())) {
                continue;
            }
            String prose = withoutCode(value);
            if (prose.contains("<") || prose.contains(">") || prose.contains("{@")) {
                problems.add("markup outside a code span in: " + value);
            }
            Matcher links = link.matcher(prose);
            while (links.find()) {
                String target = links.group(2);
                if (target.startsWith("#") ? !anchors.contains(target.substring(1)) : !target.startsWith("https://")) {
                    problems.add("a link to " + target + " in: " + value);
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    static String withoutCode(String markdown) {
        String text = Pattern.compile("^ {0,3}(`{3,}|~{3,}).*?^ {0,3}\\1[ \\t]*$", Pattern.MULTILINE | Pattern.DOTALL).matcher(markdown)
                .replaceAll("");
        return Pattern.compile("(`+)(?!`).*?(?<!`)\\1(?!`)", Pattern.DOTALL).matcher(text).replaceAll("code");
    }

    /**
     * Given the operation map and the contract / When every public method of every resource client is called
     * once with made-up arguments on a recording transport / Then each request's method and path name exactly
     * the operation the map's called section gives it, the by-hand entries are only methods that send nothing
     * with made-up arguments, and every operation in the map is an operationId of spec/openapi.yaml.
     */
    @Test
    void eachMethodsOperationIsTheContractOperationItCalls() throws Exception {
        List<SdkRefOperations.Route> routes = SdkRefOperations.routes(Path.of("spec/openapi.yaml"));
        JsonNode file = operationsFile();
        Map<String, String> found = SdkRefOperations.discover(routes);

        assertEquals(found, section(file, "called"));
        Map<String, String> byHand = section(file, "by_hand");
        Set<String> contract = new HashSet<>();
        routes.forEach(r -> contract.add(r.operationId()));
        for (Map.Entry<String, String> entry : byHand.entrySet()) {
            assertFalse(found.containsKey(entry.getKey()), entry.getKey() + " is called; it belongs in called");
        }
        List<String> operations = new ArrayList<>(found.values());
        operations.addAll(byHand.values());
        for (String operation : operations) {
            assertTrue(contract.contains(operation), operation + " is not a contract operationId");
        }
        JsonNode placeOrder = memberOf(committed(), "execution.Execution.placeOrder");
        assertEquals("placeOrder", placeOrder.path("operation").asText());
        assertEquals("placeTradingAccountOrder", memberOf(committed(), "trading-accounts.TradingAccounts.placeOrder").path("operation").asText());
    }

    static JsonNode memberOf(JsonNode root, String anchor) {
        for (JsonNode type : types(root)) {
            for (JsonNode member : type.path("members")) {
                if (member.path("anchor").asText().equals(anchor)) {
                    return member;
                }
            }
        }
        throw new AssertionError("no member " + anchor);
    }

    /**
     * Given a small package whose doc comments use every markup the SDK uses / When the doclet generates its
     * reference / Then a link tag is [Name](#anchor), a code tag a code span (generics inside it), a
     * paragraph tag a blank line, bold and lists Markdown, the param, return and throws tags the member's
     * params, returns and raises, a nested type's anchor Outer_Inner (and the schema it is given, its own), an
     * error its code, and a method's overloads one member: the fullest signature, with the others listed in its
     * doc.
     */
    @Test
    void docCommentsBecomeMarkdownAndOverloadsOneMember(@TempDir Path dir) throws Exception {
        Path pkg = Files.createDirectories(dir.resolve("fixture"));
        Files.writeString(pkg.resolve("Gadget.java"), """
                package fixture;

                import java.util.Map;

                /**
                 * A gadget. It {@link #spin(int) spins} and has a {@link #name()}; see {@link Gadget.Part}.
                 *
                 * <p>It makes a {@code Map<String, Integer>}, <b>exactly</b>:
                 * <ul>
                 * <li>once</li>
                 * <li>and only once</li>
                 * </ul>
                 */
                public final class Gadget {
                    /** The most turns, {@value}. */
                    public static final int MOST = 3;

                    /** A part of it. */
                    public static final class Part {
                        /** Makes one. */
                        public Part() {
                        }
                    }

                    /**
                     * Spins once.
                     *
                     * @return what it made
                     */
                    public Map<String, Integer> spin() {
                        return spin(1);
                    }

                    /**
                     * Spins.
                     *
                     * @param turns how many, at most {@link #MOST}
                     * @return what it made, a {@code Map<String, Integer>}
                     * @throws GadgetException when {@code turns > MOST}
                     */
                    public Map<String, Integer> spin(int turns) {
                        return Map.of("turns", turns);
                    }

                    /**
                     * Its name.
                     *
                     * @return the name
                     */
                    public String name() {
                        return "gadget";
                    }
                }
                """);
        Files.writeString(pkg.resolve("GadgetException.java"), """
                package fixture;

                /** It would not spin. */
                public class GadgetException extends RuntimeException {
                    private static final long serialVersionUID = 1L;

                    /**
                     * Says why.
                     *
                     * @param message why
                     */
                    public GadgetException(String message) {
                        super(message);
                    }
                }
                """);
        Map<String, Object> pages = Map.of("pages", List.of(Map.of("id", "client", "intro", "", "types", List.of("Gadget", "GadgetException"))));
        String text = SdkRef.generate(new SdkRef.Config(dir, "fixture", System.getProperty("java.class.path"), pages,
                Map.of("Gadget.spin", "spinGadget"), Map.of("GadgetException", "gadget_refused"), Map.of("Gadget.Part", "GadgetPart"), header()));
        JsonNode root = MfxJson.MAPPER.readTree(text);
        List<JsonNode> types = types(root);

        assertEquals(List.of("client.Gadget", "client.Gadget_Part", "client.GadgetException"),
                types.stream().map(t -> t.path("anchor").asText()).toList());
        JsonNode gadget = types.get(0);
        assertEquals("final class Gadget", gadget.path("signature").asText());
        assertEquals("A gadget. It [spins](#client.Gadget.spin) and has a [name](#client.Gadget.name); see [Gadget.Part](#client.Gadget_Part).\n\n"
                + "It makes a `Map<String, Integer>`, **exactly**:\n\n- once\n- and only once", gadget.path("doc").asText());
        assertEquals("Gadget.Part", types.get(1).path("name").asText());
        assertEquals("GadgetPart", types.get(1).path("schema").asText(), "a nested type carries its own schema");
        assertTrue(gadget.path("schema").isMissingNode() && types.get(2).path("schema").isMissingNode());
        assertEquals("client.Gadget_Part.Part", types.get(1).path("members").get(0).path("anchor").asText());

        JsonNode most = gadget.path("members").get(0);
        assertEquals("static final int MOST = 3", most.path("signature").asText());
        assertEquals("The most turns, `3`.", most.path("doc").asText());

        JsonNode spin = memberOf(root, "client.Gadget.spin");
        assertEquals(3, gadget.path("members").size(), "the two spins are one member");
        assertEquals("Map<String, Integer> spin(int turns)", spin.path("signature").asText());
        assertEquals("Spins.\n\nAlso callable as `Map<String, Integer> spin()`.", spin.path("doc").asText());
        assertEquals("how many, at most [MOST](#client.Gadget.MOST)", spin.path("params").get(0).path("doc").asText());
        assertEquals("int", spin.path("params").get(0).path("type").asText());
        assertEquals("Map<String, Integer>", spin.path("returns").path("type").asText());
        assertEquals("what it made, a `Map<String, Integer>`", spin.path("returns").path("doc").asText());
        assertEquals("GadgetException", spin.path("raises").get(0).path("type").asText());
        assertEquals("when `turns > MOST`", spin.path("raises").get(0).path("doc").asText());
        assertEquals("spinGadget", spin.path("operation").asText());
        assertTrue(memberOf(root, "client.Gadget.name").path("operation").isMissingNode());

        JsonNode error = types.get(2);
        assertEquals("error", error.path("kind").asText());
        assertEquals("gadget_refused", error.path("code").asText());
        assertEquals("[\"RuntimeException\"]", error.path("extends").toString());
        assertEquals("class GadgetException extends RuntimeException", error.path("signature").asText());
    }

    static Map<String, String> committedSchemas() throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode type : types(committed())) {
            if (type.has("schema")) {
                out.put(type.path("name").asText(), type.path("schema").asText());
            }
        }
        return out;
    }

    /**
     * Given the committed reference, the contract and the models generated from it / When the types that
     * represent one contract object are read / Then each carries that object's schema, verbatim as its key of
     * components.schemas even where the SDK's name differs: a stream event's type the schema x-event-payloads
     * gives its event type, a record the one generated model whose properties are exactly its components, an
     * enum the one generated enum whose values are exactly its own; and every generated model is named as its
     * key, the five cTrader ones among them.
     */
    @Test
    void aTypeThatIsOneContractObjectCarriesItsSchemaVerbatim() throws Exception {
        Map<String, String> schemas = committedSchemas();
        assertEquals("OrderPlaced", schemas.get("OrderPlaced"));
        assertEquals("MdCandleClosed", schemas.get("CandleClosed"), "the event's payload, not the SDK's name");
        assertEquals("RiskRuleEvent", schemas.get("RiskRuleEvent"), "three event types, one payload");
        assertEquals("CopyStopoutEvent", schemas.get("CopyStopoutEvent"));
        assertEquals("BookMessage", schemas.get("Book"));
        assertEquals("EaTelemetryMessage", schemas.get("EaTelemetry"));
        assertEquals("MdSubscriptionDegraded", schemas.get("SubscriptionDegraded"));
        assertEquals("CommandTickets", schemas.get("Tickets"));
        assertEquals("BillingAction", schemas.get("PaymentAction"));
        assertEquals("CopyLeaderDeal", schemas.get("CopyDeal"));
        assertEquals("CommandState", schemas.get("CommandState"), "an enum that is a contract schema");
        assertEquals(schemas(), schemas, "the committed reference carries exactly what is derived");

        Set<String> contract = SdkRefSchemas.contractSchemas(CONTRACT);
        Map<String, Class<?>> models = SdkRefSchemas.models(contract);
        for (String ctrader : List.of("CtraderGrantCreate", "CtraderGrantStarted", "CtraderGrantAccount", "CtraderGrant", "CtraderAccountsChoice")) {
            assertTrue(contract.contains(ctrader), ctrader);
            assertEquals(ctrader, models.get(ctrader).getSimpleName(), "a model is named as its schema's key");
        }
        assertEquals(Set.of("order", "deal", "position"), SdkRefSchemas.properties(models.get("CommandTickets")));
        assertTrue(models.size() > 300, "the generator made a model of each object schema");
    }

    /**
     * Given the committed reference / When the clients, builders, exceptions, interfaces, generic pages and
     * helpers are read / Then none carries a schema, and every type that does is a record or an enum, never an
     * error, a class, an interface or a function.
     */
    @Test
    void aClientAnErrorOrAHelperCarriesNoSchema() throws Exception {
        Map<String, String> schemas = committedSchemas();
        for (String none : List.of("MfxClient", "Execution", "Copy", "Risk", "PlaceOrder", "PlaceOrder.Builder", "MfxApiException",
                "ConflictException", "MfxTransportException", "Event", "StreamEvent", "SubscriptionHealth", "Page", "CopyPage", "Events",
                "MfxJson", "ExecutionResult", "VenueDeal", "CopyAs")) {
            assertFalse(schemas.containsKey(none), none + " carries " + schemas.get(none));
        }
        for (JsonNode type : types(committed())) {
            if (type.has("schema")) {
                assertTrue(List.of("record", "enum").contains(type.path("kind").asText()), type.path("anchor").asText());
            }
        }
    }

    /**
     * Given the committed reference and spec/openapi.yaml / When every type's schema is read / Then each is a
     * key of components.schemas, case kept, of the pattern the format allows, and no member carries one.
     */
    @Test
    void everySchemaTheReferenceNamesIsInTheContract() throws Exception {
        Set<String> contract = SdkRefSchemas.contractSchemas(CONTRACT);
        assertTrue(contract.size() > 300 && contract.contains("Position") && contract.contains("CopyCopied"));
        Map<String, String> schemas = committedSchemas();
        assertFalse(schemas.isEmpty());
        for (Map.Entry<String, String> entry : schemas.entrySet()) {
            assertTrue(entry.getValue().matches("[A-Za-z][A-Za-z0-9]*"), entry.toString());
            assertTrue(contract.contains(entry.getValue()), entry.getKey() + ": " + entry.getValue() + " is not a schema of the contract");
        }
        for (JsonNode type : types(committed())) {
            type.path("members").forEach(m -> assertFalse(m.has("schema"), m.path("anchor").asText()));
        }
    }

    /**
     * Given tools/sdkref/schemas.json, the SDK and the contract / When each type it gives an event type is read
     * / Then the event type is the value of a public constant of the SDK, the contract's x-event-payloads has
     * it, and each of the type's components but the stream's channel and seq is a property of that payload.
     */
    @Test
    void aTypeGivenAnEventTypeReadsThatPayload() throws Exception {
        Map<String, String> events = section(schemasFile(), "events");
        assertFalse(events.isEmpty());
        List<Class<?>> documented = SdkRefSchemas.documented(listed());
        Set<String> constants = new HashSet<>();
        for (Class<?> type : documented) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && field.getType() == String.class) {
                    constants.add((String) field.get(null));
                }
            }
        }
        Map<String, String> payloads = SdkRefSchemas.eventPayloads(CONTRACT);
        Map<String, Class<?>> models = SdkRefSchemas.models(SdkRefSchemas.contractSchemas(CONTRACT));
        for (Map.Entry<String, String> entry : events.entrySet()) {
            Class<?> type = documented.stream().filter(t -> SdkRefSchemas.name(t).equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> new AssertionError(entry.getKey() + " is not a documented type"));
            assertTrue(constants.contains(entry.getValue()), entry.getValue() + " is no public constant of the SDK");
            String schema = payloads.get(entry.getValue());
            assertTrue(schema != null, entry.getValue() + " has no payload in the contract");
            Set<String> properties = SdkRefSchemas.properties(models.get(schema));
            for (RecordComponent component : type.getRecordComponents()) {
                String wire = SdkRefSchemas.snake(component.getName());
                assertTrue(List.of("channel", "seq").contains(wire) || properties.contains(wire), entry.getKey() + "." + wire + " is not in " + schema);
            }
        }
    }
}
