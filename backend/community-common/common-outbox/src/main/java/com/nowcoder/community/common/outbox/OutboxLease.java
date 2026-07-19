package com.nowcoder.community.common.outbox;

import java.util.Objects;
import java.util.UUID;

public record OutboxLease(UUID rowId, UUID token) {

    public OutboxLease {
        Objects.requireNonNull(rowId, "rowId");
        Objects.requireNonNull(token, "token");
    }
}
