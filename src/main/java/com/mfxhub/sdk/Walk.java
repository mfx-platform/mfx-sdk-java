package com.mfxhub.sdk;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Every item of a list, following the cursor until it ends. There is no total and no page number
 * anywhere in the API, so this follows the cursor rather than counting.
 *
 * <p>Each page is asked for when the one before has been read, one request a page. Iterating blocks on
 * each page's request: run it where blocking is fine, such as a virtual thread. A failure is thrown from
 * {@code hasNext()} as the {@link MfxException} it is, not wrapped. Each iteration starts again from the
 * first page.
 */
final class Walk<T> implements Iterable<T> {
    private final Function<@Nullable String, CompletableFuture<Page<T>>> page;

    private Walk(Function<@Nullable String, CompletableFuture<Page<T>>> page) {
        this.page = page;
    }

    /** The walk over {@code page}, which is handed each cursor, null for the first page. */
    static <T> Iterable<T> of(Function<@Nullable String, CompletableFuture<Page<T>>> page) {
        return new Walk<>(page);
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private @Nullable Iterator<T> current;
            private @Nullable String cursor;
            private boolean last;

            @Override
            public boolean hasNext() {
                while (current == null || !current.hasNext()) {
                    if (last) {
                        return false;
                    }
                    Page<T> read;
                    try {
                        read = page.apply(cursor).join();
                    } catch (CompletionException failed) {
                        if (failed.getCause() instanceof RuntimeException cause) {
                            throw cause;
                        }
                        throw failed;
                    }
                    current = read.iterator();
                    cursor = read.nextCursor();
                    last = cursor == null;
                }
                return true;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("every item has been listed");
                }
                return Objects.requireNonNull(current).next();
            }
        };
    }
}
