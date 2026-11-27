package com.mfxhub.sdk;

import java.net.URI;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** How a stream behaves. Every setting has a default. */
public final class StreamOptions {
    private static final StreamOptions DEFAULTS = builder().build();

    private final @Nullable StreamConnector connect;
    private final @Nullable URI url;
    private final @Nullable Integer maxReconnects;

    private StreamOptions(Builder builder) {
        this.connect = builder.connect;
        this.url = builder.url;
        this.maxReconnects = builder.maxReconnects;
    }

    /**
     * Every default.
     *
     * @return the defaults
     */
    public static StreamOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Options to build.
     *
     * @return a builder with every default
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * How the connection is opened.
     *
     * @return the connector; null for the JDK's WebSocket
     */
    public @Nullable StreamConnector connect() {
        return connect;
    }

    /**
     * The stream's URL.
     *
     * @return the URL; null to derive it from the client's base URL ({@code api.} becomes {@code ws.})
     */
    public @Nullable URI url() {
        return url;
    }

    /**
     * The most connections opened after the first.
     *
     * @return the most; null for unlimited
     */
    public @Nullable Integer maxReconnects() {
        return maxReconnects;
    }

    /** Builds {@link StreamOptions}. */
    public static final class Builder {
        private @Nullable StreamConnector connect;
        private @Nullable URI url;
        private @Nullable Integer maxReconnects;

        private Builder() {
        }

        /**
         * How the connection is opened.
         *
         * @param connect the connector; the JDK's WebSocket by default
         * @return this builder
         */
        public Builder connect(StreamConnector connect) {
            this.connect = Objects.requireNonNull(connect, "connect");
            return this;
        }

        /**
         * The stream's URL.
         *
         * @param url derived from the client's base URL by default
         * @return this builder
         */
        public Builder url(URI url) {
            this.url = Objects.requireNonNull(url, "url");
            return this;
        }

        /**
         * The most connections opened after the first.
         *
         * @param maxReconnects unlimited by default; never below zero
         * @return this builder
         */
        public Builder maxReconnects(int maxReconnects) {
            if (maxReconnects < 0) {
                throw new IllegalArgumentException("maxReconnects: never below zero; got " + maxReconnects);
            }
            this.maxReconnects = maxReconnects;
            return this;
        }

        /**
         * The options.
         *
         * @return options that cannot be changed
         */
        public StreamOptions build() {
            return new StreamOptions(this);
        }
    }
}
