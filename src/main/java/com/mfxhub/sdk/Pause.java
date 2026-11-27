package com.mfxhub.sdk;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** How the client waits between attempts. Replace it to test, or to wait on your own scheduler. */
@FunctionalInterface
public interface Pause {
    /**
     * Waits {@code wait}, without holding a thread.
     *
     * @param wait how long
     * @return completes when the wait is over
     */
    CompletableFuture<Void> pause(Duration wait);

    /**
     * The client's own: the common pool's delayed executor.
     *
     * @return a pause that really waits
     */
    static Pause delayed() {
        return wait -> CompletableFuture.runAsync(() -> { },
                CompletableFuture.delayedExecutor(Math.max(0, wait.toMillis()), TimeUnit.MILLISECONDS));
    }
}
