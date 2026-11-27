package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

/** The parts of the copy events that need more than one field read. */
final class CopyEvents {
    private CopyEvents() {
    }

    /** A copy refusal's Risk Management detail, the contract's fields, figures as text; null when none. */
    static @Nullable RiskRefusal risk(EventData d) {
        JsonNode value = d.json("risk");
        if (value == null || !value.isObject()) {
            return null;
        }
        EventData r = new EventData("copy.refused `risk`", EventData.fields(value));
        Long version = r.integerOrNull("version");
        Long challengeVersion = r.integerOrNull("challenge_version");
        return new RiskRefusal(r.text("reason"), r.textOrNull("rule"), r.textOrNull("policy_id"), version == null ? null : Math.toIntExact(version),
                r.textOrNull("challenge_id"), challengeVersion == null ? null : Math.toIntExact(challengeVersion), r.textOrNull("used"),
                r.textOrNull("limit"), r.textOrNull("would_be"));
    }
}
