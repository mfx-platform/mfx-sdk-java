package com.mfxhub.sdk;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A batch of records, in the order written ({@code expert_advisor.telemetry}).
 *
 * @param channel the {@code ea:} channel
 * @param seq its sequence number
 * @param records the records
 * @param skipped lines the platform could not read, since the message before
 * @param dropped records the platform left out, since the message before
 */
public record EaTelemetry(String channel, @Nullable Long seq, List<EaRecord> records, long skipped, long dropped) implements EaMessage {
    /** The batch, its records copied so they cannot be changed. */
    public EaTelemetry {
        records = List.copyOf(records);
    }
}
