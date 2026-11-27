package com.mfxhub.sdk;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** What changes on a webhook endpoint ({@link Webhooks#update}); what is not set is left as it is. */
public final class UpdateWebhook {
    private final @Nullable String url;
    private final @Nullable List<String> eventTypes;
    private final @Nullable String description;
    private final @Nullable Boolean paused;

    private UpdateWebhook(Builder builder) {
        this.url = builder.url;
        this.eventTypes = builder.eventTypes;
        this.description = builder.description;
        this.paused = builder.paused;
    }

    /**
     * A change to build.
     *
     * @return a builder with nothing set
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nullable String url() {
        return url;
    }

    @Nullable List<String> eventTypes() {
        return eventTypes;
    }

    @Nullable String description() {
        return description;
    }

    @Nullable Boolean paused() {
        return paused;
    }

    /** Builds an {@link UpdateWebhook}. */
    public static final class Builder {
        private @Nullable String url;
        private @Nullable List<String> eventTypes;
        private @Nullable String description;
        private @Nullable Boolean paused;

        private Builder() {
        }

        /**
         * A new address.
         *
         * @param url the HTTPS address
         * @return this builder
         */
        public Builder url(String url) {
            this.url = Objects.requireNonNull(url, "url");
            return this;
        }

        /**
         * New event types, the whole list.
         *
         * @param eventTypes such as {@code position.closed}
         * @return this builder
         */
        public Builder eventTypes(List<String> eventTypes) {
            this.eventTypes = List.copyOf(eventTypes);
            return this;
        }

        /**
         * A new description.
         *
         * @param description for people
         * @return this builder
         */
        public Builder description(String description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        /**
         * Pauses the endpoint, or resumes it.
         *
         * @param paused true to pause, false to resume
         * @return this builder
         */
        public Builder paused(boolean paused) {
            this.paused = paused;
            return this;
        }

        /**
         * The change.
         *
         * @return a change that cannot be changed
         */
        public UpdateWebhook build() {
            return new UpdateWebhook(this);
        }
    }
}
