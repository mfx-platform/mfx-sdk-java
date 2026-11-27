package com.mfxhub.sdk;

import com.mfxhub.sdk.models.Usage;
import java.util.concurrent.CompletableFuture;

/**
 * Every limit that applies to this key and its tenant, and how much is used ({@code client.usage()}).
 * Each limit says its figure, where it comes from (the plan, the tenant's own figure, or this key's cap),
 * whether the platform applies it yet, and how much of it is in use where that is measured. {@code key}
 * is the calling key's view and {@code tenant} the ceiling for all its keys together. The remaining
 * budget still rides every answer and is on {@link MfxClient#budget()}.
 */
public final class UsageReader {
    private final MfxClient client;

    UsageReader(MfxClient client) {
        this.client = client;
    }

    /**
     * Every limit for this key and its tenant: figure, source, whether applied, and use.
     *
     * @return the usage
     */
    public CompletableFuture<Usage> get() {
        return client.get("/usage", Usage.class);
    }
}
