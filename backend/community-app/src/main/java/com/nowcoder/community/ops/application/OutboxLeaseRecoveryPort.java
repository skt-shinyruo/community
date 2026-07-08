package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.result.OutboxLeaseRecoveryResult;

public interface OutboxLeaseRecoveryPort {

    OutboxLeaseRecoveryResult recoverExpiredLeases(int limit);
}
