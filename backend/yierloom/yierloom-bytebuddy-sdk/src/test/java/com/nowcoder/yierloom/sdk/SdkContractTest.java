package com.nowcoder.yierloom.sdk;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;

class SdkContractTest {

    @Test
    void publicSdkDoesNotExposeRawInstrumentation() {
        Set<Class<?>> exposed = Stream.of(
                        InstrumentationCapability.class,
                        InstrumentationModule.class,
                        TypeInstrumentation.class)
                .flatMap(type -> Stream.of(type.getMethods()))
                .flatMap(method -> Stream.concat(
                        Stream.of(method.getReturnType()),
                        Stream.of(method.getParameterTypes())))
                .collect(Collectors.toSet());

        assertThat(exposed).doesNotContain(java.lang.instrument.Instrumentation.class);
    }

    @Test
    void defaultsMatchEveryClassLoaderAndNoHelpers() {
        TypeInstrumentation type = new RecordingTypeInstrumentation();
        InstrumentationModule module = new RecordingModule(type);

        assertThat(type.classLoaderMatcher().matches(getClass().getClassLoader())).isTrue();
        assertThat(module.helperClassNames()).isEmpty();
    }

    private static final class RecordingTypeInstrumentation implements TypeInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> typeMatcher() {
            return any();
        }

        @Override
        public AgentBuilder.Transformer transformer() {
            return (builder, typeDescription, classLoader, module, protectionDomain) -> builder;
        }
    }

    private record RecordingModule(TypeInstrumentation type) implements InstrumentationModule {
        @Override
        public String id() {
            return "recording";
        }

        @Override
        public List<? extends TypeInstrumentation> typeInstrumentations() {
            return List.of(type);
        }
    }
}
