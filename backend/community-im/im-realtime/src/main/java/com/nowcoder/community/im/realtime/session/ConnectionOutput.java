package com.nowcoder.community.im.realtime.session;

import java.time.Duration;

/**
 * Outbound side of one realtime connection: send a text frame, complete the stream or
 * close the connection. The production adapter is backed by the WebSocket session and
 * its Reactor sink; tests use an in-memory adapter. Neither transport primitive leaks
 * through this interface.
 */
public interface ConnectionOutput {

    /**
     * Offers one text frame to the outbound stream. Returns {@code false} when the
     * frame could not be accepted (stream completed or backlog overflow); an overflow
     * also initiates connection close, mirroring the production backpressure policy.
     */
    boolean trySendText(String text);

    /** Completes the outbound stream; further sends are rejected. */
    void complete();

    /** Closes the underlying connection asynchronously, swallowing close failures. */
    void closeAsync(Duration timeout);

    /** Frames accepted but not yet delivered to the socket. */
    int outboundBacklog();
}
