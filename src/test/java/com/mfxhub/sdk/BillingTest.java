package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mfxhub.sdk.models.BillingInvoice;
import com.mfxhub.sdk.models.OrderPlace.SideEnum;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Billing: the payment-required errors and the billing reads. */
class BillingTest {
    private static final UUID PROFILE = UUID.fromString("0191f0c2-7777-7aaa-8bbb-000000000001");
    private static final UUID INVOICE = UUID.fromString("0191f0c2-7777-7aaa-8bbb-0000000000a1");
    private static final String REFUSAL = "{\"error\":{\"code\":\"billing_payment_required\",\"message\":\"top up to continue\",\"details\":{"
            + "\"tenant_id\":\"t-1\",\"billing_profile_id\":\"" + PROFILE + "\",\"stage\":\"suspended\",\"cause\":\"balance_used_up\","
            + "\"since\":\"2026-11-15T00:00:00\",\"suspends_at\":\"2026-11-16T00:00:00+00:00\",\"pay_by\":\"\",\"action\":{\"kind\":\"top_up\","
            + "\"portal_path\":\"/billing/top-up\",\"amount_usd\":\"125.40\",\"invoice_id\":null}}}}";

    private static Fakes.Answer ok(String body) {
        return Fakes.answer(200, body);
    }

