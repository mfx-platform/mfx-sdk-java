package com.mfxhub.sdk;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Which of a tenant's servers and account types on a broker is meant, for the catalogue's symbol reads.
 * What is not named is left out of the request altogether, never sent empty.
 *
 * <pre>{@code
 * WhichServer.builder().serverId(serverId).accountType("ecn").build()
 * }</pre>
 */
public final class WhichServer {
    private static final WhichServer ANY = builder().build();

    private final @Nullable UUID serverId;
    private final @Nullable String accountType;

    private WhichServer(Builder builder) {
        this.serverId = builder.serverId;
        this.accountType = builder.accountType;
    }

    /**
     * Nothing named: every server and account type of the tenant's.
     *
     * @return no narrowing
     */
    public static WhichServer any() {
        return ANY;
    }

    /**
     * Which to name.
     *
     * @return a builder with nothing set
     */
    public static Builder builder() {
        return new Builder();
    }

    Query query() {
        return Query.of().add("server_id", serverId).add("account_type", accountType);
    }

    /** Builds a {@link WhichServer}. */
    public static final class Builder {
        private @Nullable UUID serverId;
        private @Nullable String accountType;

        private Builder() {
        }

        /**
         * The broker's server.
         *
         * @param serverId the server
         * @return this builder
         */
        public Builder serverId(UUID serverId) {
            this.serverId = Objects.requireNonNull(serverId, "serverId");
            return this;
        }

        /**
         * The account type, as the broker names it.
         *
         * @param accountType the account type
         * @return this builder
         */
        public Builder accountType(String accountType) {
            this.accountType = Objects.requireNonNull(accountType, "accountType");
            return this;
        }

        /**
         * The choice.
         *
         * @return a choice that cannot be changed
         */
        public WhichServer build() {
            return new WhichServer(this);
        }
    }
}
