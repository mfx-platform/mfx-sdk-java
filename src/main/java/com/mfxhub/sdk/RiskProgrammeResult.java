package com.mfxhub.sdk;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code risk.programme_result}: a challenge account passed, failed or ended. Once only.
 *
 * @param schemaVersion the event's schema version
 * @param challengeId the challenge
 * @param version the challenge's version the account ran
 * @param accountId the account
 * @param result {@code passed}, {@code failed} or {@code ended}
 * @param rule the rule it failed on; null on a pass or an end
 * @param at when
 */
public record RiskProgrammeResult(String schemaVersion, UUID challengeId, long version, UUID accountId, String result, @Nullable String rule, OffsetDateTime at) implements Event {
    /** The event type it is read from. */
    public static final String KIND = "risk.programme_result";

    @Override
    public String kind() {
        return KIND;
    }

    static RiskProgrammeResult read(EventData d) {
        return new RiskProgrammeResult(d.text("schema_version"), d.uuid("challenge_id"), d.integer("version"), d.uuid("account_id"), d.text("result"), d.blankNull("rule"), d.instant("at"));
    }
}
