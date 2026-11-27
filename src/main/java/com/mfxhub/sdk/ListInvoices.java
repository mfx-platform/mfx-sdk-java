package com.mfxhub.sdk;

import com.mfxhub.sdk.models.BillingInvoice;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Which invoices {@link Billing#invoicesPage(String, ListInvoices)} and {@link Billing#invoices(ListInvoices)} list, newest first, across your billing profiles. States go as one comma-separated value.
 */
public final class ListInvoices {
    private static final ListInvoices ALL = builder().build();

    private final @Nullable Integer limit;
    private final @Nullable UUID billingProfileId;
    private final Set<BillingInvoice.StateEnum> state;

    private ListInvoices(Builder builder) {
        this.limit = builder.limit;
        this.billingProfileId = builder.billingProfileId;
        this.state = Collections.unmodifiableSet(new LinkedHashSet<>(builder.state));
    }

    /**
     * Nothing set: the platform's defaults, and this SDK's own where it has one.
     *
     * @return no filter
     */
    public static ListInvoices all() {
        return ALL;
    }

    /**
     * A filter to build.
     *
     * @return a builder with nothing set
     */
    public static Builder builder() {
        return new Builder();
    }

    /** The query, the cursor first. */
    List<Map.Entry<String, String>> query(@Nullable String cursor) {
        return Query.of().add("cursor", cursor).add("limit", limit).add("billing_profile_id", billingProfileId).joined("state", state).build();
    }

    /** Builds a {@link ListInvoices}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private @Nullable UUID billingProfileId;
        private final Set<BillingInvoice.StateEnum> state = new LinkedHashSet<>();

        private Builder() {
        }

        /**
         * How many a page may hold, up to 200; 50 unless set.
         *
         * @param limit the value
         * @return this builder
         */
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * One billing profile's invoices.
         *
         * @param billingProfileId the value
         * @return this builder
         */
        public Builder billingProfileId(UUID billingProfileId) {
            this.billingProfileId = Objects.requireNonNull(billingProfileId, "billingProfileId");
            return this;
        }

        /**
         * The states to list.
         *
         * @param values one or several
         * @return this builder
         */
        public Builder state(BillingInvoice.StateEnum... values) {
            for (BillingInvoice.StateEnum one : values) {
                state.add(Objects.requireNonNull(one, "state"));
            }
            return this;
        }

        /**
         * The filter.
         *
         * @return a filter that cannot be changed
         */
        public ListInvoices build() {
            return new ListInvoices(this);
        }
    }
}
