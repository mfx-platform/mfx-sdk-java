package com.mfxhub.sdk;

/**
 * A stream message read as the typed event it is ({@link Events#eventOf(StreamEvent)}). Each is a record
 * whose components are the contract's fields in Java's names; decimals are exact {@link java.math.BigDecimal}s.
 * Switch on it with a pattern: the set of types is closed for this version, and a type the platform adds
 * later reads as null, never as an error.
 */
public sealed interface Event permits OrderPlaced, OrderModified, OrderClosed, PositionOpened, PositionModified, PositionClosed,
        TerminalStateChanged, CandleClosed, RiskRuleEvent, RiskCoverageChanged, RiskProgrammeResult, RiskPeriodClosed,
        CopyCopied, CopyRefused, CopyStopoutEvent, CopyDrift, CopyStateChanged, CopyClosedEvent {
    /**
     * The event type it was read from, such as {@code position.opened}.
     *
     * @return the type
     */
    String kind();
}
