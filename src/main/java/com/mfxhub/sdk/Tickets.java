package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

/**
 * The terminal relates one order, one deal and one position per fill; null where it has none yet.
 *
 * @param order the order ticket
 * @param deal the deal ticket
 * @param position the position ticket: what closePosition and modifyPosition take
 */
public record Tickets(@Nullable Long order, @Nullable Long deal, @Nullable Long position) {
    /** No tickets. */
    public static final Tickets NONE = new Tickets(null, null, null);

    /** Tickets from the API's object; anything else reads as no tickets. */
    static Tickets of(@Nullable JsonNode raw) {
        if (raw == null || !raw.isObject()) {
            return NONE;
        }
        return new Tickets(Results.whole(raw.get("order")), Results.whole(raw.get("deal")), Results.whole(raw.get("position")));
    }
}
