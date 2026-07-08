package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.HotPathSingleFlight;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.function.Supplier;

@Repository
@ConditionalOnMissingBean(HotPathSingleFlight.class)
public class NoopHotPathSingleFlight implements HotPathSingleFlight {

    @Override
    public <T> T execute(String scope, String key, Duration ttl, Supplier<T> loader, Supplier<T> fallbackWhenBusy) {
        return loader == null ? null : loader.get();
    }
}
