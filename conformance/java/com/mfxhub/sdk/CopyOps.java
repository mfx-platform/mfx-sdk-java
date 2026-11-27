package com.mfxhub.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mfxhub.sdk.models.CopyCloseState;
import com.mfxhub.sdk.models.CopyExternalOpen;
import com.mfxhub.sdk.models.CopyLeaderSource;
import com.mfxhub.sdk.models.CopyLeaderState;
import com.mfxhub.sdk.models.CopyOutcome;
import com.mfxhub.sdk.models.CopyReason;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** The shared suite's copy operations (vocabulary.json, mfx-sdk-conformance 0.8.0) in Java: nothing more. */
final class CopyOps {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private CopyOps() {
    }

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

    private static UUID uuid(JsonNode args, String name) {
        return UUID.fromString(args.path(name).asText());
    }

    private static Map<String, Object> map(JsonNode value) {
        return JSON.convertValue(value, new TypeReference<Map<String, Object>>() { });
    }

    private static JsonNode tree(Object model) {
        return MfxJson.MAPPER.valueToTree(model);
    }

    private static JsonNode field(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null ? NODES.nullNode() : value;
    }

    private static ArrayNode each(JsonNode rows, String name) {
        ArrayNode out = NODES.arrayNode();
        rows.forEach(row -> out.add(field(row, name)));
        return out;
    }

