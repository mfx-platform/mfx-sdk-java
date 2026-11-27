package com.mfxhub.sdk;

import com.mfxhub.sdk.models.EaReport;
import com.mfxhub.sdk.models.TenantExpertAdvisor;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * The tenant's Expert Advisor versions ({@code client.expertAdvisors()}; {@code ea:read} to read,
 * {@code ea:write} to upload), and the {@code ea:} channel's messages read as what they are. Attaching a
 * version to a terminal is {@link Terminals#attachExpertAdvisor}.
 */
public final class ExpertAdvisors {
    /** The message on an {@code ea:} channel carrying a batch of records. */
    public static final String TELEMETRY = "expert_advisor.telemetry";
    /** The message on an {@code ea:} channel saying records were discarded. */
    public static final String TELEMETRY_LOST = "expert_advisor.telemetry_lost";
    /** How long {@link #waitForVerdict(UUID)} waits unless told: ten minutes. */
    public static final Duration DEFAULT_WAIT = Duration.ofMinutes(10);
    /** A version stops changing on its own once it is in one of these ({@code judged} is on the way to one). */
    static final Set<String> SETTLED = Set.of("approved", "held", "rejected", "withdrawn", "revoked");

    private final MfxClient client;

    ExpertAdvisors(MfxClient client) {
        this.client = client;
    }

    private static String version(UUID expertAdvisorId) {
        return "/expert-advisors/" + Objects.requireNonNull(expertAdvisorId, "expertAdvisorId");
    }

    /**
     * Uploads a bundle: {@code path} to source, exactly one {@code .mq5} and any {@code .mqh} it includes, as
     * text (sent as UTF-8, base64). Never a compiled file. A new bundle answers its version in
     * {@code uploaded}, with the checks started; the same bundle again answers the version it made. A file
     * the platform will not take is 422 with {@code details.why}; no farm that runs customer code is 409
     * {@code no_customer_code_farm}.
     *
     * @param name the expert's name
     * @param files each file's path and its source text, in the order to send them
     * @param idempotencyKey the caller's key
     * @return the version
     */
    public CompletableFuture<TenantExpertAdvisor> upload(String name, Map<String, String> files, String idempotencyKey) {
        List<Map<String, String>> parts = new ArrayList<>();
        Objects.requireNonNull(files, "files").forEach((path, source) -> parts.add(Map.of("path", path,
                "content_base64", Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8)))));
        Body body = Body.of().put("name", Objects.requireNonNull(name, "name")).put("files", parts);
        return client.write("POST", "/expert-advisors", body.build(), idempotencyKey, TenantExpertAdvisor.class);
    }

    /**
     * One version: its state, verdict, each check and where it stands, and the terminals that carry it.
     * Never its bytes.
     *
     * @param expertAdvisorId the version
     * @return the version
     */
    public CompletableFuture<TenantExpertAdvisor> get(UUID expertAdvisorId) {
        return client.get(version(expertAdvisorId), TenantExpertAdvisor.class);
    }

    /**
     * One page of the tenant's versions, newest first.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param limit the page size; the platform's default when null
     * @return the page
     */
    public CompletableFuture<Page<TenantExpertAdvisor>> page(@Nullable String cursor, @Nullable Integer limit) {
        return client.page("/expert-advisors", Query.page(cursor, limit).build(), TenantExpertAdvisor.class);
    }

    /**
     * The first page of the tenant's versions.
     *
     * @return the page
     */
    public CompletableFuture<Page<TenantExpertAdvisor>> page() {
        return page(null, null);
    }

    /**
     * Every version, following the cursor to the end. Iterating blocks on each page.
     *
     * @param limit the page size; the platform's default when null
     * @return every version
     */
    public Iterable<TenantExpertAdvisor> list(@Nullable Integer limit) {
        return Walk.of(cursor -> page(cursor, limit));
    }

    /**
     * Every version.
     *
     * @return every version
     */
    public Iterable<TenantExpertAdvisor> list() {
        return list(null);
    }

    /**
     * The checks' report: each finding with its rule, colour and line. 409 until the verdict is set; wait
     * for it with {@link #waitForVerdict(UUID)}.
     *
     * @param expertAdvisorId the version
     * @return the report
     */
    public CompletableFuture<EaReport> report(UUID expertAdvisorId) {
        return client.get(version(expertAdvisorId) + "/report", EaReport.class);
    }

    /**
     * Withdraws a version; a check still running stops. 409 while a terminal carries it.
     *
     * @param expertAdvisorId the version
     * @param idempotencyKey the caller's key
     * @return the version, withdrawn
     */
    public CompletableFuture<TenantExpertAdvisor> delete(UUID expertAdvisorId, String idempotencyKey) {
        return client.write("DELETE", version(expertAdvisorId), null, idempotencyKey, TenantExpertAdvisor.class);
    }

    /**
     * Reads the version until its checks have settled it, for up to ten minutes.
     *
     * @param expertAdvisorId the version
     * @return the last read
     */
    public CompletableFuture<TenantExpertAdvisor> waitForVerdict(UUID expertAdvisorId) {
        return waitForVerdict(expertAdvisorId, DEFAULT_WAIT);
    }

    /**
     * Reads the version until its checks have settled it (approved, held, rejected, withdrawn or revoked),
     * or until {@code timeout} has passed on the client's clock, waiting 2 s, doubling to 15 s. Completes
     * with the last read either way: a version still being checked when the time runs out is reported as
     * it is, not failed.
     *
     * @param expertAdvisorId the version
     * @param timeout how long to keep reading
     * @return the last read
     */
    public CompletableFuture<TenantExpertAdvisor> waitForVerdict(UUID expertAdvisorId, Duration timeout) {
        Instant deadline = client.clock().instant().plus(Objects.requireNonNull(timeout, "timeout"));
        return get(expertAdvisorId).thenCompose(first -> poll(expertAdvisorId, first, Terminals.POLL_FROM, deadline));
    }

    private CompletableFuture<TenantExpertAdvisor> poll(UUID id, TenantExpertAdvisor read, Duration wait, Instant deadline) {
        if (settled(read) || !client.clock().instant().isBefore(deadline)) {
            return CompletableFuture.completedFuture(read);
        }
        Duration capped = wait.compareTo(Terminals.POLL_CAP) <= 0 ? wait : Terminals.POLL_CAP;
        return client.pause(capped).thenCompose(done -> get(id)).thenCompose(next -> poll(id, next, wait.multipliedBy(2), deadline));
    }

    private static boolean settled(TenantExpertAdvisor read) {
        return read.getState() != null && SETTLED.contains(read.getState().getValue());
    }

    /**
     * A message from an {@code ea:} channel as what it is, or null for any other message. Delivery is at
     * least once: a record's {@code (segment, line)} tells a repeat.
     *
     * @param received what the stream delivered
     * @return an {@link EaTelemetry} or an {@link EaTelemetryLost}; null for any other message
     * @throws IllegalArgumentException a field the contract requires is missing
     */
    public static @Nullable EaMessage telemetryOf(StreamEvent received) {
        if (!(received instanceof StreamEvent.Message message)) {
            return null;
        }
        EventData d = new EventData(String.valueOf(message.type()), message.data());
        if (TELEMETRY.equals(message.type())) {
            List<EaRecord> records = new ArrayList<>();
            for (EventData r : d.parts("records")) {
                records.add(new EaRecord(r.integer("segment"), r.integer("line"), r.object("record")));
            }
            Long skipped = d.integerOrNull("skipped");
            Long dropped = d.integerOrNull("dropped");
            return new EaTelemetry(message.channel(), message.seq(), records, skipped == null ? 0 : skipped, dropped == null ? 0 : dropped);
        }
        if (TELEMETRY_LOST.equals(message.type())) {
            return new EaTelemetryLost(message.channel(), message.seq(), d.integer("segment_from"), d.integer("segment_to"), d.integer("records"));
        }
        return null;
    }
}
