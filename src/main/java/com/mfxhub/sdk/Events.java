package com.mfxhub.sdk;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Reads stream messages as typed events. */
public final class Events {
    private static final Map<String, Function<EventData, Event>> READERS = readers();

    /** The event types {@link #eventOf(StreamEvent)} reads. Any other type is null, never an error. */
    public static final Set<String> EVENT_KINDS = Set.copyOf(READERS.keySet());

    private Events() {
    }

    private static Map<String, Function<EventData, Event>> readers() {
        Map<String, Function<EventData, Event>> readers = new LinkedHashMap<>();
        readers.put(OrderPlaced.KIND, OrderPlaced::read);
        readers.put(OrderModified.KIND, OrderModified::read);
        readers.put(OrderClosed.KIND, OrderClosed::read);
        readers.put(PositionOpened.KIND, PositionOpened::read);
        readers.put(PositionModified.KIND, PositionModified::read);
        readers.put(PositionClosed.KIND, PositionClosed::read);
        readers.put(TerminalStateChanged.KIND, TerminalStateChanged::read);
        readers.put(CandleClosed.KIND, CandleClosed::read);
        RiskRuleEvent.KINDS.stream().sorted().forEach(kind -> readers.put(kind, RiskRuleEvent::read));
        readers.put(RiskCoverageChanged.KIND, RiskCoverageChanged::read);
        readers.put(RiskProgrammeResult.KIND, RiskProgrammeResult::read);
        readers.put(RiskPeriodClosed.KIND, RiskPeriodClosed::read);
        readers.put(CopyCopied.KIND, CopyCopied::read);
        readers.put(CopyRefused.KIND, CopyRefused::read);
        readers.put(CopyStopoutEvent.KIND, CopyStopoutEvent::read);
        readers.put(CopyDrift.KIND, CopyDrift::read);
        readers.put(CopyStateChanged.KIND, CopyStateChanged::read);
        readers.put(CopyClosedEvent.KIND, CopyClosedEvent::read);
        return Map.copyOf(readers);
    }

    /**
     * A stream message as the typed event it is, or null for a message of any other type, a resync or a
     * refusal. A candle is read by its channel: {@code candles:} messages carry the bar and no event type.
     *
     * @param received what the stream delivered
     * @return the event; null for any other message
     * @throws IllegalArgumentException a message of one of these types lacks a field the contract
     *     requires, or carries one that is not of its kind; the message names the field
     */
    public static @Nullable Event eventOf(StreamEvent received) {
        if (!(received instanceof StreamEvent.Message message)) {
            return null;
        }
        String kind = message.type();
        if (kind == null) {
            if (!message.channel().startsWith("candles:")) {
                return null;
            }
            kind = CandleClosed.KIND;
        }
        Function<EventData, Event> reader = READERS.get(kind);
        return reader == null ? null : reader.apply(new EventData(kind, message.data()));
    }
}
