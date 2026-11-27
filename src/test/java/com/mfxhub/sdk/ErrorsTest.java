package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Every failure a call can raise is typed by the platform's {@code error.code}, then by the HTTP status,
 * exactly as the shared error table (spec/surface.json) maps them.
 */
class ErrorsTest {

    /** An error name in spec/surface.json as this SDK's: {@code FooError} is {@code FooException}. */
    private static String java(String python) {
        return SurfaceTest.javaError(python);
    }

    private static MfxAnswer answer(int status, String body) {
        return new MfxAnswer(status, HttpHeaders.of(Map.of(), (n, v) -> true), body.getBytes(StandardCharsets.UTF_8));
    }

    private static @Nullable MfxApiException read(int status, String body, @Nullable Duration retryAfter) {
        return ApiErrors.of(answer(status, body), "GET /x", retryAfter);
    }

    /**
     * Given the shared error table (spec/surface.json) / When this SDK's mapping is read /
     * Then every code and every status maps to the same error, by its Java name, and there is no code or
     * status the table does not map.
     */
    @Test
    void everyCodeAndStatusMapsToTheErrorTheReferenceSdkNames() throws Exception {
        JsonNode reference = MfxJson.MAPPER.readTree(Files.readString(Path.of("spec/surface.json"))).get("errors");
        // An error the surface map holds as pending is not mapped yet, by the pull request it names.
        JsonNode pending = MfxJson.MAPPER.readTree(Files.readString(Path.of("tools/surface-check/map.json"))).path("pending");
        Map<String, String> byCode = new TreeMap<>();
        Map<Integer, String> byStatus = new TreeMap<>();
        reference.forEach(entry -> {
            if (pending.has("errors." + entry.get("error").asText())) {
                return;
            }
            if (entry.has("code")) {
                byCode.put(entry.get("code").asText(), java(entry.get("error").asText()));
            } else {
                byStatus.put(entry.get("status").asInt(), java(entry.get("error").asText()));
            }
        });
        Map<String, String> javaByCode = new TreeMap<>();
        ApiErrors.BY_CODE.forEach((code, kind) -> javaByCode.put(code, kind.type().getSimpleName()));
        Map<Integer, String> javaByStatus = new TreeMap<>();
        ApiErrors.BY_STATUS.forEach((status, kind) -> javaByStatus.put(status, kind.type().getSimpleName()));

        assertFalse(byCode.isEmpty());
        assertEquals(byCode, javaByCode);
        assertEquals(byStatus, javaByStatus);
        ApiErrors.BY_CODE.forEach((code, kind) -> assertSame(kind.type(), ApiErrors.of(code, "m", 499, null, null).getClass()));
    }

    /**
     * Given failed answers with a known code, an unknown code, no code, and no JSON at all / When each is
     * read / Then the code decides, the status decides only when the code is unknown, neither gives the
     * base error, a missing code reads as the error's name, a missing message is the call that failed,
     * the details are kept read-only, and Retry-After is carried.
     */
    @Test
    void aFailedAnswerIsTypedByItsCodeThenItsStatus() {
        MfxApiException upstream = read(503, "{\"error\":{\"code\":\"upstream_error\",\"message\":\"the terminal saw no tick"
                + " within 4 s\",\"details\":{\"reason\":\"no_tick\",\"age_s\":4}}}", Duration.ofSeconds(3));
        assertInstanceOf(UpstreamException.class, upstream);
        assertEquals("the terminal saw no tick within 4 s", upstream.getMessage());
        assertEquals("upstream_error (503): the terminal saw no tick within 4 s", upstream.toString());
        assertEquals("no_tick", upstream.details().get("reason").asText());
        assertEquals(4, upstream.details().get("age_s").asInt());
        assertThrows(UnsupportedOperationException.class, () -> upstream.details().clear());
        assertEquals(Duration.ofSeconds(3), upstream.retryAfter());

        MfxApiException later = read(409, "{\"error\":{\"code\":\"some_code_from_later\",\"message\":\"new\"}}", null);
        assertInstanceOf(ConflictException.class, later);
        assertEquals("some_code_from_later", later.code());

        MfxApiException unknown = read(418, "{\"error\":{\"code\":\"teapot\",\"message\":\"short and stout\"}}", null);
        assertSame(MfxApiException.class, unknown.getClass());

        MfxApiException edge = ApiErrors.of(answer(502, "<html>bad gateway</html>"), "GET /terminals", null);
        assertInstanceOf(EdgeException.class, edge);
        assertInstanceOf(UpstreamException.class, edge);
        assertEquals("EdgeException", edge.code());
        assertEquals("GET /terminals failed", edge.getMessage());
        assertTrue(edge.details().isEmpty());

        assertNull(read(200, "{}", null));
        assertNull(read(304, "", null));
    }