    static Map<String, JsonNode> call(MfxClient client, String op, JsonNode args) {
        Copy copy = client.copy();
        Map<String, JsonNode> got = new TreeMap<>();
        switch (op) {
            case "copy.create_leader" -> {
                CreateLeader.Builder options = CreateLeader.builder();
                if (args.hasNonNull("account_id")) {
                    options.accountId(uuid(args, "account_id"));
                }
                JsonNode leader = tree(joined(copy.createLeader(CopyLeaderSource.fromValue(args.path("source").asText()),
                        args.path("signal_lifetime_s").asInt(), args.path("idempotency_key").asText(), options.build())));
                for (String name : List.of("id", "source", "state")) {
                    got.put(name, field(leader, name));
                }
            }
            case "copy.leaders" -> {
                ListCopyLeaders.Builder filter = ListCopyLeaders.builder();
                args.path("state").forEach(state -> filter.state(CopyLeaderState.fromValue(state.asText())));
                ListCopyLeaders built = filter.build();
                ArrayNode ids = NODES.arrayNode();
                JsonNode stats = NODES.nullNode();
                String cursor = null;
                do {
                    CopyPage<?, ?> page = joined(copy.leaders(cursor, built));
                    page.items().forEach(item -> ids.add(field(tree(item), "id")));
                    stats = page.stats() == null ? NODES.nullNode() : tree(page.stats());
                    cursor = page.nextCursor();
                } while (cursor != null);
                got.put("ids", ids);
                got.put("stats_leaders", field(stats, "leaders"));
            }
            case "copy.send_open_signal" -> {
                Object builder = SuiteTest.invoke(new OpenSignalFactory(), "volume", new Class<?>[] {BigDecimal.class},
                        SuiteTest.amount(args.path("volume")));
                JsonNode accepted = tree(joined(copy.sendOpenSignal(uuid(args, "leader_id"), args.path("id").asText(), args.path("symbol").asText(),
                        CopyExternalOpen.SideEnum.fromValue(args.path("side").asText()), args.path("idempotency_key").asText(),
                        ((OpenSignal.Builder) builder).build())));
                got.put("signal_id", field(accepted, "signal_id"));
                got.put("duplicate", field(accepted, "duplicate"));
            }
            case "copy.create_follower" -> {
                FollowerMandate.Builder mandate = FollowerMandate.builder().scaling(map(args.path("scaling")));
                if (args.hasNonNull("options")) {
                    mandate.options(map(args.get("options")));
                }
                JsonNode follower = tree(joined(copy.createFollower(uuid(args, "account_id"), uuid(args, "leader_id"),
                        args.path("idempotency_key").asText(), mandate.build())));
                for (String name : List.of("id", "state", "mandate_version")) {
                    got.put(name, field(follower, name));
                }
            }
            case "copy.preview_follower" -> {
                List<Map<String, Object>> rows = new ArrayList<>();
                args.path("rows").forEach(row -> rows.add(map(row)));
                JsonNode preview = tree(joined(copy.previewFollower(uuid(args, "account_id"), uuid(args, "leader_id"),
                        map(args.path("scaling")), rows)));
                got.put("refusal", field(preview, "refusal"));
                got.put("follower_volumes", each(preview.path("rows"), "follower_volume"));
                got.put("reasons", each(preview.path("rows"), "reason"));
            }
            case "copy.unfollow" -> got.put("state", field(tree(joined(copy.unfollow(uuid(args, "follower_id"),
                    args.path("close_copied").asBoolean(), args.path("idempotency_key").asText()))), "state"));
            case "copy.reset_stopouts" -> got.put("state", field(tree(joined(copy.resetStopouts(uuid(args, "follower_id"),
                    args.path("reason").asText(), args.path("idempotency_key").asText()))), "state"));
            case "copy.log" -> {
                ListCopyLog.Builder filter = ListCopyLog.builder();
                args.path("outcome").forEach(outcome -> filter.outcome(CopyOutcome.fromValue(outcome.asText())));
                // A reason outside the closed list reads as CopyReason.UNKNOWN_DEFAULT_OPEN_API, which the query
                args.path("reason").forEach(reason -> filter.reason(CopyReason.fromValue(reason.asText())));
                if (args.hasNonNull("from")) {
                    filter.from(OffsetDateTime.parse(args.get("from").asText()));
                }
                if (args.hasNonNull("to")) {
                    filter.to(OffsetDateTime.parse(args.get("to").asText()));
                }
                CopyPage<?, ?> page = joined(copy.log(null, filter.build()));
                ArrayNode reasons = NODES.arrayNode();
                page.items().forEach(item -> reasons.add(field(tree(item), "reason")));
                got.put("count", NODES.numberNode(page.size()));
                got.put("reasons", reasons);
                got.put("stats_refused", page.stats() == null ? NODES.nullNode() : field(tree(page.stats()), "refused"));
            }
            case "copy.add_group_followers" -> {
                List<UUID> ids = new ArrayList<>();
                args.path("follower_ids").forEach(f -> ids.add(UUID.fromString(f.asText())));
                JsonNode result = tree(joined(copy.addGroupFollowers(uuid(args, "group_id"), ids, args.path("idempotency_key").asText())));
                got.put("outcomes", each(result.path("results"), "outcome"));
                got.put("reasons", each(result.path("results"), "reason"));
            }
            case "copy.close_group" -> {
                JsonNode result = tree(joined(copy.closeGroup(uuid(args, "group_id"), args.path("reason").asText(),
                        args.path("pause").asBoolean(), args.path("idempotency_key").asText())));
                got.put("batch_id", field(result, "batch_id"));
                got.put("paused", field(result, "paused"));
                got.put("closes", result.path("closes"));
            }
            case "copy.rollover_group" -> {
                RolloverGroup.Builder options = RolloverGroup.builder();
                if (args.has("fields")) {
                    List<RolloverField> fields = new ArrayList<>();
                    args.path("fields").forEach(f -> fields.add(RolloverField.valueOf(f.asText().toUpperCase(Locale.ROOT))));
                    options.fields(fields);
                }
                if (args.hasNonNull("after")) {
                    options.after(uuid(args, "after"));
                }
                if (args.hasNonNull("limit")) {
                    options.limit(args.get("limit").asInt());
                }
                JsonNode result = tree(joined(copy.rolloverGroup(uuid(args, "group_id"), args.path("dry_run").asBoolean(), options.build(),
                        args.hasNonNull("idempotency_key") ? args.get("idempotency_key").asText() : null)));
                got.put("outcomes", each(result.path("results"), "outcome"));
                got.put("reasons", each(result.path("results"), "reason"));
                got.put("next_after", field(result, "next_after"));
            }
            case "copy.closes" -> {
                ListCopyCloses.Builder filter = ListCopyCloses.builder();
                if (args.hasNonNull("batch_id")) {
                    filter.batchId(uuid(args, "batch_id"));
                }
                args.path("state").forEach(state -> filter.state(CopyCloseState.fromValue(state.asText())));
                ListCopyCloses built = filter.build();
                ArrayNode ids = NODES.arrayNode();
                ArrayNode states = NODES.arrayNode();
                JsonNode stats = NODES.nullNode();
                String cursor = null;
                do {
                    CopyPage<?, ?> page = joined(copy.closes(cursor, built));
                    page.items().forEach(item -> {
                        ids.add(field(tree(item), "id"));
                        states.add(field(tree(item), "state"));
                    });
                    stats = page.stats() == null ? NODES.nullNode() : tree(page.stats());
                    cursor = page.nextCursor();
                } while (cursor != null);
                got.put("ids", ids);
                got.put("states", states);
                got.put("stats_incomplete", field(stats, "incomplete"));
            }
            case "copy.read_events" -> got.putAll(readEvents(client, args.path("count").asInt()));
            default -> throw new IllegalStateException("no copy op " + op);
        }
        return got;
    }

