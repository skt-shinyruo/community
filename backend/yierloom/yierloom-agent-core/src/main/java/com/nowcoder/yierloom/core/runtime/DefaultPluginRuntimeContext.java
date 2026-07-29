package com.nowcoder.yierloom.core.runtime;

import java.time.Clock;
import java.util.Objects;

import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ManagedScheduler;
import com.nowcoder.yierloom.api.ObservationChannel;
import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginRuntimeContext;

public record DefaultPluginRuntimeContext(
        PluginConfig config,
        ManagedScheduler scheduler,
        ObservationChannel observations,
        EventSink events,
        System.Logger logger,
        Clock clock
) implements PluginRuntimeContext {
    public DefaultPluginRuntimeContext {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(observations, "observations");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(clock, "clock");
    }
}
