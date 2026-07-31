package com.nowcoder.yierloom.plugins.http;

import java.util.List;
import java.util.Set;

import com.nowcoder.yierloom.sdk.InstrumentationModule;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;

public final class HttpInstrumentationModule implements InstrumentationModule {
    @Override
    public String id() {
        return "http";
    }

    @Override
    public List<? extends TypeInstrumentation> typeInstrumentations() {
        return List.of(new HttpTypeInstrumentation());
    }

    @Override
    public Set<String> helperClassNames() {
        return Set.of(HttpObservationHelper.class.getName());
    }
}
