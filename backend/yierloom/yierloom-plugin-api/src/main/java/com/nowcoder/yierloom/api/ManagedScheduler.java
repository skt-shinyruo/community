package com.nowcoder.yierloom.api;

import java.time.Duration;

public interface ManagedScheduler {
    ManagedTask scheduleWithFixedDelay(String taskName, Duration initialDelay, Duration delay, Runnable task);
}
