package com.nowcoder.community.common.outbox;

import org.springframework.util.StringUtils;

/**
 * Signals that an outbox event is permanently obsolete and its payload must be erased.
 */
public final class OutboxTerminalException extends RuntimeException {

    private final String reasonCode;

    public OutboxTerminalException(String reasonCode, String message) {
        super(StringUtils.hasText(message) ? message.trim() : "terminal outbox event");
        if (!StringUtils.hasText(reasonCode)) {
            throw new IllegalArgumentException("reasonCode is required");
        }
        this.reasonCode = reasonCode.trim();
    }

    public String reasonCode() {
        return reasonCode;
    }
}
