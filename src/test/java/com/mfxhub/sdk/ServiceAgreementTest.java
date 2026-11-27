package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mfxhub.sdk.models.PlanChangeSummary;
import com.mfxhub.sdk.models.TenantServiceAgreement;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Plan agreements: the two 409 refusals typed, the
 * tenant's read of what is due, and the one warning a client gives when the platform says an agreement is due.
 */
class ServiceAgreementTest {
    private static final UUID CHANGE = UUID.fromString("0191f0c2-5555-7aaa-8bbb-0000000000c1");
    private static final String ACCEPT = "https://portal.mfxhub.com/organisation/legal";
    private static final String HELD = "{\"error\":{\"code\":\"service_agreement_not_accepted\",\"message\":\"accept the Enterprise"
            + " Service Agreement first\",\"details\":{\"plan_change\":{\"id\":\"" + CHANGE + "\",\"from\":\"standard\",\"to\":\"enterprise\","
            + "\"state\":\"awaiting_acceptance\",\"requested_at\":\"2026-11-17T08:00:00Z\",\"until\":\"2026-12-01T08:00:00Z\","
            + "\"requested_by_operator\":true},\"documents\":[{\"document\":\"service_agreement_enterprise\",\"number\":2,"
            + "\"plan\":\"enterprise\",\"grace_ends_at\":null},{\"document\":\"terms\",\"number\":4,\"plan\":null,"
            + "\"grace_ends_at\":\"2026-11-10T00:00:00Z\"}],\"accept_url\":\"" + ACCEPT + "\"}}}";
    private static final String TERMS = "{\"error\":{\"code\":\"terms_not_accepted\",\"message\":\"accept the Terms first\",\"details\":"
            + "{\"documents\":[{\"document\":\"mt5_licence\",\"number\":3,\"grace_ends_at\":\"2026-11-12T00:00:00Z\"}]}}}";
    private static final String DUE = "{\"due\":true,\"held\":true,\"plan\":\"standard\",\"plan_change\":{\"id\":\"" + CHANGE + "\","
            + "\"from\":\"standard\",\"to\":\"enterprise\",\"state\":\"awaiting_acceptance\",\"requested_at\":\"2026-11-17T08:00:00Z\","
            + "\"until\":\"2026-12-01T08:00:00Z\",\"requested_by_operator\":true},\"documents\":[{\"document\":\"service_agreement_enterprise\","
            + "\"number\":2,\"plan\":\"enterprise\",\"grace_ends_at\":null,\"held\":true}],\"accept_url\":\"" + ACCEPT + "\"}";

    /** Records what the SDK's logger says, for as long as it is open. */
    private static final class Heard extends Handler implements AutoCloseable {
        private final Logger logger = Logger.getLogger(MfxClient.LOGGER_NAME);
        final List<LogRecord> records = new ArrayList<>();

        Heard() {
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord logged) {
            records.add(logged);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
        }
    }

    /**
     * Given a 409 service_agreement_not_accepted carrying a plan change, two documents and where to accept
     * / When adding a terminal is refused with it / Then it is a ServiceAgreementNotAcceptedException (a
     * ConflictException), its plan change, documents and accept URL typed from the details, its cause
     * never shadowed, and it is not retried.
     */
    @Test
    void aServiceAgreementRefusalIsTypedWithItsPlanChangeDocumentsAndAcceptUrl() {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(409, HELD));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            ServiceAgreementNotAcceptedException held = assertInstanceOf(ServiceAgreementNotAcceptedException.class,
                    Fakes.failure(client.terminals().create(TerminalWritesTest.BROKER, TerminalWritesTest.SERVER, "t-1")));

