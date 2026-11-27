package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Prices, volumes and amounts are exact: a decimal on the wire is a JSON string, read and written
 * without passing through binary floating point. Core proves this rule here; the shared suite's decimals
 * cases go through trading methods and are claimed in Enriched.
 */
class DecimalsTest {
    record Quote(BigDecimal bid, @Nullable BigDecimal ask) {
    }

    static Stream<String> exact() {
        return Stream.of("1.10450", "0.21", "-0.5", "1.10", "12345678901234567.123456789");
    }

    /**
     * Given prices written as JSON strings, with trailing zeros, a sign, and more digits than a double
     * holds / When they are read and written back / Then each is the exact decimal, its scale kept, and it
     * goes back out as the same string.
     */
    @ParameterizedTest
    @MethodSource("exact")
    void aDecimalStringReadsExactlyAndWritesBackTheSame(String text) throws Exception {
        Quote quote = MfxJson.MAPPER.readValue("{\"bid\":\"" + text + "\",\"ask\":null}", Quote.class);

        assertEquals(new BigDecimal(text), quote.bid());
        assertEquals(text, quote.bid().toPlainString());
        assertNull(quote.ask());
        assertEquals("{\"bid\":\"" + text + "\"}", MfxJson.MAPPER.writeValueAsString(quote));
    }

    static Stream<String> refused() {
        return Stream.of("0.21000000000000002", "1", "\"1e5\"", "\"1,000.5\"", "\" 1.5\"", "\"\"", "true", "\"+1\"");
    }

    /**
     * Given a price written as a JSON number, and strings that are not plain decimals (an exponent, a
     * separator, spaces, empty, a plus sign) / When they are read / Then each is refused, because a
     * number token has already been through binary floating point.
     */
    @ParameterizedTest
    @MethodSource("refused")
    void aDecimalThatIsNotAPlainStringIsRefused(String token) {
        assertThrows(JsonMappingException.class, () -> MfxJson.MAPPER.readValue("{\"bid\":" + token + "}", Quote.class));
    }

    /**
     * Given the arithmetic a double gets wrong, and a decimal whose plain form a double would print with
     * an exponent / When the same values are carried as decimals / Then 0.1 + 0.2 is 0.3 exactly, the sum
     * goes out as "0.3", and a tiny or huge decimal goes out plainly, never with an exponent.
     */
    @Test
    void decimalArithmeticStaysExactWhereADoubleDoesNot() throws Exception {
        Quote quote = MfxJson.MAPPER.readValue("{\"bid\":\"0.1\",\"ask\":\"0.2\"}", Quote.class);

        assertNotEquals(0.3, 0.1 + 0.2);
        assertEquals(new BigDecimal("0.3"), quote.bid().add(quote.ask()));
        assertEquals("\"0.3\"", MfxJson.MAPPER.writeValueAsString(quote.bid().add(quote.ask())));
        assertEquals("\"0.00000001\"", MfxJson.MAPPER.writeValueAsString(new BigDecimal("1E-8")));
        assertEquals("\"120000000\"", MfxJson.MAPPER.writeValueAsString(new BigDecimal("1.2E+8")));
        assertEquals(new BigDecimal("1.10"), MfxJson.decimal("1.10"));
        assertThrows(IllegalArgumentException.class, () -> MfxJson.decimal("1e5"));
    }
}
