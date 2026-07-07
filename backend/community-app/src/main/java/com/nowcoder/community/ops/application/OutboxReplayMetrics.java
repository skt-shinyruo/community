package com.nowcoder.community.ops.application;

public interface OutboxReplayMetrics {

    void recordReplay(String topic, String result);
}
