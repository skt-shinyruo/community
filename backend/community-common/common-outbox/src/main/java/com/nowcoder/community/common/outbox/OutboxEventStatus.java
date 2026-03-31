package com.nowcoder.community.common.outbox;

/**
 * Outbox event status values stored in {@code community.outbox_event.status}.
 */
public final class OutboxEventStatus {

    private OutboxEventStatus() {
    }

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String DEAD = "DEAD";
}
