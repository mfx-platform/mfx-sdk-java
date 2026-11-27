package com.mfxhub.sdk;

import org.jspecify.annotations.Nullable;

/** A message from an {@code ea:} channel ({@link ExpertAdvisors#telemetryOf}): a batch, or a loss. */
public sealed interface EaMessage permits EaTelemetry, EaTelemetryLost {
    /**
     * The channel it came on.
     *
     * @return the {@code ea:} channel
     */
    String channel();

    /**
     * Its sequence number on the channel.
     *
     * @return the number; null when it carries none
     */
    @Nullable Long seq();
}
