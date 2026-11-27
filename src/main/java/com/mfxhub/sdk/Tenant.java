package com.mfxhub.sdk;

import com.mfxhub.sdk.models.OwnContacts;
import com.mfxhub.sdk.models.TenantServiceAgreement;
import java.util.concurrent.CompletableFuture;

/** What the tenant reads about itself ({@code client.tenant()}). */
public final class Tenant {
    private final MfxClient client;

    Tenant(MfxClient client) {
        this.client = client;
    }

    /**
     * The tenant's contacts and their roles, as an operator set them (an expert advisor trial's code goes
     * to the representative). Read-only for a tenant.
     *
     * @return the contacts
     */
    public CompletableFuture<OwnContacts> contacts() {
        return client.get("/tenant/contacts", OwnContacts.class);
    }

    /**
     * What the organisation must still accept before it can add terminals, expert advisors or API keys: a
     * plan change awaiting acceptance of the new plan's Service Agreement, or a version of a Service
     * Agreement, the Terms of Service or the MetaTrader 5 licence. Any key may read it, whatever its scopes.
     * {@code held} is true while additions are refused ({@link ServiceAgreementNotAcceptedException} or
     * {@link TermsNotAcceptedException}); reads, orders and anything running never are.
     *
     * @return what is due, the plan change and where an owner or admin accepts
     */
    public CompletableFuture<TenantServiceAgreement> serviceAgreement() {
        return client.get("/tenant/service-agreement", TenantServiceAgreement.class);
    }
}
