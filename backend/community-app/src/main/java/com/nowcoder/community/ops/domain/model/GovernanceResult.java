package com.nowcoder.community.ops.domain.model;

public enum GovernanceResult {
    ACCEPTED,
    REPLAYED,
    PARTIAL,
    REJECTED,
    NOT_REQUEUED,
    FAILED,
    DEGRADED,
    SKIPPED
}
