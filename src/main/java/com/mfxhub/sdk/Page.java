package com.mfxhub.sdk;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One page of a list, and the cursor for the next. Every list in the API is cursor-paginated and has no
 * total, no page number and no offset, so a page never says how many there are in all.
 *
 * <p>Iterate it, or read {@link #items()}. To walk every page, pass {@link #nextCursor()} back as the
 * cursor, or use the method that does it for you ({@link Terminals#list(ListTerminals)}).
 *
 * @param <T> what the list holds
 * @param items what is in this page
 * @param nextCursor the cursor for the next page; null on the last (an empty one reads as null)
 * @param returned how many are in THIS page, as the platform said; null when it did not say. Not a
 *     total, and never to be shown as one
 * @param source where the rows came from, on the lists that say ({@code live}, {@code last_known});
 *     null on every other
 */
public record Page<T>(List<T> items, @Nullable String nextCursor, @Nullable Integer returned, @Nullable String source)
        implements Iterable<T> {
    /** The page, its items copied and an empty cursor or source read as none. */
    public Page {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        // An empty cursor would otherwise read as a cursor and fetch the first page again for ever.
        nextCursor = nextCursor == null || nextCursor.isEmpty() ? null : nextCursor;
        source = source == null || source.isEmpty() ? null : source;
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
     * The item at {@code index} in this page.
     *
     * @param index its place in this page, from 0
     * @return the item
     */
    public T get(int index) {
        return items.get(index);
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
}
