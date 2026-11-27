package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One deal a command made, as the venue reported it (cTrader): two for an emulated close-by, one for an
 * order filled at once.
 *
 * @param deal the deal number
 * @param order its order
 * @param position its position
 * @param volumeUnits in units of the instrument, not lots
 * @param price the fill price
 */
public record VenueDeal(long deal, @Nullable Long order, @Nullable Long position, @Nullable BigDecimal volumeUnits,
        @Nullable BigDecimal price) {

    /** One deal; anything without a deal number reads as none. */
    static @Nullable VenueDeal of(@Nullable JsonNode raw) {
        Long deal = raw == null || !raw.isObject() ? null : Results.whole(raw.get("deal"));
        if (raw == null || deal == null) {
            return null;
        }
        return new VenueDeal(deal, Results.whole(raw.get("order")), Results.whole(raw.get("position")),
                Results.decimal(raw.get("volume_units")), Results.decimal(raw.get("price")));
    }

    static List<VenueDeal> list(@Nullable JsonNode raw) {
        List<VenueDeal> deals = new ArrayList<>();
        if (raw != null && raw.isArray()) {
            for (JsonNode one : raw) {
                VenueDeal deal = of(one);
                if (deal != null) {
                    deals.add(deal);
                }
            }
        }
        return List.copyOf(deals);
    }
}
