package com.mfxhub.sdk;

import org.jspecify.annotations.Nullable;

/**
 * The expert wrote faster than its records could be delivered and the oldest waiting files were discarded
 * ({@code expert_advisor.telemetry_lost}). Trading is never held for telemetry.
 *
 * @param channel the {@code ea:} channel
 * @param seq its sequence number
 * @param segmentFrom the first file discarded
 * @param segmentTo the last file discarded
 * @param records how many records they held
 */
public record EaTelemetryLost(String channel, @Nullable Long seq, long segmentFrom, long segmentTo, long records) implements EaMessage {
}
