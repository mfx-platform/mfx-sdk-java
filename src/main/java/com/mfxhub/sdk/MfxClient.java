package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * One signed connection to the MFX Hub API.
 *
 * <p>Every request is signed with the tenant's Ed25519 key, afresh on every attempt. A read that is
 * rate limited is tried again after the platform's own {@code Retry-After}. A write is never retried,
 * because a retried write is a second order.
 *
 * <p>Every call returns a {@link CompletableFuture}; block on it with {@code join()} if you want to. A
 * failure completes it exceptionally with an {@link MfxException}, which {@code join()} throws as the
 * cause of a {@link CompletionException}.
 *
 * <pre>{@code
 * try (MfxClient client = MfxClient.builder(URI.create("https://api.dev.mfxhub.com/v1"), keyId,
 *         SigningKey.parse(privateKeyPem)).build()) {
 *     TenantTerminal terminal = client.terminals().get(terminalId).join();
 * }
 * }</pre>
 */
public final class MfxClient implements AutoCloseable {
    /** The default time a single request may take. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    /** The default number of times a rate-limited read is tried again. */
    public static final int DEFAULT_READ_RETRIES = 3;
    /** The {@code User-Agent} every request carries. */
    public static final String USER_AGENT = "mfx-sdk-java/" + SdkVersion.VALUE;
    /** The environment variable naming a file to append one JSON line to for every HTTP attempt. */
    public static final String CALL_LOG_ENVIRONMENT = "MFX_CALL_LOG";
    /**
     * The one logger this SDK writes to, a {@link System.Logger}: the host levels and routes it (by default
     * {@code java.util.logging}, or SLF4J or Log4j through their {@code System.LoggerFinder} bridges).
     */
    public static final String LOGGER_NAME = "com.mfxhub.sdk";
    /** The header the platform sends on every answer while the organisation has an agreement to accept. */
    static final String SERVICE_AGREEMENT_HEADER = "MFX-Service-Agreement";

    // DECISION: the JDK's System.Logger rather than a logging dependency: the SDK had no logger before this
    // warning, and a library must not choose its host's logging framework.
    private static final System.Logger LOG = System.getLogger(LOGGER_NAME);

    /** The longest the client waits between attempts when the platform names no delay. */
    static final Duration BACKOFF_CAP = Duration.ofSeconds(30);

    private final String base;
    private final String basePath;
    private final String origin;
    private final Duration timeout;
    private final int readRetries;
    private final RequestSigner signer;
    private final Pause pause;
    private final DoubleSupplier random;
    private final @Nullable Consumer<CallTiming> onCall;
    private final @Nullable Path callLog;
    private final Transport transport;
    private final @Nullable HttpClient owned;
    private final AtomicReference<@Nullable Budget> budget = new AtomicReference<>();
    private final AtomicBoolean agreementWarned = new AtomicBoolean();
    private final Clock clock;
    private final Terminals terminals = new Terminals(this);
    private final Catalog catalog = new Catalog(this);
    private final Farms farms = new Farms(this);
    private final UsageReader usage = new UsageReader(this);
    private final Maintenance maintenance = new Maintenance(this);
    private final Tenant tenant = new Tenant(this);
    private final Execution execution = new Execution(this, "terminals");
    private final Accounts accounts = new Accounts(this);
    private final TradingAccounts tradingAccounts = new TradingAccounts(this);
    private final MarketData md = new MarketData(this);
    private final Risk risk = new Risk(this);
    private final Webhooks webhooks = new Webhooks(this);
    private final ExpertAdvisors expertAdvisors = new ExpertAdvisors(this);
    private final Copy copy = new Copy(this);
    private final Billing billing = new Billing(this);

