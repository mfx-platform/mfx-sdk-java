package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mfxhub.sdk.models.AccountMdSubscription;
import com.mfxhub.sdk.models.Candle;
import com.mfxhub.sdk.models.CandleList;
import com.mfxhub.sdk.models.OrderPlace;
import com.mfxhub.sdk.models.PlanChangeSummary;
import com.mfxhub.sdk.models.RiskChallengeAccount;
import com.mfxhub.sdk.models.TenantServiceAgreement;
import com.mfxhub.sdk.models.TenantTerminal;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The Java runner of the shared SDK test suite. It holds no rule: it asks the fake hub for each case,
 * turns the case's neutral operation into the Java call, and checks what came back against the case and
 * against the hub's verdict on what was sent.
 */
@Tag("conformance")
class SuiteTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNode VOCABULARY = read(Hub.SUITE.resolve("vocabulary.json"));
    private static final byte[] SEED = HexFormat.of().parseHex(VOCABULARY.path("key").path("seed_hex").asText());
    /** Cases this SDK does not pass yet, each with the step or phase that will claim it. */
    private static final Map<String, String> NOT_YET = notYet();

    private static @Nullable Hub hub;

    @BeforeAll
    static void start() throws Exception {
        hub = new Hub();
    }

    @AfterAll
    static void stop() {
        if (hub != null) {
            hub.close();
        }
    }

    private static JsonNode read(Path path) {
        try {
            return JSON.readTree(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
    }

    private static Map<String, String> notYet() {
        Map<String, String> listed = new TreeMap<>();
        read(Path.of("conformance", "not-yet.json")).fields().forEachRemaining(e -> listed.put(e.getKey(), e.getValue().asText()));
        return listed;
    }

    /** Every case id of the vendored suite, in file order. */
    static Stream<String> caseIds() throws IOException {
        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.list(Hub.SUITE.resolve("cases"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                read(file).forEach(c -> ids.add(c.path("id").asText()));
            }
        }
        return ids.stream();
    }

    /** The not-yet list names only cases the suite has, so it cannot go stale. */
    @Test
    void theNotYetListNamesOnlyCasesTheSuiteHas() throws IOException {
        List<String> ids = caseIds().toList();
        assertEquals(List.of(), NOT_YET.keySet().stream().filter(id -> !ids.contains(id)).toList());
    }

    /**
     * Given a case of the shared SDK test suite, played back by the suite's fake MFX Hub / When this SDK
     * makes the case's call, signed with the suite's test key / Then it returns, throws or refuses exactly
     * as the case observes, and the hub's verdict is that every expected request arrived in order, signed
     * and shaped as the case says; a case listed as not yet passed must fail, so the list cannot hide a
     * case that passes.
     */
    @ParameterizedTest
    @MethodSource("caseIds")
    void theJavaSdkPassesTheCase(String id) throws Exception {
        List<String> problems = run(id);

        String why = NOT_YET.get(id);
        if (why != null) {
            assertTrue(!problems.isEmpty(), id + " passes: take it off not-yet.json (" + why + ")");
        } else {
            assertEquals(List.of(), problems);
        }
    }

    private static List<String> run(String id) throws Exception {
        Hub served = hub;
        if (served == null) {
            throw new IllegalStateException("the hub is not running");
        }
        List<String> problems = new ArrayList<>();
        JsonNode c = served.load(id);
        JsonNode call = c.get("call");
        JsonNode observe = c.path("observe");
        try (MfxClient client = MfxClient.builder(served.baseUrl(), VOCABULARY.path("key").path("id").asText(), SigningKey.fromSeed(SEED))
                .pause(wait -> CompletableFuture.completedFuture(null))
                .build()) {
            if (call == null || call.isNull()) {
                throw new NotYet("a case without a call");
            }
            String op = call.path("op").asText();
            JsonNode args = call.path("args");
            if (observe.has("raises")) {
                Class<?> kind = kind(observe.path("raises").asText());
                Throwable thrown = null;
                try {
                    call(client, op, args);
                } catch (NotYet missing) {
                    throw missing;
                } catch (RuntimeException failure) {
                    thrown = failure;
                }
                if (thrown == null) {
                    problems.add("returned, where " + kind.getSimpleName() + " was expected");
                } else if (!kind.isInstance(thrown)) {
                    problems.add("threw " + thrown.getClass().getSimpleName() + " (" + thrown.getMessage() + "), where "
                            + kind.getSimpleName() + " was expected");
                } else {
                    Iterator<Map.Entry<String, JsonNode>> fields = observe.path("fields").fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        Method accessor = thrown.getClass().getMethod(camel(field.getKey()));
                        JsonNode value = JSON.valueToTree(accessor.invoke(thrown));
                        if (!same(value, field.getValue())) {
                            problems.add(field.getKey() + ": got " + value + ", want " + field.getValue());
                        }
                    }
                }
            } else if (observe.has("refuses")) {
                Throwable thrown = null;
                try {
                    call(client, op, args);
                } catch (NotYet missing) {
                    throw missing;
                } catch (IllegalArgumentException | NullPointerException | ClassCastException refused) {
                    thrown = refused;
                } catch (RuntimeException other) {
                    problems.add("threw " + other.getClass().getSimpleName() + " (" + other.getMessage() + "), where "
                            + observe.path("refuses").asText() + " was expected");
                    thrown = other;
                }
                if (thrown == null) {
                    problems.add("returned, where it should have refused (" + observe.path("refuses").asText() + ")");
                }
            } else if (observe.has("returns")) {
                Map<String, JsonNode> got = call(client, op, args);
                Iterator<Map.Entry<String, JsonNode>> wanted = observe.path("returns").fields();
                while (wanted.hasNext()) {
                    Map.Entry<String, JsonNode> want = wanted.next();
                    JsonNode value = got.get(want.getKey());
                    if (!same(value, want.getValue())) {
                        problems.add(want.getKey() + ": got " + value + ", want " + want.getValue());
                    }
                }
            } else {
                problems.add("the runner cannot check '" + observe + "' yet");
            }
        } catch (NotYet missing) {
            problems.add("the SDK has no " + missing.getMessage() + " yet");
        }
        problems.addAll(served.problems());
        return problems;
    }

    /** The suite's neutral error kind as this SDK's type: {@code FooError} is {@code FooException}. */
    private static Class<?> kind(String neutral) throws ClassNotFoundException {
        return switch (neutral) {
            case "ApiError" -> MfxApiException.class;
            case "TransportError" -> MfxTransportException.class;
            case "Timeout" -> MfxTimeoutException.class;
            default -> Class.forName("com.mfxhub.sdk." + neutral.substring(0, neutral.length() - "Error".length()) + "Exception");
        };
    }

    private static String camel(String snake) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < snake.length(); i++) {
            char ch = snake.charAt(i);
            if (ch == '_') {
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        return out.toString();
    }

    /** What a future gave, or the SDK's own failure, unwrapped from join()'s CompletionException. */
    private static <T> T joined(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException failed) {
            if (failed.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw failed;
        }
    }

    /** Makes the case's call through this SDK and describes what came back in the suite's names. */
    private static Map<String, JsonNode> call(MfxClient client, String op, JsonNode args) throws InterruptedException {
        JsonNodeFactory nodes = JsonNodeFactory.instance;
        Map<String, JsonNode> got = new TreeMap<>();
        switch (op) {
            case "signing.sign" -> {
                byte[] message = Signing.message(args.path("timestamp").asText(), args.path("nonce").asText(),
                        args.path("method").asText(), args.path("path_with_query").asText(),
                        args.path("body").asText().getBytes(StandardCharsets.UTF_8));
                got.put("signature", nodes.textNode(SigningKey.fromSeed(SEED).sign(message)));
            }
            case "terminals.get" -> {
                // Read back in the API's own JSON, so the state is the contract's word (ACTIVE).
                JsonNode terminal = MfxJson.MAPPER.valueToTree(
                        joined(client.terminals().get(UUID.fromString(args.path("terminal_id").asText()))));
                got.put("id", terminal.path("id"));
                got.put("state", terminal.path("state"));
            }
            case "terminals.page" -> {
                Page<TenantTerminal> page = joined(client.terminals().page(
                        args.hasNonNull("cursor") ? args.get("cursor").asText() : null, filter(args)));
                got.put("count", nodes.numberNode(page.size()));
                got.put("next_cursor", page.nextCursor() == null ? nodes.nullNode() : nodes.textNode(page.nextCursor()));
            }
            case "terminals.list" -> {
                ArrayNode ids = nodes.arrayNode();
                for (TenantTerminal listed : client.terminals().list(filter(args))) {
                    ids.add(listed.getId().toString());
                }
                got.put("ids", ids);
            }
            case "terminals.create" -> {
                CreateTerminal.Builder options = CreateTerminal.builder();
                if (args.hasNonNull("farm_id")) {
                    options.farmId(UUID.fromString(args.get("farm_id").asText()));
                }
                JsonNode terminal = MfxJson.MAPPER.valueToTree(joined(client.terminals().create(
                        UUID.fromString(args.path("broker_id").asText()), UUID.fromString(args.path("server_id").asText()),
                        args.path("idempotency_key").asText(), options.build())));
                got.put("id", terminal.path("id"));
                got.put("state", terminal.path("state"));
            }
            case "execution.place_order" -> got.putAll(placeOrder(client, args));
            case "catalog.quote" -> {
                WhichServer.Builder which = WhichServer.builder();
                if (args.hasNonNull("server_id")) {
                    which.serverId(UUID.fromString(args.get("server_id").asText()));
                }
                JsonNode quote = MfxJson.MAPPER.valueToTree(joined(client.catalog().quote(
                        UUID.fromString(args.path("broker_id").asText()), args.path("symbol").asText(), which.build())));
                for (String field : List.of("bid", "ask", "age_s", "state")) {
                    got.put(field, quote.has(field) ? quote.get(field) : nodes.nullNode());
                }
            }
            case "md.subscribe_account" -> {
                JsonNode subscription = MfxJson.MAPPER.valueToTree(joined(client.md().subscribeAccount(
                        UUID.fromString(args.path("account_id").asText()), args.path("symbol").asText(), args.path("idempotency_key").asText())));
                for (String field : List.of("id", "account_id", "symbol", "state")) {
                    got.put(field, subscription.has(field) ? subscription.get(field) : nodes.nullNode());
                }
            }
            case "md.account_subscriptions" -> {
                ArrayNode symbols = nodes.arrayNode();
                for (AccountMdSubscription subscription : client.md().allAccountSubscriptions(UUID.fromString(args.path("account_id").asText()))) {
                    symbols.add(subscription.getSymbol());
                }
                got.put("symbols", symbols);
            }
            case "md.account_candles" -> {
                CandleList candles = joined(client.md().accountCandles(UUID.fromString(args.path("account_id").asText()),
                        args.path("symbol").asText(), Timeframe.valueOf(args.path("timeframe").asText()),
                        OffsetDateTime.parse(args.path("from").asText()), OffsetDateTime.parse(args.path("to").asText())));
                List<Candle> bars = candles.getItems() == null ? List.of() : candles.getItems();
                got.put("count", nodes.numberNode(bars.size()));
                got.put("first_open", bars.isEmpty() ? nodes.nullNode() : nodes.textNode(bars.get(0).getO()));
                got.put("last_close", bars.isEmpty() ? nodes.nullNode() : nodes.textNode(bars.get(bars.size() - 1).getC()));
            }
            case "risk.check" -> {
                JsonNode result = MfxJson.MAPPER.valueToTree(joined(client.risk().check(UUID.fromString(args.path("account_id").asText()),
                        order(args))));
                got.put("verdict", result.has("verdict") ? result.get("verdict") : nodes.nullNode());
            }
            case "risk.create_plan" -> {
                Map<String, Object> plan = new java.util.LinkedHashMap<>();
                plan.put("account_id", args.path("account_id").asText());
                plan.put("order", order(args));
                plan.put("steps", List.of(Map.of("trigger", Map.of("kind", "r", "value", "1"), "action", Map.of("kind", "close_part", "pct", "50"),
                        "held_by", "mfx")));
                JsonNode created = MfxJson.MAPPER.valueToTree(joined(client.risk().createPlan(plan, args.path("idempotency_key").asText())));
                got.put("id", created.has("id") ? created.get("id") : nodes.nullNode());
            }
            case "risk.create_policy" -> {
                Map<String, Object> rules = JSON.convertValue(args.path("rules"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
                Map<String, Object> reset = JSON.convertValue(args.path("reset"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
                JsonNode policy = MfxJson.MAPPER.valueToTree(joined(client.risk().createPolicy(args.path("name").asText(), rules, reset,
                        args.path("idempotency_key").asText())));
                got.put("id", policy.path("id"));
                got.put("version", policy.path("current_version").path("version"));
            }
            case "risk.challenge_accounts" -> {
                ListChallengeAccounts.Builder filter = ListChallengeAccounts.builder();
                args.path("state").forEach(state -> filter.state(ListChallengeAccounts.State.valueOf(state.asText().toUpperCase(java.util.Locale.ROOT))));
                if (args.hasNonNull("sort")) {
                    filter.sort(ListChallengeAccounts.Sort.valueOf(args.get("sort").asText().toUpperCase(java.util.Locale.ROOT)));
                }
                ArrayNode ids = nodes.arrayNode();
                for (RiskChallengeAccount account : client.risk().challengeAccounts(UUID.fromString(args.path("challenge_id").asText()), filter.build())) {
                    ids.add(account.getAccountId().toString());
                }
                got.put("ids", ids);
            }
            case "copy.create_leader", "copy.leaders", "copy.send_open_signal", "copy.create_follower", "copy.preview_follower",
                    "copy.unfollow", "copy.reset_stopouts", "copy.log", "copy.add_group_followers", "copy.read_events", "copy.close_group",
                    "copy.rollover_group", "copy.closes" -> got.putAll(CopyOps.call(client, op, args));
            case "tenant.service_agreement" -> {
                TenantServiceAgreement agreement = joined(client.tenant().serviceAgreement());
                PlanChangeSummary change = agreement.getPlanChange();
                ArrayNode documents = nodes.arrayNode();
                agreement.getDocuments().forEach(document -> documents.add(document.getDocument()));
                got.put("due", nodes.booleanNode(agreement.getDue()));
                got.put("held", nodes.booleanNode(agreement.getHeld()));
                got.put("plan", nodes.textNode(agreement.getPlan()));
                got.put("plan_change_state", change == null ? nodes.nullNode() : nodes.textNode(change.getState().getValue()));
                got.put("plan_change_to", change == null ? nodes.nullNode() : nodes.textNode(change.getTo()));
                got.put("documents", documents);
                got.put("accept_url", nodes.textNode(agreement.getAcceptUrl().toString()));
            }
            case "agreement.due_warnings" -> got.put("warnings", nodes.numberNode(dueWarnings(UUID.fromString(args.path("terminal_id").asText()),
                    args.path("clients").asInt(), args.path("calls").asInt())));
            case "stream.read" -> got.putAll(readStream(client, channels(args), args.path("count").asInt()));
            case "stream.close_waiting" -> got.putAll(closeWaiting(client, channels(args)));
            default -> throw new NotYet(op);
        }
        return got;
    }

    /**
     * Builds {@code clients} clients, one after another, makes {@code calls} reads of the terminal through
     * each, and counts the warnings the SDK logged that a Service Agreement is due.
     */
    private static int dueWarnings(UUID terminalId, int clients, int calls) {
        Hub served = hub;
        if (served == null) {
            throw new IllegalStateException("the hub is not running");
        }
        List<LogRecord> heard = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logged) {
                synchronized (heard) {
                    heard.add(logged);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger(MfxClient.LOGGER_NAME);
        logger.addHandler(handler);
        try {
            for (int i = 0; i < clients; i++) {
                try (MfxClient client = MfxClient.builder(served.baseUrl(), VOCABULARY.path("key").path("id").asText(), SigningKey.fromSeed(SEED))
                        .pause(wait -> CompletableFuture.completedFuture(null))
                        .build()) {
                    for (int j = 0; j < calls; j++) {
                        joined(client.terminals().get(terminalId));
                    }
                }
            }
        } finally {
            logger.removeHandler(handler);
        }
        synchronized (heard) {
            return (int) heard.stream()
                    .filter(r -> r.getLevel().intValue() >= Level.WARNING.intValue() && r.getMessage().contains("Service Agreement"))
                    .count();
        }
    }

    /**
     * A case's value for a decimal argument: {@code $decimal} is this SDK's BigDecimal, and {@code $float}
     * is a Java double, handed to the call as it is, so the SDK's own signature decides whether it is taken.
     */
    static Object amount(JsonNode value) {
        if (value.has("$decimal")) {
            return new BigDecimal(value.get("$decimal").asText());
        }
        if (value.has("$float")) {
            return value.get("$float").asDouble();
        }
        return new BigDecimal(value.asText());
    }

    /**
     * Invokes a public SDK method by its Java signature with the case's values. A value of the wrong
     * type (a double where the SDK takes a BigDecimal) is refused by the JVM before the method runs,
     * as IllegalArgumentException: Java's own type error, and nothing is sent.
     */
    static Object invoke(Object target, String name, Class<?>[] types, Object... values) {
        try {
            return target.getClass().getMethod(name, types).invoke(target, values);
        } catch (java.lang.reflect.InvocationTargetException failed) {
            if (failed.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException(failed.getCause());
        } catch (NoSuchMethodException | IllegalAccessException missing) {
            throw new IllegalStateException(missing);
        }
    }

    private static Map<String, JsonNode> placeOrder(MfxClient client, JsonNode args) {
        JsonNodeFactory nodes = JsonNodeFactory.instance;
        Object result = invoke(client.execution(), "placeOrder",
                new Class<?>[] {UUID.class, String.class, OrderPlace.SideEnum.class, BigDecimal.class, String.class},
                UUID.fromString(args.path("terminal_id").asText()), args.path("symbol").asText(),
                OrderPlace.SideEnum.fromValue(args.path("side").asText()), amount(args.path("volume")),
                args.path("idempotency_key").asText());
        @SuppressWarnings("unchecked")
        ExecutionResult answer = joined((CompletableFuture<ExecutionResult>) result);
        Map<String, JsonNode> got = new TreeMap<>();
        got.put("ok", nodes.booleanNode(answer.ok()));
        got.put("state", answer.state() == null ? nodes.nullNode() : nodes.textNode(answer.state().name()));
        got.put("command_id", answer.commandId() == null ? nodes.nullNode() : nodes.textNode(answer.commandId().toString()));
        got.put("duplicate", nodes.booleanNode(answer.duplicate()));
        got.put("retryable", nodes.booleanNode(answer.retryable()));
        got.put("exec_price", answer.execPrice() == null ? nodes.nullNode() : nodes.textNode(answer.execPrice().toPlainString()));
        got.put("position", answer.tickets().position() == null ? nodes.nullNode() : nodes.numberNode(answer.tickets().position()));
        got.put("comment_raw", answer.commentRaw() == null ? nodes.nullNode() : nodes.textNode(answer.commentRaw()));
        return got;
    }

    /** A case's market order, in the API's names, its volume this SDK's BigDecimal or a Java double as the case says. */
    private static Map<String, Object> order(JsonNode args) {
        Map<String, Object> order = new java.util.LinkedHashMap<>();
        order.put("symbol", args.path("symbol").asText());
        order.put("side", args.path("side").asText());
        order.put("type", "market");
        order.put("volume", amount(args.path("volume")));
        return order;
    }

    private static List<String> channels(JsonNode args) {
        List<String> channels = new ArrayList<>();
        args.path("channels").forEach(channel -> channels.add(channel.asText()));
        return channels;
    }

    /**
     * Opens the stream (reconnecting allowed, no real wait between attempts), subscribes each channel in
     * order, reads until {@code count} messages have arrived, then closes.
     */
    private static Map<String, JsonNode> readStream(MfxClient client, List<String> channels, int count) {
        JsonNodeFactory nodes = JsonNodeFactory.instance;
        ArrayNode seqs = nodes.arrayNode();
        ArrayNode types = nodes.arrayNode();
        StreamStats stats;
        try (MfxStream stream = joined(client.stream(StreamOptions.builder().maxReconnects(3).build()))) {
            for (String channel : channels) {
                joined(stream.subscribe(channel));
            }
            for (StreamEvent event : stream) {
                if (event instanceof StreamEvent.Message message) {
                    seqs.add(message.seq() == null ? nodes.nullNode() : nodes.numberNode(message.seq()));
                    types.add(message.type() == null ? nodes.nullNode() : nodes.textNode(message.type()));
                    if (seqs.size() >= count) {
                        break;
                    }
                }
            }
            stats = stream.stats();
        }
        Map<String, JsonNode> got = new TreeMap<>();
        got.put("seqs", seqs);
        got.put("types", types);
        got.put("dropped", nodes.numberNode(stats.dropped()));
        got.put("reconnects", nodes.numberNode(stats.reconnects()));
        got.put("lifetime_reconnects", nodes.numberNode(stats.lifetimeReconnects()));
        got.put("slow_consumer_drops", nodes.numberNode(stats.slowConsumerDrops()));
        got.put("resyncs", nodes.numberNode(stats.resyncs()));
        got.put("refused", nodes.numberNode(stats.refused()));
        return got;
    }

    /**
     * Leaves a read waiting on a quiet stream, closes the stream, and sees whether the read ended: it has
     * when it finishes within 2 s with nothing, no message and no error.
     */
    private static Map<String, JsonNode> closeWaiting(MfxClient client, List<String> channels) throws InterruptedException {
        MfxStream stream = joined(client.stream(StreamOptions.builder().maxReconnects(3).build()));
        for (String channel : channels) {
            joined(stream.subscribe(channel));
        }
        CompletableFuture<@Nullable StreamEvent> waiting = stream.next();
        Thread.sleep(100);
        stream.close();
        boolean ended;
        try {
            ended = waiting.get(2, java.util.concurrent.TimeUnit.SECONDS) == null;
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException notEnded) {
            ended = false;
        }
        Map<String, JsonNode> got = new TreeMap<>();
        got.put("ended", JsonNodeFactory.instance.booleanNode(ended));
        got.put("reconnects", JsonNodeFactory.instance.numberNode(stream.stats().reconnects()));
        return got;
    }

    private static ListTerminals filter(JsonNode args) {
        ListTerminals.Builder filter = ListTerminals.builder();
        if (args.hasNonNull("limit")) {
            filter.limit(args.get("limit").asInt());
        }
        return filter.build();
    }

    /** A case value against what came back: a {@code $decimal} compares as a decimal. */
    private static boolean same(@Nullable JsonNode got, JsonNode want) {
        if (want instanceof ObjectNode wrapped && wrapped.has("$decimal")) {
            try {
                return got != null && new BigDecimal(got.asText()).compareTo(new BigDecimal(wrapped.get("$decimal").asText())) == 0;
            } catch (NumberFormatException notDecimal) {
                return false;
            }
        }
        if (got != null && got.isContainerNode() && want.isContainerNode()) {
            // An object's fields in any order, and a number by its value (3 and 3L are the same).
            return got.equals((x, y) -> x.isNumber() && y.isNumber() ? x.decimalValue().compareTo(y.decimalValue()) : x.equals(y) ? 0 : 1, want);
        }
        return (got == null ? "null" : got.toString()).equals(want.toString());
    }

    private static final class NotYet extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NotYet(String op) {
            super(op);
        }
    }
}
