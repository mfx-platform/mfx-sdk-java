package com.mfxhub.sdk;

import com.mfxhub.sdk.models.TenantFarm;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * The farms this tenant's plan places terminals on ({@code client.farms()}). Each says its operating
 * system, region, the usages it offers, its refresh rate and its instrument limits. Any other farm,
 * another tenant's included, is 404.
 */
public final class Farms {
    private final MfxClient client;

    Farms(MfxClient client) {
        this.client = client;
    }

    /**
     * One page of farms.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param limit the page size; the platform's default when null
     * @return the page
     */
    public CompletableFuture<Page<TenantFarm>> page(@Nullable String cursor, @Nullable Integer limit) {
        return client.page("/farms", Query.page(cursor, limit).build(), TenantFarm.class);
    }

    /**
     * The first page of farms.
     *
     * @return the page
     */
    public CompletableFuture<Page<TenantFarm>> page() {
        return page(null, null);
    }

    /**
     * Every farm, following the cursor. Iterating blocks on each page's request.
     *
     * @param limit the page size; the platform's default when null
     * @return every farm
     */
    public Iterable<TenantFarm> list(@Nullable Integer limit) {
        return Walk.of(cursor -> page(cursor, limit));
    }

    /**
     * Every farm, in pages of the platform's default size.
     *
     * @return every farm
     */
    public Iterable<TenantFarm> list() {
        return list(null);
    }

    /**
     * One farm the plan allows.
     *
     * @param farmId the farm
     * @return the farm
     */
    public CompletableFuture<TenantFarm> get(UUID farmId) {
        return client.get("/farms/" + Objects.requireNonNull(farmId, "farmId"), TenantFarm.class);
    }
}
