package com.mfxhub.sdk;

import com.mfxhub.sdk.models.BillingForecast;
import com.mfxhub.sdk.models.BillingInvoice;
import com.mfxhub.sdk.models.BillingInvoiceDetail;
import com.mfxhub.sdk.models.BillingInvoicePage;
import com.mfxhub.sdk.models.BillingPriceList;
import com.mfxhub.sdk.models.BillingProfile;
import com.mfxhub.sdk.models.BillingSummary;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Billing, read only ({@code client.billing()}; {@code billing:read}, and {@link #priceList()} needs no
 * scope): the standing, the profiles, the forecast, the prices and the invoices. Never refused for billing:
 * a suspended profile still reads its own standing. Amounts stay the exact decimal text the API wrote.
 */
public final class Billing {
    private final MfxClient client;

    Billing(MfxClient client) {
        this.client = client;
    }

    /**
     * The public price list: the plans, every item with its monthly price, and {@code next}, the following
     * list once it is released. It needs no key; the SDK signs it as it signs every request, which the
     * platform accepts and ignores.
     *
     * @return the price list
     */
    public CompletableFuture<BillingPriceList> priceList() {
        return client.get("/price-list", BillingPriceList.class);
    }

    /**
     * The organisation's billing: every billing profile with its standing, and {@code standing} at the top,
     * the most urgent of theirs. Named {@code summary} because it is an overview of every
     * profile, not one object, which {@link #profile(UUID)} reads in full.
     *
     * @return the summary
     */
    public CompletableFuture<BillingSummary> summary() {
        return client.get("/billing", BillingSummary.class);
    }

    /**
     * One billing profile: its balance, today's daily charge, month to date and standing.
     *
     * @param profileId the profile
     * @return the profile
     */
    public CompletableFuture<BillingProfile> profile(UUID profileId) {
        return client.get("/billing/profiles/" + Objects.requireNonNull(profileId, "profileId"), BillingProfile.class);
    }

    /**
     * This month for one billing profile, to date by product and forecast to month end.
     *
     * @param profileId the profile
     * @return the forecast
     */
    public CompletableFuture<BillingForecast> forecast(UUID profileId) {
        return forecast(profileId, null);
    }

    /**
     * A month for one billing profile, to date by product and forecast to month end; a past month gives
     * its final figures.
     *
     * @param profileId the profile
     * @param month {@code YYYY-MM}; this month when null
     * @return the forecast
     */
    public CompletableFuture<BillingForecast> forecast(UUID profileId, @Nullable String month) {
        return client.get("/billing/profiles/" + Objects.requireNonNull(profileId, "profileId") + "/forecast",
                Query.of().add("month", month).build(), BillingForecast.class);
    }

    /**
     * The prices your organisation pays, in the price list's shape: your Enterprise agreement's
     * ({@code scope} {@code tenant}) when one is in force, otherwise the list in force.
     *
     * @return the prices
     */
    public CompletableFuture<BillingPriceList> prices() {
        return client.get("/billing/prices", BillingPriceList.class);
    }

    /**
     * One invoice, with its summary by product, tax, what is matched and how to pay.
     *
     * @param invoiceId the invoice
     * @return the invoice
     */
    public CompletableFuture<BillingInvoiceDetail> invoice(UUID invoiceId) {
        return client.get("/billing/invoices/" + Objects.requireNonNull(invoiceId, "invoiceId"), BillingInvoiceDetail.class);
    }

    /**
     * One page of invoices, newest first, across your billing profiles, with its stats.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the profile, states and page size
     * @return the page
     */
    public CompletableFuture<BillingInvoicePage> invoicesPage(@Nullable String cursor, ListInvoices filter) {
        return client.get("/billing/invoices", Objects.requireNonNull(filter, "filter").query(cursor), BillingInvoicePage.class);
    }

    /**
     * The first page of invoices.
     *
     * @return the page
     */
    public CompletableFuture<BillingInvoicePage> invoicesPage() {
        return invoicesPage(null, ListInvoices.all());
    }

    /**
     * Every invoice, newest first, following the cursor; narrowed as {@link #invoicesPage(String, ListInvoices)}
     * is. The filter's limit is the page size, not a total. Iterating blocks on each page.
     *
     * @param filter the profile, states and page size
     * @return every invoice
     */
    public Iterable<BillingInvoice> invoices(ListInvoices filter) {
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> invoicesPage(cursor, filter)
                .thenApply(p -> new Page<>(p.getItems() == null ? List.of() : p.getItems(), p.getNextCursor(), p.getReturned(), null)));
    }

    /**
     * Every invoice.
     *
     * @return every invoice
     */
    public Iterable<BillingInvoice> invoices() {
        return invoices(ListInvoices.all());
    }
}