    /**
     * Given a 402 with the code billing_payment_required and its details, and a 402 with a code this version
     * does not know / When each is read / Then the first is a BillingPaymentRequiredException whose stage,
     * cause (billingCause), moments (one written without an offset read as UTC, an empty one as null) and
     * action (its portal page and exact amount) are typed from the details, and the second is a
     * PaymentRequiredException by its status; neither is retried.
     */
    @Test
    void aPaymentRequiredRefusalIsTypedByItsCodeThenItsStatus() {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(402, REFUSAL),
                Fakes.answer(402, "{\"error\":{\"code\":\"card_declined_later\",\"message\":\"pay\"}}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            BillingPaymentRequiredException billing = assertInstanceOf(BillingPaymentRequiredException.class,
                    Fakes.failure(client.terminals().create(TerminalWritesTest.BROKER, TerminalWritesTest.SERVER, "b-1")));
            Throwable other = Fakes.failure(client.terminals().get(TerminalWritesTest.ID));

            assertEquals("suspended", billing.stage());
            assertEquals("balance_used_up", billing.billingCause());
            assertNull(billing.getCause());
            assertEquals(PROFILE.toString(), billing.billingProfileId());
            assertEquals("t-1", billing.tenantId());
            assertEquals(OffsetDateTime.of(2026, 11, 15, 0, 0, 0, 0, ZoneOffset.UTC), billing.since());
            assertEquals(OffsetDateTime.of(2026, 11, 16, 0, 0, 0, 0, ZoneOffset.UTC), billing.suspendsAt());
            assertNull(billing.payBy());
            assertEquals(new PaymentAction("top_up", "/billing/top-up", new BigDecimal("125.40"), null), billing.action());
            assertEquals("125.40", billing.action().amountUsd().toPlainString());
            assertEquals("billing_payment_required (402): top up to continue", billing.toString());

            assertInstanceOf(PaymentRequiredException.class, other);
            assertFalse(other instanceof BillingPaymentRequiredException);
            assertEquals(2, transport.requests.size());
        }
    }

    /**
     * Given an order refused for billing / When it is placed / Then it completes as a refused result, never
     * exceptionally, with the error's text in its detail, and is not retryable unchanged.
     */
    @Test
    void anOrderRefusedForBillingIsARefusedResult() {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(402, REFUSAL));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            ExecutionResult result = client.execution().placeOrder(TerminalWritesTest.ID, "EURUSD", SideEnum.BUY, new BigDecimal("0.01"), "o-1").join();

            assertFalse(result.ok());
            assertEquals(Reason.REFUSED, result.reason());
            assertFalse(result.retryable());
            assertTrue(result.detail().startsWith("billing_payment_required (402): top up to continue"), result.detail());
            assertEquals(1, transport.requests.size());
        }
    }

    /**
     * Given every exception class this SDK has / When their public members are read / Then none is named
     * after a member of Throwable (cause, message, stackTrace and the like, with or without get), so a
     * platform field such as the billing cause can never be mistaken for the Java exception's own.
     */
    @Test
    void noExceptionHasAMemberNamedAfterThrowables() throws Exception {
        Set<String> reserved = new HashSet<>();
        for (Method method : Throwable.class.getMethods()) {
            String name = method.getName();
            reserved.add(name);
            if (name.startsWith("get") && name.length() > 3) {
                reserved.add(name.substring(3, 4).toLowerCase(Locale.ROOT) + name.substring(4));
            }
        }
        List<Class<?>> exceptions = new ArrayList<>();
        try (Stream<Path> classes = Files.list(Path.of("target", "classes", "com", "mfxhub", "sdk"))) {
            for (Path file : classes.filter(f -> f.getFileName().toString().endsWith("Exception.class")).toList()) {
                String name = file.getFileName().toString();
                exceptions.add(Class.forName("com.mfxhub.sdk." + name.substring(0, name.length() - ".class".length())));
            }
        }
        assertTrue(exceptions.contains(BillingPaymentRequiredException.class));
        List<String> clashes = new ArrayList<>();
        for (Class<?> type : exceptions) {
            assertTrue(Throwable.class.isAssignableFrom(type), type.getName());
            Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
                    .filter(m -> reserved.contains(m.getName()) && !overrides(m))
                    .forEach(m -> clashes.add(type.getSimpleName() + "." + m.getName()));
        }
        assertEquals(List.of(), clashes);
    }

    /** An override of Throwable's own member (toString) is that member, not a field named after it. */
    private static boolean overrides(Method method) {
        try {
            Throwable.class.getMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException none) {
            return false;
        }
    }

    /**
     * Given the price list, the summary, a profile, its forecast for a month, the prices and an invoice / When
     * each is read / Then each is one signed GET of its own path, the month as the query when given, and an
     * amount stays the exact text the API wrote.
     */
    @Test
    void theBillingReadsAreOneSignedGetEach() {
        Fakes.Recording transport = new Fakes.Recording(ok("{}"), ok("{}"), ok("{\"id\": \"" + PROFILE + "\"}"), ok("{}"), ok("{}"), ok("{}"),
                ok("{\"id\": \"" + INVOICE + "\"}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            Billing billing = client.billing();
            billing.priceList().join();
            billing.summary().join();
            billing.profile(PROFILE).join();
            billing.forecast(PROFILE, "2026-10").join();
            billing.forecast(PROFILE).join();
            billing.prices().join();
            billing.invoice(INVOICE).join();

            assertEquals(List.of("/v1/price-list", "/v1/billing", "/v1/billing/profiles/" + PROFILE, "/v1/billing/profiles/" + PROFILE
                    + "/forecast?month=2026-10", "/v1/billing/profiles/" + PROFILE + "/forecast", "/v1/billing/prices", "/v1/billing/invoices/" + INVOICE),
                    transport.requests.stream().map(Fakes.Sent::pathWithQuery).toList());
            assertTrue(transport.requests.stream().allMatch(r -> r.method().equals("GET") && r.has(Signing.SIGNATURE_HEADER)));
        }
    }

    /**
     * Given invoices on two pages, filtered by a profile and two states / When every invoice is listed / Then
     * the states go as one comma-separated value with the profile and size on every page, the list follows the
     * cursor, and each amount stays the exact text the API wrote.
     */
    @Test
    void theInvoicesAreListedByProfileAndStateFollowingTheCursor() {
        Fakes.Recording transport = new Fakes.Recording(
                ok("{\"items\": [{\"id\": \"" + INVOICE + "\", \"subtotal_usd\": \"99.90\"}], \"next_cursor\": \"i-2\", \"stats\": {}}"),
                ok("{\"items\": [{\"id\": \"" + PROFILE + "\"}], \"next_cursor\": null}"), ok("{\"items\": [], \"stats\": {}}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            ListInvoices filter = ListInvoices.builder().billingProfileId(PROFILE)
                    .state(BillingInvoice.StateEnum.DUE, BillingInvoice.StateEnum.OVERDUE).limit(1).build();
            List<UUID> ids = new ArrayList<>();
            List<String> subtotals = new ArrayList<>();
            for (BillingInvoice invoice : client.billing().invoices(filter)) {
                ids.add(invoice.getId());
                subtotals.add(invoice.getSubtotalUsd());
            }
            client.billing().invoicesPage().join();

            assertEquals(List.of(INVOICE, PROFILE), ids);
            String query = "limit=1&billing_profile_id=" + PROFILE + "&state=due%2Coverdue";
            assertEquals(List.of("/v1/billing/invoices?" + query, "/v1/billing/invoices?cursor=i-2&" + query, "/v1/billing/invoices"),
                    transport.requests.stream().map(Fakes.Sent::pathWithQuery).toList());
            assertEquals("99.90", subtotals.get(0));
        }
    }
}
