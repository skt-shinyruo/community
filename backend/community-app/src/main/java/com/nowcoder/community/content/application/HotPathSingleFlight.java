package com.nowcoder.community.content.application;

import java.time.Duration;
import java.util.function.Supplier;

public interface HotPathSingleFlight {

    <T> T execute(String scope, String key, Duration ttl, Supplier<T> loader, Supplier<T> fallbackWhenBusy);
}
