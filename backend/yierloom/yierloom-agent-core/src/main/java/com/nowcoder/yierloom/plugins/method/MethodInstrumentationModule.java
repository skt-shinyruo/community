package com.nowcoder.yierloom.plugins.method;

import java.util.List;
import java.util.Set;

import com.nowcoder.yierloom.plugins.support.GlobClassMatcher;
import com.nowcoder.yierloom.sdk.InstrumentationModule;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;

public final class MethodInstrumentationModule implements InstrumentationModule {
    private final List<TypeInstrumentation> typeInstrumentations;

    public MethodInstrumentationModule(GlobClassMatcher matcher) {
        this.typeInstrumentations = List.of(new MethodTypeInstrumentation(matcher));
    }

    @Override
    public String id() {
        return "method";
    }

    @Override
    public List<? extends TypeInstrumentation> typeInstrumentations() {
        return typeInstrumentations;
    }

    @Override
    public Set<String> helperClassNames() {
        return Set.of(MethodObservationHelper.class.getName());
    }
}
