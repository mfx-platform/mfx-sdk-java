package com.mfxhub.sdk;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A page of a copy list and its {@code stats} row. {@code stats} counts everything the filters (and, on
 * the log, the window) match, not this page, and is the same on every page; null when the answer carries
 * none. No total, ever.
 *
 * @param <T> what the list holds
 * @param <S> the contract's stats row for the list
 * @param items what is in this page
 * @param nextCursor the cursor for the next page; null on the last
 * @param returned how many are in this page, as the platform said
 * @param stats the stats row, over the whole filtered set
 */
public record CopyPage<T, S>(List<T> items, @Nullable String nextCursor, @Nullable Integer returned, @Nullable S stats)
        implements Iterable<T> {
    /** The page, its items copied and an empty cursor read as none. */
    public CopyPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        nextCursor = nextCursor == null || nextCursor.isEmpty() ? null : nextCursor;
    }

    /**
     * How many are in this page.
     *
     * @return the count of {@link #items()}
     */
    public int size() {
        return items.size();
    }

    /**
     * This page's items, in order. Only this page: the list's own iterable follows the cursor.
     *
     * @return the items
     */
    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }

    /** The page without its stats, for the cursor walk. */
    Page<T> page() {
        return new Page<>(items, nextCursor, returned, null);
    }
}