            assertInstanceOf(ConflictException.class, held);
            assertEquals("service_agreement_not_accepted", held.code());
            assertEquals(409, held.status());
            assertNull(held.getCause());
            PlanChangeSummary change = held.planChange();
            assertEquals(CHANGE, change.getId());
            assertEquals("standard", change.getFrom());
            assertEquals("enterprise", change.getTo());
            assertEquals(PlanChangeSummary.StateEnum.AWAITING_ACCEPTANCE, change.getState());
            assertEquals(OffsetDateTime.of(2026, 12, 1, 8, 0, 0, 0, ZoneOffset.UTC), change.getUntil());
            assertTrue(change.getRequestedByOperator());
            assertEquals(List.of(new DueDocument("service_agreement_enterprise", 2, "enterprise", null),
                    new DueDocument("terms", 4, null, OffsetDateTime.of(2026, 11, 10, 0, 0, 0, 0, ZoneOffset.UTC))), held.documents());
            assertEquals(URI.create(ACCEPT), held.acceptUrl());
            assertEquals(1, transport.requests.size());
        }
    }

    /**
     * Given a service_agreement_not_accepted with no plan change (a version past its grace period), and one
     * with no details at all / When each is read / Then the plan change is null, the documents are those
     * named or none, and the accept URL is null where the platform gave none: nothing read fails.
     */
    @Test
    void aServiceAgreementRefusalWithoutAPlanChangeOrDetailsReadsAsNothing() {
        Fakes.Recording transport = new Fakes.Recording(
                Fakes.answer(409, "{\"error\":{\"code\":\"service_agreement_not_accepted\",\"message\":\"m\",\"details\":{\"plan_change\":null,"
                        + "\"documents\":[{\"document\":\"service_agreement_standard\",\"number\":3,\"plan\":\"standard\","
                        + "\"grace_ends_at\":\"2026-11-14T00:00:00\"}],\"accept_url\":\"" + ACCEPT + "\"}}}"),
                Fakes.answer(409, "{\"error\":{\"code\":\"service_agreement_not_accepted\",\"message\":\"m\"}}"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            ServiceAgreementNotAcceptedException version = assertInstanceOf(ServiceAgreementNotAcceptedException.class,
                    Fakes.failure(client.terminals().create(TerminalWritesTest.BROKER, TerminalWritesTest.SERVER, "t-1")));
            ServiceAgreementNotAcceptedException bare = assertInstanceOf(ServiceAgreementNotAcceptedException.class,
                    Fakes.failure(client.terminals().create(TerminalWritesTest.BROKER, TerminalWritesTest.SERVER, "t-2")));

            assertNull(version.planChange());
            assertEquals(List.of(new DueDocument("service_agreement_standard", 3, "standard",
                    OffsetDateTime.of(2026, 11, 14, 0, 0, 0, 0, ZoneOffset.UTC))), version.documents());
            assertNull(bare.planChange());
            assertEquals(List.of(), bare.documents());
            assertNull(bare.acceptUrl());
        }
    }

    /**
     * Given a 409 terms_not_accepted naming the document whose grace period ended / When adding a terminal
     * is refused with it / Then it is a TermsNotAcceptedException (a ConflictException, no longer the bare
     * one) with its documents typed.
     */
    @Test
    void aTermsRefusalIsTypedWithItsDocuments() {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(409, TERMS));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            TermsNotAcceptedException terms = assertInstanceOf(TermsNotAcceptedException.class,
                    Fakes.failure(client.terminals().create(TerminalWritesTest.BROKER, TerminalWritesTest.SERVER, "t-1")));

            assertInstanceOf(ConflictException.class, terms);
            assertEquals("terms_not_accepted", terms.code());
            assertEquals(List.of(new DueDocument("mt5_licence", 3, null, OffsetDateTime.of(2026, 11, 12, 0, 0, 0, 0, ZoneOffset.UTC))),
                    terms.documents());
            assertEquals(1, transport.requests.size());
        }
    }

    /**
     * Given an organisation whose plan change awaits acceptance / When the tenant reads its service
     * agreement / Then it is one GET of /tenant/service-agreement, typed as the contract's
     * TenantServiceAgreement: due, held, the plan in force, the change, the documents and where to accept.
     */
    @Test
    void theTenantReadsWhatItMustAccept() {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(200, DUE, "MFX-Service-Agreement", "due"));
        try (MfxClient client = TerminalWritesTest.client(transport)) {
            TenantServiceAgreement agreement = client.tenant().serviceAgreement().join();

            assertEquals("GET", transport.requests.get(0).method());
            assertEquals("/v1/tenant/service-agreement", transport.requests.get(0).pathWithQuery());
            assertTrue(agreement.getDue());
            assertTrue(agreement.getHeld());
            assertEquals("standard", agreement.getPlan());
            assertEquals(CHANGE, agreement.getPlanChange().getId());
            assertEquals("service_agreement_enterprise", agreement.getDocuments().get(0).getDocument());
            assertNull(agreement.getDocuments().get(0).getGraceEndsAt());
            assertEquals(URI.create(ACCEPT), agreement.getAcceptUrl());
        }
    }

    /**
     * Given answers that carry MFX-Service-Agreement: due, on a success and on a refusal / When one client
     * gets three of them, and a second client one / Then each client logs exactly one warning, the first
     * time, on the SDK's logger, naming the read that says what is due; never again on that client.
     */
    @Test
    void aDueAgreementIsWarnedOncePerClient() {
        Fakes.Recording first = new Fakes.Recording(Fakes.answer(200, "{}", "MFX-Service-Agreement", "due"),
                Fakes.answer(409, HELD, "MFX-Service-Agreement", "due"), Fakes.answer(200, DUE, "mfx-service-agreement", "due"));
        Fakes.Recording second = new Fakes.Recording(Fakes.answer(200, "{}", "MFX-Service-Agreement", "due"));
        try (Heard heard = new Heard()) {
            try (MfxClient client = TerminalWritesTest.client(first)) {
                client.usage().get().join();
                Fakes.failure(client.terminals().create(TerminalWritesTest.BROKER, TerminalWritesTest.SERVER, "t-1"));
                client.tenant().serviceAgreement().join();
            }
            assertEquals(1, heard.records.size());
            LogRecord warning = heard.records.get(0);
            assertEquals(Level.WARNING, warning.getLevel());
            assertTrue(warning.getMessage().contains("Service Agreement"), warning.getMessage());
            assertTrue(warning.getMessage().contains("tenant().serviceAgreement()"), warning.getMessage());

            try (MfxClient other = TerminalWritesTest.client(second)) {
                other.usage().get().join();
            }
            assertEquals(2, heard.records.size());
        }
    }

    /**
     * Given answers with no MFX-Service-Agreement header, or one whose value is not due / When a client
     * gets them / Then nothing is logged.
     */
    @Test
    void noWarningWithoutTheHeader() {
        Fakes.Recording transport = new Fakes.Recording(Fakes.answer(200, "{}"), Fakes.answer(200, "{}", "MFX-Service-Agreement", "none"));
        try (Heard heard = new Heard(); MfxClient client = TerminalWritesTest.client(transport)) {
            client.usage().get().join();
            client.usage().get().join();

            assertEquals(List.of(), heard.records);
        }
    }
}
