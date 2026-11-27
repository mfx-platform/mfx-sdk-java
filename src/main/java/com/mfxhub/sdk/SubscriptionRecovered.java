package com.mfxhub.sdk;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A degraded subscription that is delivering again ({@code md.subscription_recovered}). Sent once.
 * {@code reason} and {@code since} with {@code recoveredAt} are the whole gap, without the first message kept.
 *
 * @param subscriptionId the subscription
 * @param brokerId the broker
 * @param serverId the server
 * @param symbol the broker's symbol
 * @param reason what it recovered from
 * @param since when it had become degraded
 * @param recoveredAt when it recovered
 * @param lastTickAt the symbol's last tick; null when it has never ticked
 */
public record SubscriptionRecovered(UUID subscriptionId, UUID brokerId, UUID serverId, String symbol, String reason, OffsetDateTime since,
        OffsetDateTime recoveredAt, @Nullable OffsetDateTime lastTickAt) implements SubscriptionHealth {
}
