package com.mfxhub.sdk;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Which terminals {@link Terminals#bulkRestart(String, String, BulkRestart)} restarts: those on a farm,
 * at a broker, or listed, at least one of the three.
 */
public final class BulkRestart {
    private final @Nullable UUID farmId;
    private final @Nullable UUID brokerId;
    private final @Nullable List<UUID> terminalIds;
    private final boolean confirmOpenPositions;

    private BulkRestart(Builder builder) {
        this.farmId = builder.farmId;
        this.brokerId = builder.brokerId;
        this.terminalIds = builder.terminalIds;
        this.confirmOpenPositions = builder.confirmOpenPositions;
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

    @Nullable UUID brokerId() {
        return brokerId;
    }

    @Nullable List<UUID> terminalIds() {
        return terminalIds;
    }

    boolean confirmOpenPositions() {
        return confirmOpenPositions;
    }

    /** Builds a {@link BulkRestart}. */
    public static final class Builder {
        private @Nullable UUID farmId;
        private @Nullable UUID brokerId;
        private @Nullable List<UUID> terminalIds;
        private boolean confirmOpenPositions;

        private Builder() {
        }

        /**
         * Your terminals on this farm.
         *
         * @param farmId the farm
         * @return this builder
         */
        public Builder farmId(UUID farmId) {
            this.farmId = Objects.requireNonNull(farmId, "farmId");
            return this;
        }

        /**
         * Your terminals at this broker.
         *
         * @param brokerId the broker
         * @return this builder
         */
        public Builder brokerId(UUID brokerId) {
            this.brokerId = Objects.requireNonNull(brokerId, "brokerId");
            return this;
        }

        /**
         * These terminals, at most 1000.
         *
         * @param terminalIds the terminals
         * @return this builder
         */
        public Builder terminalIds(List<UUID> terminalIds) {
            this.terminalIds = List.copyOf(terminalIds);
            return this;
        }

        /**
         * Restarts a terminal with positions open too; without it, such a terminal is refused
         * ({@code positions_open}) and the rest go ahead.
         *
         * @param confirmOpenPositions false unless set
         * @return this builder
         */
        public Builder confirmOpenPositions(boolean confirmOpenPositions) {
            this.confirmOpenPositions = confirmOpenPositions;
            return this;
        }

        /**
         * The options.
         *
         * @return options that cannot be changed
         */
        public BulkRestart build() {
            return new BulkRestart(this);
        }
    }
}
