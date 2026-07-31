package com.nowcoder.yierloom.plugins.kafka;

import java.util.List;
import java.util.Set;

import com.nowcoder.yierloom.sdk.InstrumentationModule;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;

public final class KafkaInstrumentationModule implements InstrumentationModule {
    private final List<TypeInstrumentation> typeInstrumentations;

    public KafkaInstrumentationModule(boolean topicNamesEnabled) {
        this.typeInstrumentations = List.of(new KafkaTypeInstrumentation(topicNamesEnabled));
    }

    @Override
    public String id() {
        return "kafka";
    }

    @Override
    public List<? extends TypeInstrumentation> typeInstrumentations() {
        return typeInstrumentations;
    }

    @Override
    public Set<String> helperClassNames() {
        return Set.of(KafkaObservationHelper.class.getName());
    }
}