    /**
     * Given the errors whose details carry more (a suspended tenant, a maintenance window, a broker that
     * is not supported, a risk refusal) / When their details are read / Then each reads its own fields,
     * and a field that is absent or of the wrong kind is null, never an exception.
     */
    @Test
    void anErrorReadsTheFieldsItsDetailsCarry() {
        TenantSuspendedException suspended = assertInstanceOf(TenantSuspendedException.class, detailed("tenant_suspended",
                "{\"since\":\"2026-11-15T08:00:00Z\",\"reads_until\":\"2026-11-16T08:00:00+04:00\"}"));
        assertEquals(OffsetDateTime.of(2026, 11, 15, 8, 0, 0, 0, ZoneOffset.UTC), suspended.since());
        assertEquals(OffsetDateTime.of(2026, 11, 16, 4, 0, 0, 0, ZoneOffset.UTC).toInstant(), suspended.readsUntil().toInstant());
        assertNull(assertInstanceOf(TenantDisabledException.class, detailed("tenant_disabled", "{\"since\":42}")).since());

        MaintenanceException window = assertInstanceOf(MaintenanceException.class, detailed("maintenance",
                "{\"window_id\":\"w-1\",\"window\":\"MW-0031\",\"ends_at\":\"2026-11-15T02:38:00Z\","
                        + "\"service\":\"order_placement\",\"suspended\":[\"order_placement\",7,\"md\"]}"));
        assertEquals(List.of("w-1", "MW-0031", "order_placement"), List.of(window.windowId(), window.window(), window.service()));
        assertEquals(OffsetDateTime.of(2026, 11, 15, 2, 38, 0, 0, ZoneOffset.UTC), window.endsAt());
        assertEquals(List.of("order_placement", "md"), window.suspended());
        assertTrue(assertInstanceOf(MaintenanceException.class, detailed("maintenance", "{}")).suspended().isEmpty());

        UnsupportedBrokerException broker = assertInstanceOf(UnsupportedBrokerException.class, detailed("broker_not_supported",
                "{\"refused_by\":\"server_disabled\",\"reason\":\"r\",\"broker_id\":\"b-1\",\"server_id\":\"s-1\"}"));
        assertEquals(List.of("server_disabled", "r", "b-1", "s-1"),
                List.of(broker.refusedBy(), broker.reason(), broker.brokerId(), broker.serverId()));

        RiskRefusedException risk = assertInstanceOf(RiskRefusedException.class, detailed("risk_refused",
                "{\"rule\":\"max_daily_loss\",\"policy_id\":\"p-1\",\"version\":\"3\",\"used\":\"412.50\",\"limit\":500,"
                        + "\"would_be\":\"\",\"challenge_version\":true}"));
        assertEquals(new RiskRefusal("risk_refused", "max_daily_loss", "p-1", 3, null, null, "412.50", "500", null), risk.refusal());
        assertInstanceOf(ValidationException.class, risk);
    }

    private static MfxApiException detailed(String code, String details) {
        MfxApiException read = read(403, "{\"error\":{\"code\":\"" + code + "\",\"message\":\"m\",\"details\":" + details + "}}", null);
        if (read == null) {
            throw new AssertionError("no error");
        }
        return read;
    }

    /**
     * Given a request whose connection never opens, one whose connection drops, and ones that get no
     * answer in time / When each is sent / Then a connection that never opened is a transport error that
     * cannot have reached the platform, a dropped one or a timeout may have, and the client's own timeout
     * bounds a transport that never answers.
     */
    @Test
    void aRequestWithNoAnswerSaysWhetherItCouldHaveArrived() {
        MfxTransportException refused = assertInstanceOf(MfxTransportException.class,
                Fakes.failure(over(Fakes.failing(new ConnectException("refused")), Duration.ofSeconds(5)).read(MfxRequest.get("/terminals"))));
        assertFalse(refused.mayHaveReached());
        assertTrue(refused.getMessage().contains("GET /terminals"));

        MfxTransportException dropped = assertInstanceOf(MfxTransportException.class,
                Fakes.failure(over(Fakes.failing(new java.io.IOException("ended")), Duration.ofSeconds(5)).write(
                        new MfxRequest("POST", "/terminals", List.of(), "{}".getBytes(StandardCharsets.UTF_8), null))));
        assertTrue(dropped.mayHaveReached());

        MfxTimeoutException connecting = assertInstanceOf(MfxTimeoutException.class,
                Fakes.failure(over(Fakes.failing(new HttpConnectTimeoutException("slow")), Duration.ofSeconds(5)).read(MfxRequest.get("/x"))));
        assertFalse(connecting.mayHaveReached());

        MfxTimeoutException slow = assertInstanceOf(MfxTimeoutException.class,
                Fakes.failure(over(Fakes.hanging(), Duration.ofMillis(50)).read(MfxRequest.get("/terminals"))));
        assertTrue(slow.mayHaveReached());
    }

    private static MfxClient over(Fakes.Answer answer,
            Duration timeout) {
        return MfxClient.builder(Fakes.BASE, "mfxk_1", Fakes.key()).transport(new Fakes.Recording(answer)).timeout(timeout).build();
    }
}
