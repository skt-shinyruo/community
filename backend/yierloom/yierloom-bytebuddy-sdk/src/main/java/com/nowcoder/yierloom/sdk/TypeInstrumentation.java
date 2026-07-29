package com.nowcoder.yierloom.sdk;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public interface TypeInstrumentation {
    ElementMatcher<? super TypeDescription> typeMatcher();

    default ElementMatcher<? super ClassLoader> classLoaderMatcher() {
        return ElementMatchers.any();
    }

    AgentBuilder.Transformer transformer();
}
