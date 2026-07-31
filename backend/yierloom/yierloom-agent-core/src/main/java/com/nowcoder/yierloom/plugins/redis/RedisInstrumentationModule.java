package com.nowcoder.yierloom.plugins.redis;

import java.util.List;
import java.util.Set;

import com.nowcoder.yierloom.sdk.InstrumentationModule;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;

public final class RedisInstrumentationModule implements InstrumentationModule {
    @Override
    public String id() {
        return "redis";
    }

    @Override
    public List<? extends TypeInstrumentation> typeInstrumentations() {
        return List.of(new RedisTypeInstrumentation());
    }

    @Override
    public Set<String> helperClassNames() {
        return Set.of(RedisObservationHelper.class.getName());
    }
}
