package com.nowcoder.yierloom.plugins.redis;

import com.nowcoder.yierloom.sdk.AdviceTransformers;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public final class RedisTypeInstrumentation implements TypeInstrumentation {
    private static final ElementMatcher.Junction<MethodDescription> METHODS =
            named("execute")
                    .or(named("executePipelined"))
                    .or(named("executeWithStickyConnection"));

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
        return named("org.springframework.data.redis.core.RedisTemplate");
    }

    @Override
    public AgentBuilder.Transformer transformer() {
        return AdviceTransformers.forAdvice(RedisTemplateAdvice.class, METHODS);
    }
}