    private MfxClient(Builder builder) {
        URI url = builder.baseUrl;
        String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
        if (!url.isAbsolute() || url.getRawAuthority() == null || !(scheme.equals("https") || scheme.equals("http"))) {
            throw new IllegalArgumentException("baseUrl: the base URL is an absolute http or https URL");
        }
        String path = url.getRawPath() == null ? "" : url.getRawPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        this.origin = scheme + "://" + url.getRawAuthority();
        this.basePath = path;
        this.base = origin + path;
        this.timeout = builder.timeout;
        this.readRetries = builder.readRetries;
        this.clock = builder.clock == null ? Clock.systemUTC() : builder.clock;
        this.signer = new RequestSigner(builder.keyId, builder.key, clock, builder.nonce);
        this.pause = builder.pause == null ? Pause.delayed() : builder.pause;
        this.random = builder.random == null ? () -> ThreadLocalRandom.current().nextDouble() : builder.random;
        this.onCall = builder.onCall;
        String log = System.getenv(CALL_LOG_ENVIRONMENT);
        this.callLog = log == null || log.isEmpty() ? null : Path.of(log);
        if (builder.transport == null) {
            HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
            this.owned = http;
            this.transport = request -> http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        } else {
            this.owned = null;
            this.transport = builder.transport;
        }
    }

    /**
     * A client for the API at {@code baseUrl}, signing as {@code keyId}.
     *
     * @param baseUrl the API's base, such as {@code https://api.mfxhub.com/v1}
     * @param keyId the key's id
     * @param key the key's private half
     * @return a builder; {@link Builder#build()} makes the client
     */
    public static Builder builder(URI baseUrl, String keyId, SigningKey key) {
        return new Builder(baseUrl, keyId, key);
    }

    /**
     * This tenant's terminals: one by id, one page, or every one by cursor.
     *
     * @return the terminals' calls
     */
    public Terminals terminals() {
        return terminals;
    }

    /**
     * Placing and changing orders on a terminal. Every call completes with an {@link ExecutionResult},
     * never exceptionally.
     *
     * @return the execution calls
     */
    public Execution execution() {
        return execution;
    }

    /**
     * What a terminal's account looks like right now: balance, positions, orders, deals and spreads.
     *
     * @return the account reads
     */
    public Accounts accounts() {
        return accounts;
    }

    /**
     * The trading accounts over every venue (a MetaTrader account or a cTrader one): their reads, the commands
     * on an account, and the cTrader grant flow.
     *
     * @return the trading accounts' calls
     */
    public TradingAccounts tradingAccounts() {
        return tradingAccounts;
    }

    /**
     * Market data: what this tenant has asked to be streamed, and the candles and ticks it can read.
     *
     * @return the market-data calls
     */
    public MarketData md() {
        return md;
    }

    /**
     * Risk Management: policies, the accounts they protect, the planner, exit plans, challenges and the
     * activity.
     *
     * @return the Risk Management calls
     */
    public Risk risk() {
        return risk;
    }

    /**
     * The tenant's webhook endpoints; {@link Webhooks#verify} checks a delivery.
     *
     * @return the webhook calls
     */
    public Webhooks webhooks() {
        return webhooks;
    }

    /**
     * The tenant's Expert Advisor versions: upload, read, report, withdraw and wait for a verdict.
     *
     * @return the expert advisor calls
     */
    public ExpertAdvisors expertAdvisors() {
        return expertAdvisors;
    }

    /**
     * Copy Trading: leaders, followers, groups, stop-outs, requested closes, signals and the copy log.
     *
     * @return the Copy Trading calls
     */
    public Copy copy() {
        return copy;
    }

    /**
     * Billing, read only: standing, profiles, forecast, prices and invoices.
     *
     * @return the billing reads
     */
    public Billing billing() {
        return billing;
    }

    /**
     * What MFX supports and what a broker lists: brokers, servers, symbols and quotes.
     *
     * @return the catalogue's calls
     */
    public Catalog catalog() {
        return catalog;
    }

    /**
     * The farms this tenant's plan places terminals on.
     *
     * @return the farms' calls
     */
    public Farms farms() {
        return farms;
    }

    /**
     * Every limit that applies to this key and its tenant, and how much is used.
     *
     * @return the usage call
     */
    public UsageReader usage() {
        return usage;
    }

    /**
     * Planned maintenance on this tenant's terminals.
     *
     * @return the maintenance reads
     */
    public Maintenance maintenance() {
        return maintenance;
    }

    /**
     * What the tenant reads about itself.
     *
     * @return the tenant's reads
     */
    public Tenant tenant() {
        return tenant;
    }

    /**
     * The API's base.
     *
     * @return the base, without a trailing slash
     */
    public URI baseUrl() {
        return URI.create(base);
    }

    /**
     * The key's id.
     *
     * @return the id every request is signed as
     */
    public String keyId() {
        return signer.keyId();
    }

