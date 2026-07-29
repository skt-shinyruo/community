package com.nowcoder.yierloom.sdk;

import java.util.List;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

public final class AdviceTransformers {
    private AdviceTransformers() {
    }

    public static AdviceTransformer forAdvice(
            Class<?> adviceClass,
            ElementMatcher<? super MethodDescription> methodMatcher
    ) {
        return new AdviceTransformer(adviceClass, methodMatcher, List.of());
    }

    public static AdviceTransformer forAdvice(
            Class<?> adviceClass,
            ElementMatcher<? super MethodDescription> methodMatcher,
            AdviceBinding... bindings
    ) {
        return new AdviceTransformer(adviceClass, methodMatcher, List.of(bindings));
    }
}
