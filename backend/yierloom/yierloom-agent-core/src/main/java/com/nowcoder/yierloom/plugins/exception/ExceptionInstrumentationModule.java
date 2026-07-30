package com.nowcoder.yierloom.plugins.exception;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.nowcoder.yierloom.plugins.support.GlobClassMatcher;
import com.nowcoder.yierloom.sdk.InstrumentationModule;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;

public final class ExceptionInstrumentationModule implements InstrumentationModule {
    private static final Set<String> HELPERS = Set.of(
            ExceptionObservationHelper.class.getName());

    private final List<TypeInstrumentation> typeInstrumentations;

    public ExceptionInstrumentationModule(GlobClassMatcher typeMatcher) {
        this.typeInstrumentations = List.of(
                new ExceptionTypeInstrumentation(Objects.requireNonNull(typeMatcher)));
    }

    @Override
    public String id() {
        return "exception";
    }

    @Override
    public List<? extends TypeInstrumentation> typeInstrumentations() {
        return typeInstrumentations;
    }

    @Override
    public Set<String> helperClassNames() {
        return HELPERS;
    }
}