    /**
     * The signer every request goes through.
     *
     * @return the signer
     */
    public RequestSigner signer() {
        return signer;
    }

    /**
     * What the key had left when the platform last answered.
     *
     * @return the budget; null before the first answer that carried one
     */
    public @Nullable Budget budget() {
        return budget.get();
    }

    /**
     * A read: tried again after a 429, up to the client's read retries, after {@code Retry-After} or,
     * when the platform names no delay, a jittered backoff. A failure completes it with its typed
     * {@link MfxApiException}.
     */
    CompletableFuture<MfxAnswer> read(MfxRequest request) {
        return attempt(request, 0);
    }

    private CompletableFuture<MfxAnswer> attempt(MfxRequest request, int attempt) {
        return send(request).thenCompose(answer -> {
            if (answer.status() == 429 && attempt < readRetries) {
                Duration wait = retryAfter(answer.headers());
                return pause.pause(wait == null ? backoff(attempt) : wait).thenCompose(done -> attempt(request, attempt + 1));
            }
            return checked(answer, request);
        });
    }

    /**
     * A write: sent once, never retried. A failure completes it with its typed {@link MfxApiException}.
     * The idempotency key is the caller's: it is sent as given, a blank one is refused before anything
     * is sent, and none is ever invented, because a key made up per attempt makes a retry by the
     * caller a second order.
     *
     * @throws IllegalArgumentException the idempotency key is blank
     */
    CompletableFuture<MfxAnswer> write(MfxRequest request) {
        Objects.requireNonNull(request, "request");
        String key = request.idempotencyKey();
        if (key != null && key.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey: an idempotency key is the caller's own, and not blank");
        }
        return send(request).thenCompose(answer -> checked(answer, request));
    }

    /** The answer's body as {@code type}, read as the API writes it ({@link MfxJson}). */
    static <T> T body(MfxAnswer answer, Class<T> type, String what) {
        try {
            T value = MfxJson.MAPPER.readValue(answer.body(), type);
            if (value == null) {
                throw new MfxException(what + " answered " + answer.status() + " with no " + type.getSimpleName());
            }
            return value;
        } catch (IOException unreadable) {
            throw new MfxException(what + " answered " + answer.status() + " with a body that is not a "
                    + type.getSimpleName() + ": " + unreadable.getMessage(), unreadable);
        }
    }

    /** A read whose answer is one {@code type}. */
    <T> CompletableFuture<T> get(String path, List<Map.Entry<String, String>> query, Class<T> type) {
        return read(MfxRequest.get(path, query)).thenApply(answer -> body(answer, type, "GET " + path));
    }

    /** A read whose answer is one {@code type}, with no query. */
    <T> CompletableFuture<T> get(String path, Class<T> type) {
        return get(path, List.of(), type);
    }

    /**
     * A read whose answer is one page of {@code item}: its items, its cursor (an empty one read as
     * none), how many it returned and, on the lists that say, its source. No total, ever.
     */
    <T> CompletableFuture<Page<T>> page(String path, List<Map.Entry<String, String>> query, Class<T> item) {
        return read(MfxRequest.get(path, query)).thenApply(answer -> pageOf(answer, item, "GET " + path));
    }

    static <T> Page<T> pageOf(MfxAnswer answer, Class<T> item, String what) {
        JsonNode page = body(answer, JsonNode.class, what);
        if (!page.isObject() || !(page.path("items").isArray() || page.path("items").isMissingNode())) {
            throw new MfxException(what + " answered " + answer.status() + " with a body that is not a page");
        }
        List<T> items = new ArrayList<>();
        try {
            for (JsonNode one : page.path("items")) {
                items.add(MfxJson.MAPPER.treeToValue(one, item));
            }
        } catch (IOException unreadable) {
            throw new MfxException(what + " answered " + answer.status() + " with a row that is not a "
                    + item.getSimpleName() + ": " + unreadable.getMessage(), unreadable);
        }
        JsonNode cursor = page.path("next_cursor");
        JsonNode returned = page.path("returned");
        JsonNode source = page.path("source");
        return new Page<>(items, cursor.isTextual() ? cursor.textValue() : null,
                returned.isInt() ? returned.intValue() : null, source.isTextual() ? source.textValue() : null);
    }

