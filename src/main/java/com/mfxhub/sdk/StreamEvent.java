package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** What the stream delivers: a message, a resync, or one channel's refusal. */
public sealed interface StreamEvent permits StreamEvent.Message, StreamEvent.Resync, StreamEvent.ChannelRefused {
    /**
     * The channel it concerns.
     *
     * @return the channel key
     */
    String channel();

    /**
     * One message on one channel.
     *
     * <p>There is deliberately no "replayed" flag: a resume replays messages the caller never saw and the
     * protocol marks no end to the replay, so {@link #seq()} is the ground truth to reconcile against.
     *
     * @param channel the channel it arrived on
     * @param seq its sequence number on the channel; null on a channel that has none
     * @param type the event type on {@code events} and {@code results}; null on a tick or a candle
     * @param data the payload, as the platform wrote it
     * @param base on {@code account:} only: the snapshot's {@code seq} this message applies to
     */
    record Message(String channel, @Nullable Long seq, @Nullable String type, Map<String, JsonNode> data, @Nullable Long base)
            implements StreamEvent {
        /** The message, its payload copied so it cannot be changed. */
        public Message {
            data = Map.copyOf(data);
        }
    }

    /**
     * The requested position is older than the replay window: read current state over REST. Delivered,
     * not thrown: it is the platform being honest, and the remedy is a read, not a retry.
     *
     * @param channel the channel to read afresh
     */
    record Resync(String channel) implements StreamEvent {
    }

    /**
     * The platform refused one channel; every other channel is unaffected. The subscription is dropped, so
     * a reconnect does not ask for it again. Delivered, not thrown.
     *
     * @param channel the refused channel
     * @param code the platform's code, such as {@code not_found} or {@code forbidden}
     * @param message the platform's sentence
     * @param details what the platform said about it
     */
    record ChannelRefused(String channel, String code, String message, Map<String, JsonNode> details) implements StreamEvent {
        /** The refusal, its details copied so they cannot be changed. */
        public ChannelRefused {
            details = Map.copyOf(details);
        }
    }
}
