package com.mfxhub.sdk;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * One HTTP attempt: what was asked, the status, and how long it took. Each attempt is a call: a read
 * retried after a 429 is two.
 *
 * @param what the call, as {@code METHOD /path}, without its query
 * @param status the HTTP status; null when no answer came
 * @param elapsed how long it took, on the client's own monotonic clock
 */
public record CallTiming(String what, @Nullable Integer status, Duration elapsed) {
}