    /**
     * A write whose answer is one {@code type}: sent once, never retried, its key the caller's own. The
     * key is checked before anything is sent; pass null only for the writes the API takes no key on.
     */
    <T> CompletableFuture<T> write(String method, String path, List<Map.Entry<String, String>> query, @Nullable Object body,
            @Nullable String idempotencyKey, Class<T> type) {
        return write(MfxRequest.write(method, path, query, body, idempotencyKey))
                .thenApply(answer -> body(answer, type, method + " " + path));
    }

    /** A keyed write with no query whose answer is one {@code type}. */
    <T> CompletableFuture<T> write(String method, String path, @Nullable Object body, String idempotencyKey, Class<T> type) {
        return write(method, path, List.of(), body, MfxRequest.key(idempotencyKey), type);
    }

    /** A write whose answer has no body the caller needs (a 204). */
    CompletableFuture<Void> writeNothing(String method, String path, List<Map.Entry<String, String>> query, @Nullable Object body,
            @Nullable String idempotencyKey) {
        return write(MfxRequest.write(method, path, query, body, idempotencyKey)).thenApply(answer -> null);
    }

    /** One path segment, percent-encoded as every MFX SDK escapes one: a symbol such as {@code US30#} stays one segment. */
    static String segment(Object value) {
        return escape(value.toString());
    }

    /** The clock this client signs and waits by. */
    Clock clock() {
        return clock;
    }

    private static CompletableFuture<MfxAnswer> checked(MfxAnswer answer, MfxRequest request) {
        MfxApiException failure = ApiErrors.of(answer, request.method() + " " + relative(request.path()),
                retryAfter(answer.headers()));
        return failure == null ? CompletableFuture.completedFuture(answer) : CompletableFuture.failedFuture(failure);
    }

