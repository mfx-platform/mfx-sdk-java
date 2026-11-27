package com.mfxhub.sdk;

/**
 * What the stream missed and how often it had to come back. A snapshot; it never changes under the caller.
 *
 * @param reconnects connections opened after the first
 * @param dropped messages the sequence numbers prove were not delivered, over every channel
 * @param resyncs channels told to resync because the replay window had passed
 * @param slowConsumerDrops times the platform closed the stream for falling behind (4008)
 * @param lifetimeReconnects times the connection's lifetime was reached (4009). Expected, not a fault
 * @param refused channels the platform refused
 */
public record StreamStats(int reconnects, long dropped, int resyncs, int slowConsumerDrops, int lifetimeReconnects, int refused) {
    /** Nothing missed yet. */
    public static final StreamStats NONE = new StreamStats(0, 0, 0, 0, 0, 0);

    StreamStats withReconnect() {
        return new StreamStats(reconnects + 1, dropped, resyncs, slowConsumerDrops, lifetimeReconnects, refused);
    }

    StreamStats withDropped(long more) {
        return new StreamStats(reconnects, dropped + more, resyncs, slowConsumerDrops, lifetimeReconnects, refused);
    }

    StreamStats withResync() {
        return new StreamStats(reconnects, dropped, resyncs + 1, slowConsumerDrops, lifetimeReconnects, refused);
    }

    StreamStats withSlowConsumerDrop() {
        return new StreamStats(reconnects, dropped, resyncs, slowConsumerDrops + 1, lifetimeReconnects, refused);
    }

    StreamStats withLifetimeReconnect() {
        return new StreamStats(reconnects, dropped, resyncs, slowConsumerDrops, lifetimeReconnects + 1, refused);
    }

    StreamStats withRefused() {
        return new StreamStats(reconnects, dropped, resyncs, slowConsumerDrops, lifetimeReconnects, refused + 1);
    }
}
