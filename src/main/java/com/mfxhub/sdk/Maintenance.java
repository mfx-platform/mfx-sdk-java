package com.mfxhub.sdk;

import com.mfxhub.sdk.models.MaintenanceWindowChanged;
import com.mfxhub.sdk.models.TenantMaintenance;
import com.mfxhub.sdk.models.TenantMaintenanceTerminal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Planned maintenance on this tenant's terminals ({@code client.maintenance()}). While a window runs,
 * what it suspends for you is refused with 403 {@code maintenance} ({@link MaintenanceException}, or an
 * execution result with reason {@code maintenance}) until {@code ends_at}. These reads say what is
 * scheduled and in progress and where each of your terminals is, and are answered under every
 * suspension, so a caller that has just been refused can always ask how long for. Open positions are
 * never closed by a window.
 */
public final class Maintenance {
    /** The message on {@code events}, and the webhook event type, that says a window reached you or moved. */
    public static final String WINDOW_CHANGED = "maintenance.window_changed";

    private final MfxClient client;

    Maintenance(MfxClient client) {
        this.client = client;
    }

    /**
     * A message from {@code events} as the window change it is, or null for any other. It is sent when
     * a window is announced to you, starts, has its expected end or its message moved, ends or is
     * cancelled; {@code change} says which, and the rest is the window as it stands after the change. On
     * the stream every value arrives as text, so {@code terminals} is a count written as digits.
     *
     * @param received what the stream delivered
     * @return the change; null for any other event
     */
    public static @Nullable MaintenanceWindowChanged windowEvent(StreamEvent received) {
        if (!(received instanceof StreamEvent.Message message) || !WINDOW_CHANGED.equals(message.type())) {
            return null;
        }
        return MfxJson.MAPPER.convertValue(message.data(), MaintenanceWindowChanged.class);
    }

    /**
     * One page of windows. Unfiltered: scheduled and in progress, soonest first; {@code completed} lists
     * the last 30 days, and a scheduled window is listed once it has been announced to you.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the states and the page size
     * @return the page
     */
    public CompletableFuture<Page<TenantMaintenance>> page(@Nullable String cursor, ListMaintenance filter) {
        return client.page("/maintenance", Objects.requireNonNull(filter, "filter").query(cursor), TenantMaintenance.class);
    }

    /**
     * The first page of windows, scheduled and in progress.
     *
     * @return the page
     */
    public CompletableFuture<Page<TenantMaintenance>> page() {
        return page(null, ListMaintenance.all());
    }

    /**
     * Every window, following the cursor. Iterating blocks on each page's request.
     *
     * @param filter the states and the page size
     * @return every window
     */
    public Iterable<TenantMaintenance> list(ListMaintenance filter) {
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> page(cursor, filter));
    }

    /**
     * Every window, scheduled and in progress.
     *
     * @return every window
     */
    public Iterable<TenantMaintenance> list() {
        return list(ListMaintenance.all());
    }

    /**
     * One page of your terminals in one window, each with its state and expected time back. A window you
     * are not in, or another tenant's, is 404.
     *
     * @param windowId the window
     * @param cursor the cursor of the page before; the first page when null
     * @param limit the page size; the platform's default when null
     * @return the page
     */
    public CompletableFuture<Page<TenantMaintenanceTerminal>> terminalsPage(UUID windowId, @Nullable String cursor,
            @Nullable Integer limit) {
        String path = "/maintenance/" + Objects.requireNonNull(windowId, "windowId") + "/terminals";
        return client.page(path, Query.page(cursor, limit).build(), TenantMaintenanceTerminal.class);
    }

    /**
     * Every one of your terminals in the window, following the cursor.
     *
     * @param windowId the window
     * @param limit the page size; the platform's default when null
     * @return every terminal in it
     */
    public Iterable<TenantMaintenanceTerminal> terminals(UUID windowId, @Nullable Integer limit) {
        Objects.requireNonNull(windowId, "windowId");
        return Walk.of(cursor -> terminalsPage(windowId, cursor, limit));
    }

    /**
     * Every one of your terminals in the window, in pages of the platform's default size.
     *
     * @param windowId the window
     * @return every terminal in it
     */
    public Iterable<TenantMaintenanceTerminal> terminals(UUID windowId) {
        return terminals(windowId, null);
    }
}
