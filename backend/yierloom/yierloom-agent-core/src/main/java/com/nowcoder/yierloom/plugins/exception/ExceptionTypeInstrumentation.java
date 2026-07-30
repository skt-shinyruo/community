package com.nowcoder.yierloom.plugins.exception;

import java.util.Objects;

import com.nowcoder.yierloom.plugins.support.GlobClassMatcher;
import com.nowcoder.yierloom.sdk.AdviceTransformers;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public final class ExceptionTypeInstrumentation implements TypeInstrumentation {
    private static final ElementMatcher.Junction<MethodDescription> METHOD_MATCHER =
            ElementMatchers.isMethod()
                    .and(ElementMatchers.not(ElementMatchers.isConstructor()))
                    .and(ElementMatchers.not(ElementMatchers.isTypeInitializer()))
                    .and(ElementMatchers.not(ElementMatchers.isAbstract()))
                    .and(ElementMatchers.not(ElementMatchers.isNative()))
                    .and(ElementMatchers.not(ElementMatchers.isBridge()))
                    .and(ElementMatchers.not(ElementMatchers.isSynthetic()));

    private final GlobClassMatcher typeMatcher;

    public ExceptionTypeInstrumentation(GlobClassMatcher typeMatcher) {
        this.typeMatcher = Objects.requireNonNull(typeMatcher);
    }

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
        return typeMatcher;
    }

    @Override
    public AgentBuilder.Transformer transformer() {
        return AdviceTransformers.forAdvice(ExceptionAdvice.class, METHOD_MATCHER);
    }
}
