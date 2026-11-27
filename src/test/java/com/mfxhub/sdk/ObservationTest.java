package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * What the client tells its caller about the platform's answers: the key's budget, and the timing of
 * every attempt, without either ever failing a call.
 */
class ObservationTest {

    /**
     * Given answers carrying the key's budget, one whose budget is not a number, and one with none / When
     * the client reads them / Then the budget is null before the first answer, the platform's reading
     * after it, and the last good reading when a later answer says nothing usable; each attempt is told
     * to the listener with its status, a listener that throws fails nothing, and a retried read is two
     * attempts.
     */
    @Test
    void theBudgetAndEachAttemptAreReportedWithoutFailingACall() {
        List<CallTiming> told = new ArrayList<>();
        Fakes.Recording transport = new Fakes.Recording(
                Fakes.answer(200, "{}", "X-MFX-Budget-Remaining", "4980", "X-MFX-Budget-Source", "counted"),
                Fakes.answer(200, "{}", "X-MFX-Budget-Remaining", "plenty", "X-MFX-Budget-Source", "counted"),
                Fakes.answer(429, "{}"),
                Fakes.answer(200, "{}"));
        try (MfxClient client = MfxClient.builder(Fakes.BASE, "mfxk_1", Fakes.key())
                .transport(transport)
                .pause(wait -> CompletableFuture.completedFuture(null))
                .onCall(timing -> {
                    told.add(timing);
                    throw new IllegalStateException("a listener's bug");
                })
                .build()) {
            assertNull(client.budget());
            client.read(MfxRequest.get("/terminals")).join();
            assertEquals(new Budget(4980, "counted"), client.budget());
            client.read(MfxRequest.get("/farms")).join();
            client.read(MfxRequest.get("/usage")).join();

            assertEquals(new Budget(4980, "counted"), client.budget());
            assertEquals(List.of("GET /terminals 200", "GET /farms 200", "GET /usage 429", "GET /usage 200"),
                    told.stream().map(t -> t.what() + " " + t.status()).toList());
            told.forEach(t -> assertFalse(t.elapsed().isNegative()));
        }
    }
}
