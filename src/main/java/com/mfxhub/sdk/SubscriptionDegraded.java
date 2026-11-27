package com.mfxhub.sdk;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A subscription whose symbol cannot tick, or should be ticking and is not ({@code md.subscription_degraded}).
 * Sent once, when it becomes degraded. A closed market is not a degradation, and neither is a terminal that
 * restarts and is back inside a few minutes.
 *
 * @param subscriptionId the subscription
 * @param brokerId the broker
 * @param serverId the server
 * @param symbol the broker's symbol
 * @param reason the platform's own word, text and not a closed list
 * @param since when it became degraded
 * @param lastTickAt the symbol's last tick; null when it has never ticked
 */
public record SubscriptionDegraded(UUID subscriptionId, UUID brokerId, UUID serverId, String symbol, String reason, OffsetDateTime since,
        @Nullable OffsetDateTime lastTickAt) implements SubscriptionHealth {
}
