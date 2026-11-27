package com.mfxhub.sdk;

import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One legal document version the organisation must still accept, as a {@code terms_not_accepted} or
 * {@code service_agreement_not_accepted} refusal names it in {@code details.documents}.
 *
 * @param document the document's key: {@code terms}, {@code mt5_licence}, {@code service_agreement_<plan>}
 *     and others later; a key this version does not know is one more document, by its name
 * @param number the version due
 * @param plan the plan a Service Agreement belongs to; null for a document common to every plan
 * @param graceEndsAt when its grace period ends or ended; null for a plan change, which waits for
 *     acceptance instead
 */
public record DueDocument(String document, int number, @Nullable String plan, @Nullable OffsetDateTime graceEndsAt) {
}
