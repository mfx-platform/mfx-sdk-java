package com.mfxhub.sdk;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A market-data subscription's health, as {@link MarketData#subscriptionEvent} reads it from {@code events}:
 * {@link SubscriptionDegraded} or {@link SubscriptionRecovered}.
 */
public sealed interface SubscriptionHealth permits SubscriptionDegraded, SubscriptionRecovered {
    /**
     * The subscription.
     *
     * @return its id
     */
    UUID subscriptionId();

    /**
     * The platform's own word for why: text, not a closed list ({@code symbol_removed},
     * {@code subscription_refused}, {@code terminal_disconnected}, {@code report_stale},
     * {@code no_ticks_in_session} today).
     *
     * @return the reason
     */
    String reason();

    /**
     * The symbol's last tick.
     *
     * @return the moment; null when it has never ticked
     */
    @Nullable OffsetDateTime lastTickAt();
}
