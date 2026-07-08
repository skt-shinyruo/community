package com.nowcoder.community.ops.application;

public interface GovernanceMetrics extends OutboxReplayMetrics {

    void recordOutboxBatchReplay(String topic, String result, long count);

    void recordGovernanceAction(String action, String result);

    void recordHotCacheGovernance(String operation, String result, String scope);

    void recordCompensationTrigger(String jobName, String result);
}
