package com.nowcoder.community.infra.outbox;

/**
 * Handles outbox events for a single {@code topic}.
 *
 * <p>Handlers should be idempotent (at-least-once delivery).</p>
 */
public interface OutboxHandler {

    String topic();

    void handle(OutboxEvent event);
}

