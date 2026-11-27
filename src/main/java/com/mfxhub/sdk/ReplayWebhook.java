package com.mfxhub.sdk;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What {@link Webhooks#replay(UUID, ReplayWebhook, String)} sends again: every event after a stream
 * cursor, or one failure. Exactly one of the two, by construction.
 */
public final class ReplayWebhook {
    private final @Nullable String after;
    private final @Nullable UUID failureId;

    private ReplayWebhook(@Nullable String after, @Nullable UUID failureId) {
        this.after = after;
        this.failureId = failureId;
    }

    /**
     * Every event after this one.
     *
     * @param after an {@code MFX-Event-Id}, the last event received
     * @return the replay
     */
    public static ReplayWebhook after(String after) {
        return new ReplayWebhook(Objects.requireNonNull(after, "after"), null);
    }

    /**
     * One failure, sent again.
     *
     * @param failureId the failure
     * @return the replay
     */
    public static ReplayWebhook failureId(UUID failureId) {
        return new ReplayWebhook(null, Objects.requireNonNull(failureId, "failureId"));
    }

    @Nullable String after() {
        return after;
    }

    @Nullable UUID failureId() {
        return failureId;
    }
}