    /**
     * One attempt, signed afresh. A request that got no answer completes with
     * {@link MfxTransportException}, or {@link MfxTimeoutException} when the client's timeout ran out.
     */
    CompletableFuture<MfxAnswer> send(MfxRequest request) {
        Objects.requireNonNull(request, "request");
        String pathWithQuery = basePath + relative(request.path()) + query(request.query());
        byte[] raw = request.body();
        byte[] body = raw == null ? new byte[0] : raw;
        // The URL is sent exactly as it is signed: URI.create keeps the escaping it is given, so the two
        // cannot disagree about it, which the platform would answer with a 401 nobody can explain.
        HttpRequest.Builder message = HttpRequest.newBuilder(URI.create(origin + pathWithQuery))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT);
        for (Map.Entry<String, String> header : signer.headersFor(request.method(), pathWithQuery, body).entrySet()) {
            message.header(header.getKey(), header.getValue());
        }
        String key = request.idempotencyKey();
        if (key != null) {
            message.header("Idempotency-Key", key);
        }
        if (raw != null) {
            message.header("Content-Type", "application/json")
                    .method(request.method(), HttpRequest.BodyPublishers.ofByteArray(body));
        } else {
            message.method(request.method(), HttpRequest.BodyPublishers.noBody());
        }
        String what = request.method() + " " + relative(request.path());
        long started = System.nanoTime();
        CompletableFuture<HttpResponse<byte[]>> sent;
        try {
            sent = transport.send(message.build());
        } catch (RuntimeException refused) {
            sent = CompletableFuture.failedFuture(refused);
        }
        // The client's own timeout as well as the JDK's, so a transport the caller gave is bounded too.
        return sent.copy().orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).handle((response, failure) -> {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            if (failure != null) {
                timed(new CallTiming(what, null, elapsed));
                // Thrown as a CompletionException, the future fails with the typed error as its cause.
                throw new CompletionException(transportFailure(what, unwrap(failure)));
            }
            timed(new CallTiming(what, response.statusCode(), elapsed));
            noteBudget(response.headers());
            noteServiceAgreement(response.headers());
            byte[] got = response.body();
            return new MfxAnswer(response.statusCode(), response.headers(), got == null ? new byte[0] : got);
        });
    }

    private Throwable transportFailure(String what, Throwable failure) {
        if (failure instanceof HttpConnectTimeoutException) {
            return new MfxTimeoutException(what + " could not connect within " + timeout.toMillis() + " ms", false, failure);
        }
        if (failure instanceof HttpTimeoutException || failure instanceof TimeoutException) {
            return new MfxTimeoutException(what + " got no answer within " + timeout.toMillis() + " ms", true, failure);
        }
        if (failure instanceof ConnectException) {
            // Only a connection that never opened is known not to have sent anything.
            return new MfxTransportException(what + " got no answer: " + failure.getMessage(), false, failure);
        }
        if (failure instanceof IOException) {
            return new MfxTransportException(what + " got no answer: " + failure.getMessage(), true, failure);
        }
        return failure;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private void noteBudget(HttpHeaders headers) {
        // A header that is not a number tells us nothing; the last reading stands.
        Optional<String> remaining = headers.firstValue("X-MFX-Budget-Remaining");
        if (remaining.isEmpty()) {
            return;
        }
        try {
            long left = Long.parseLong(remaining.get().strip());
            budget.set(new Budget(left, headers.firstValue("X-MFX-Budget-Source").orElse("")));
        } catch (NumberFormatException notANumber) {
            // Not a reading: keep the last one.
        }
    }

    /**
     * One warning per client, the first time an answer says an agreement is due ({@code MFX-Service-Agreement:
     * due}), on a success or a refusal alike; never again on this client. Additions may be refused until it
     * is accepted, and nothing running is touched.
     */
    private void noteServiceAgreement(HttpHeaders headers) {
        Optional<String> value = headers.firstValue(SERVICE_AGREEMENT_HEADER);
        if (value.isEmpty() || !value.get().strip().equalsIgnoreCase("due") || !agreementWarned.compareAndSet(false, true)) {
            return;
        }
        LOG.log(System.Logger.Level.WARNING, "MFX Hub: your organisation has a Service Agreement to accept. Until an owner"
                + " or admin accepts it in the customer portal, adding terminals, attaching expert advisors, generating API"
                + " keys and asking for higher limits may be refused (ServiceAgreementNotAcceptedException); nothing"
                + " running is touched. client.tenant().serviceAgreement() says what is due.");
    }

    /**
     * Hands one attempt's timing on. Never throws: timing is an observation, not a reason for a call
     * that succeeded to fail. The file gets the status and the time alone: no path, id, header or key.
     */
    @SuppressWarnings("CatchingUnchecked") // a listener's bug must not fail the call
    private void timed(CallTiming timing) {
        Consumer<CallTiming> listener = onCall;
        if (listener != null) {
            try {
                listener.accept(timing);
            } catch (RuntimeException listenersBug) {
                // Ignored, on purpose.
            }
        }
        Path log = callLog;
        if (log != null) {
            String status = timing.status() == null ? "null" : Integer.toString(timing.status());
            String line = String.format(Locale.ROOT, "{\"status\":%s,\"ms\":%.3f}%n", status, (double) timing.elapsed().toNanos() / 1e6);
            try {
                Files.writeString(log, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException | RuntimeException unwritable) {
                // A log file that cannot be written must not fail the call.
            }
        }
    }

    /**
     * Opens the multiplexed stream, signed as a request is, over {@code GET} on the stream's path.
     * Close it with {@link MfxStream#close()}, or try-with-resources.
     *
     * @param options how the stream connects and reconnects
     * @return the open stream
     */
    public CompletableFuture<MfxStream> stream(StreamOptions options) {
        return new MfxStream(this, Objects.requireNonNull(options, "options")).open();
    }

    /**
     * Opens the multiplexed stream beside the API, reconnecting without limit.
     *
     * @return the open stream
     */
    public CompletableFuture<MfxStream> stream() {
        return stream(StreamOptions.defaults());
    }

    /** Waits as the client waits between attempts. */
    CompletableFuture<Void> pause(Duration wait) {
        return pause.pause(wait);
    }

    /**
     * Jittered exponential backoff for an attempt when the platform gave no delay: up to 1 s, 2 s, 4 s
     * and so on, never more than 30 s.
     */
    Duration backoff(int attempt) {
        double ceiling = Math.min(1000 * Math.pow(2, attempt), (double) BACKOFF_CAP.toMillis());
        return Duration.ofMillis(Math.round(random.getAsDouble() * ceiling));
    }

    /**
     * The platform's {@code Retry-After} in seconds, never below zero; null when it is absent or not a
     * number of seconds. The API writes whole seconds only, so an HTTP date is not read.
     */
    static @Nullable Duration retryAfter(HttpHeaders headers) {
        Optional<String> value = headers.firstValue("Retry-After");
        if (value.isEmpty()) {
            return null;
        }
        try {
            double seconds = Double.parseDouble(value.get().strip());
            if (!Double.isFinite(seconds)) {
                return null;
            }
            return Duration.ofMillis(Math.round(Math.max(0, seconds) * 1000));
        } catch (NumberFormatException notSeconds) {
            return null;
        }
    }

    private static String relative(String path) {
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path: a request has a path");
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String query(java.util.List<Map.Entry<String, String>> query) {
        if (query.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder("?");
        for (Map.Entry<String, String> pair : query) {
            if (text.length() > 1) {
                text.append('&');
            }
            text.append(escape(pair.getKey())).append('=').append(escape(pair.getValue()));
        }
        return text.toString();
    }

    /** RFC 3986 percent-encoding: everything but the unreserved characters, as every MFX SDK escapes a query. */
    static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~') {
                out.append(c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xf, 16)))
                        .append(Character.toUpperCase(Character.forDigit(b & 0xf, 16)));
            }
        }
        return out.toString();
    }

    /** Closes the client's own HTTP client; a transport the caller gave is the caller's to close. */
    @Override
    public void close() {
        HttpClient http = owned;
        if (http != null) {
            http.close();
        }
    }

    @Override
    public String toString() {
        return "MfxClient(" + base + ", keyId=" + keyId() + ", key=***)";
    }

    /** How an {@link MfxClient} behaves. Every setting has a default. */
    public static final class Builder {
        private final URI baseUrl;
        private final String keyId;
        private final SigningKey key;
        private Duration timeout = DEFAULT_TIMEOUT;
        private int readRetries = DEFAULT_READ_RETRIES;
        private @Nullable Clock clock;
        private @Nullable Pause pause;
        private @Nullable Transport transport;
        private @Nullable Supplier<String> nonce;
        private @Nullable DoubleSupplier random;
        private @Nullable Consumer<CallTiming> onCall;

        private Builder(URI baseUrl, String keyId, SigningKey key) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            this.keyId = Objects.requireNonNull(keyId, "keyId");
            this.key = Objects.requireNonNull(key, "key");
        }

        /**
         * The longest a single request may take.
         *
         * @param timeout 30 s by default
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout: a timeout is above zero");
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * How many times a rate-limited read is tried again.
         *
         * @param readRetries 3 by default; never below zero
         * @return this builder
         */
        public Builder readRetries(int readRetries) {
            if (readRetries < 0) {
                throw new IllegalArgumentException("readRetries: never below zero; got " + readRetries);
            }
            this.readRetries = readRetries;
            return this;
        }

        /**
         * The clock signatures are timed by.
         *
         * @param clock the system clock by default
         * @return this builder
         */
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /**
         * How the client waits between attempts.
         *
         * @param pause a real wait by default; replace it to test
         * @return this builder
         */
        public Builder pause(Pause pause) {
            this.pause = Objects.requireNonNull(pause, "pause");
            return this;
        }

        /**
         * What sends each request.
         *
         * @param transport the JDK's HTTP client by default
         * @return this builder
         */
        public Builder transport(Transport transport) {
            this.transport = Objects.requireNonNull(transport, "transport");
            return this;
        }

        /**
         * Makes each nonce.
         *
         * @param nonce 16 random bytes in base64url by default
         * @return this builder
         */
        public Builder nonce(Supplier<String> nonce) {
            this.nonce = Objects.requireNonNull(nonce, "nonce");
            return this;
        }

        /**
         * A number in [0, 1) that jitters the backoff.
         *
         * @param random random by default
         * @return this builder
         */
        public Builder random(DoubleSupplier random) {
            this.random = Objects.requireNonNull(random, "random");
            return this;
        }

        /**
         * Told about each HTTP attempt as it ends. A listener that throws does not fail the call. The
         * {@code MFX_CALL_LOG} environment variable does the same into a file, one JSON line per attempt.
         *
         * @param onCall the listener
         * @return this builder
         */
        public Builder onCall(Consumer<CallTiming> onCall) {
            this.onCall = Objects.requireNonNull(onCall, "onCall");
            return this;
        }

        /**
         * The client.
         *
         * @return a client; close it when done
         * @throws IllegalArgumentException the base URL is not an absolute http or https URL
         */
        public MfxClient build() {
            return new MfxClient(this);
        }
    }
}