    /** Opens the stream, subscribes events, reads count messages as the SDK's typed copy events, and closes. */
    private static Map<String, JsonNode> readEvents(MfxClient client, int count) {
        ArrayNode types = NODES.arrayNode();
        ArrayNode accounts = NODES.arrayNode();
        ArrayNode venues = NODES.arrayNode();
        ArrayNode reasons = NODES.arrayNode();
        try (MfxStream stream = joined(client.stream(StreamOptions.builder().maxReconnects(3).build()))) {
            joined(stream.subscribe(Channel.EVENTS));
            for (StreamEvent event : stream) {
                if (!(event instanceof StreamEvent.Message message)) {
                    continue;
                }
                Event typed = Events.eventOf(message);
                types.add(message.type());
                switch (typed) {
                    case CopyCopied e -> add(accounts, venues, reasons, e.accountId(), e.venue(), null);
                    case CopyRefused e -> add(accounts, venues, reasons, e.accountId(), e.venue(), e.reason());
                    case CopyStopoutEvent e -> add(accounts, venues, reasons, e.accountId(), e.venue(), null);
                    case CopyDrift e -> add(accounts, venues, reasons, e.accountId(), e.venue(), null);
                    case CopyStateChanged e -> add(accounts, venues, reasons, e.accountId(), e.venue(), null);
                    case CopyClosedEvent e -> add(accounts, venues, reasons, e.accountId(), e.venue(), null);
                    case null, default -> add(accounts, venues, reasons, null, null, null);
                }
                if (types.size() >= count) {
                    break;
                }
            }
        }
        Map<String, JsonNode> got = new TreeMap<>();
        got.put("types", types);
        got.put("accounts", accounts);
        got.put("venues", venues);
        got.put("reasons", reasons);
        return got;
    }

    private static void add(ArrayNode accounts, ArrayNode venues, ArrayNode reasons, UUID account, String venue, String reason) {
        accounts.add(account == null ? null : account.toString());
        venues.add(venue);
        reasons.add(reason);
    }

    /** OpenSignal's static factories through an instance, so the runner can invoke one by its Java signature. */
    public static final class OpenSignalFactory {
        /**
         * An open of this many lots.
         *
         * @param volume the volume
         * @return the builder
         */
        public OpenSignal.Builder volume(BigDecimal volume) {
            return OpenSignal.volume(volume);
        }
    }
}
