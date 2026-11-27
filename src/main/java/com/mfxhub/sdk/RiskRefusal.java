package com.mfxhub.sdk;

import org.jspecify.annotations.Nullable;

/**
 * What a Risk Management refusal names.
 *
 * @param reason the refusal's reason, {@code risk_refused} when the platform named none
 * @param rule the rule that refused the entry
 * @param policyId the policy whose rule refused it; null when a challenge's rule did
 * @param version the policy's version
 * @param challengeId the challenge whose rule refused it; null when a policy's rule did
 * @param challengeVersion the challenge's version
 * @param used the figure used so far, in the account's currency, as the platform wrote it
 * @param limit the rule's limit, as the platform wrote it
 * @param wouldBe what the figure would have been, as the platform wrote it
 */
public record RiskRefusal(
        String reason, @Nullable String rule, @Nullable String policyId, @Nullable Integer version,
        @Nullable String challengeId, @Nullable Integer challengeVersion, @Nullable String used,
        @Nullable String limit, @Nullable String wouldBe) {
}
