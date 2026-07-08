package com.nowcoder.community.ops.application.result;

public record OutboxLeaseRecoveryResult(
        int selectedCount,
        int recoveredCount
) {
}
