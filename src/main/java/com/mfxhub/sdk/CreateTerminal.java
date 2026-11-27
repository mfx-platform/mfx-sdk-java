package com.mfxhub.sdk;

import com.mfxhub.sdk.models.TerminalCreate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The optional arguments of {@link Terminals#create(UUID, UUID, String, CreateTerminal)}. What is not set
 * is left out of the request, and the platform's default applies: the farm it chooses, no streaming, no
 * execution, no instruments, no labels, no redundancy.
 *
 * <pre>{@code
 * CreateTerminal.builder().execution(true).instruments(List.of("EURUSD.sd")).label("desk", "fx").build()
 * }</pre>
 */
public final class CreateTerminal {
    private static final CreateTerminal DEFAULTS = builder().build();

    private final @Nullable UUID farmId;
    private final @Nullable Boolean streaming;
    private final @Nullable Boolean execution;
    private final @Nullable List<String> instruments;
    private final @Nullable Map<String, String> labels;
    private final TerminalCreate.@Nullable RedundancyEnum redundancy;

    private CreateTerminal(Builder builder) {
        this.farmId = builder.farmId;
        this.streaming = builder.streaming;
        this.execution = builder.execution;
        this.instruments = builder.instruments == null ? null : List.copyOf(builder.instruments);
        this.labels = builder.labels == null ? null : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(builder.labels));
        this.redundancy = builder.redundancy;
    }

    /**
     * Nothing set.
     *
     * @return the platform's defaults
     */
    public static CreateTerminal defaults() {
        return DEFAULTS;
    }

    /**
     * Options to build.
     *
     * @return a builder with nothing set
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nullable UUID farmId() {
        return farmId;
    }

    @Nullable Boolean streaming() {
        return streaming;
    }

    @Nullable Boolean execution() {
        return execution;
    }

    @Nullable List<String> instruments() {
        return instruments;
    }

    @Nullable Map<String, String> labels() {
        return labels;
    }

    TerminalCreate.@Nullable RedundancyEnum redundancy() {
        return redundancy;
    }

    /** Builds a {@link CreateTerminal}. */
    public static final class Builder {
        private @Nullable UUID farmId;
        private @Nullable Boolean streaming;
        private @Nullable Boolean execution;
        private @Nullable List<String> instruments;
        private @Nullable Map<String, String> labels;
        private TerminalCreate.@Nullable RedundancyEnum redundancy;

        private Builder() {
        }

        /**
         * The farm to place it on, deliberately; checked rather than trusted. Left out, the platform
         * chooses ({@code client.farms().list()} shows what it could choose from).
         *
         * @param farmId the farm
         * @return this builder
         */
        public Builder farmId(UUID farmId) {
            this.farmId = Objects.requireNonNull(farmId, "farmId");
            return this;
        }

        /**
         * Whether it streams quotes.
         *
         * @param streaming false unless set
         * @return this builder
         */
        public Builder streaming(boolean streaming) {
            this.streaming = streaming;
            return this;
        }

        /**
         * Whether it executes orders.
         *
         * @param execution false unless set
         * @return this builder
         */
        public Builder execution(boolean execution) {
            this.execution = execution;
            return this;
        }

        /**
         * The instruments, in the broker's own names ({@code EURUSD.sd}).
         *
         * @param instruments the whole list
         * @return this builder
         */
        public Builder instruments(List<String> instruments) {
            this.instruments = List.copyOf(instruments);
            return this;
        }

        /**
         * The labels, keys to values.
         *
         * @param labels every label
         * @return this builder
         */
        public Builder labels(Map<String, String> labels) {
            this.labels = new LinkedHashMap<>(labels);
            return this;
        }

        /**
         * One label.
         *
         * @param key its key
         * @param value its value
         * @return this builder
         */
        public Builder label(String key, String value) {
            if (labels == null) {
                labels = new LinkedHashMap<>();
            }
            labels.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Its redundancy.
         *
         * @param redundancy {@code none} unless set
         * @return this builder
         */
        public Builder redundancy(TerminalCreate.RedundancyEnum redundancy) {
            this.redundancy = Objects.requireNonNull(redundancy, "redundancy");
            return this;
        }

        /**
         * The options.
         *
         * @return options that cannot be changed
         */
        public CreateTerminal build() {
            return new CreateTerminal(this);
        }
    }
}
