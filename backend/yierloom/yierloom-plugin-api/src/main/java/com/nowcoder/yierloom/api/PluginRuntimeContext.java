package com.nowcoder.yierloom.api;

import java.time.Clock;

public interface PluginRuntimeContext {
    PluginConfig config();

    ManagedScheduler scheduler();

    ObservationChannel observations();

    EventSink events();

    System.Logger logger();

    Clock clock();
}
