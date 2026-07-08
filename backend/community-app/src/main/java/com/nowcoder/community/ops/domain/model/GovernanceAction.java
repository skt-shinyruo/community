package com.nowcoder.community.ops.domain.model;

public enum GovernanceAction {
    OUTBOX_REPLAY_SINGLE,
    OUTBOX_REPLAY_BATCH,
    COMPENSATION_TRIGGER,
    HOT_CACHE_STATUS,
    HOT_CACHE_PREWARM,
    HOT_CACHE_DEGRADATION_SIGNAL
}
