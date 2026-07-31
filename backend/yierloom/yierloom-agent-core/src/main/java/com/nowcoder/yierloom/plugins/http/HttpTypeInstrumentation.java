package com.nowcoder.yierloom.plugins.http;

import com.nowcoder.yierloom.sdk.AdviceTransformers;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

public final class HttpTypeInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
        return hasSuperType(named(
                "org.springframework.web.reactive.function.client.ExchangeFunction"));
    }

    @Override
    public AgentBuilder.Transformer transformer() {
        return AdviceTransformers.forAdvice(HttpExchangeAdvice.class, named("exchange"));
    }
}
