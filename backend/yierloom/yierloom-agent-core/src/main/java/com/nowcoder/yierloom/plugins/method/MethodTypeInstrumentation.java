package com.nowcoder.yierloom.plugins.method;

import java.util.Objects;

import com.nowcoder.yierloom.plugins.support.GlobClassMatcher;
import com.nowcoder.yierloom.sdk.AdviceTransformers;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isBridge;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isNative;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.not;

public final class MethodTypeInstrumentation implements TypeInstrumentation {
    private final GlobClassMatcher matcher;

    public MethodTypeInstrumentation(GlobClassMatcher matcher) {
        this.matcher = Objects.requireNonNull(matcher, "matcher");
    }

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
        return matcher;
    }

    @Override
    public AgentBuilder.Transformer transformer() {
        return AdviceTransformers.forAdvice(
                MethodAdvice.class,
                isMethod()
                        .and(not(isConstructor()))
                        .and(not(isTypeInitializer()))
                        .and(not(isAbstract()))
                        .and(not(isNative()))
                        .and(not(isBridge()))
                        .and(not(isSynthetic())));
    }
}
