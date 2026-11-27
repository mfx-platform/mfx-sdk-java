package com.mfxhub.sdk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code risk.rule_warning}, {@code risk.rule_breached} or {@code risk.rule_cleared}: a rule of a policy
 * ({@code policyId}) or of a challenge ({@code challengeId}) reached its warning or action level, or
 * cleared. Figures stay exact, in the account's currency.
 *
 * @param kind which of the three event types it was read from
 * @param schemaVersion the event's schema version
 * @param accountId the account
 * @param rule the rule
 * @param policyId the policy whose rule it is; null for a challenge's
 * @param challengeId the challenge whose rule it is; null for a policy's
 * @param version the policy's or challenge's version
 * @param used the figure used so far
 * @param limit the rule's limit
 * @param level {@code warn}, {@code act} or {@code cleared}
 * @param actions what the rule authorised: {@code notify}, {@code block_entries}, {@code cancel_pending},
 *     {@code close_positions}
 * @param clearedBy {@code reset}, {@code override} or {@code below_warn} on a clear; null otherwise
 * @param at when
 */
public record RiskRuleEvent(String kind, String schemaVersion, UUID accountId, String rule, @Nullable UUID policyId,
        @Nullable UUID challengeId, @Nullable Long version, @Nullable BigDecimal used, @Nullable BigDecimal limit, String level,
        List<String> actions, @Nullable String clearedBy, OffsetDateTime at) implements Event {
    /** The three event types it is read from. */
    public static final Set<String> KINDS = Set.of("risk.rule_warning", "risk.rule_breached", "risk.rule_cleared");

    /** The event, its actions copied so they cannot be changed. */
    public RiskRuleEvent {
        actions = List.copyOf(actions);
    }

    static RiskRuleEvent read(EventData d) {
        return new RiskRuleEvent(d.kind(), d.text("schema_version"), d.uuid("account_id"), d.text("rule"), d.blankUuid("policy_id"),
                d.blankUuid("challenge_id"), d.blankInteger("version"), d.blankDecimal("used"), d.blankDecimal("limit"), d.text("level"),
                d.words("actions"), d.blankNull("cleared_by"), d.instant("at"));
    }
}
