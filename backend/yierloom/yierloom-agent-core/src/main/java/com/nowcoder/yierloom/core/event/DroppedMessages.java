package com.nowcoder.yierloom.core.event;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

public final class DroppedMessages {
    private final ConcurrentMap<String, LongAdder> observations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> events = new ConcurrentHashMap<>();

    public long observations(String pluginId) {
        return count(observations, pluginId);
    }

    public long events(String pluginId) {
        return count(events, pluginId);
    }

    void increment(PipelineMessage message) {
        if (message instanceof PipelineMessage.Observation) {
            observations.computeIfAbsent(message.pluginId(), ignored -> new LongAdder()).increment();
        } else {
            events.computeIfAbsent(message.pluginId(), ignored -> new LongAdder()).increment();
        }
    }

    private static long count(ConcurrentMap<String, LongAdder> counters, String pluginId) {
        LongAdder counter = counters.get(pluginId);
        return counter == null ? 0 : counter.sum();
    }
}
