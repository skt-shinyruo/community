package com.nowcoder.yierloom.plugins.kafka;

import com.nowcoder.yierloom.sdk.AdviceBinding;
import com.nowcoder.yierloom.sdk.AdviceTransformers;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public final class KafkaTypeInstrumentation implements TypeInstrumentation {
    private final boolean topicNamesEnabled;

    public KafkaTypeInstrumentation(boolean topicNamesEnabled) {
        this.topicNamesEnabled = topicNamesEnabled;
    }

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
        return named("org.springframework.kafka.core.KafkaTemplate");
    }

    @Override
    public AgentBuilder.Transformer transformer() {
        return AdviceTransformers.forAdvice(
                KafkaTemplateAdvice.class,
                named("send"),
                new AdviceBinding(KafkaTopicNamesEnabled.class, topicNamesEnabled));
    }
}
