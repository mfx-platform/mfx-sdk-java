package com.mfxhub.sdk;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * What clears a billing standing: the contract's {@code BillingAction}, named {@code PaymentAction} here
 * because the generated model {@code com.mfxhub.sdk.models.BillingAction} has that name.
 *
 * @param kind {@code top_up} or {@code pay_invoice}
 * @param portalPath the portal page that does it, relative to the portal's address ({@code /billing/top-up}):
 *     join it to the portal's address to give a person a link
 * @param amountUsd at least what clears it, exact; null when the platform did not say
 * @param invoiceId the invoice to pay; null for a top-up
 */
public record PaymentAction(String kind, String portalPath, @Nullable BigDecimal amountUsd, @Nullable String invoiceId) {
}
