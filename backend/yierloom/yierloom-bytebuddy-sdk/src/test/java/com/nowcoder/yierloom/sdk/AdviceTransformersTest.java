package com.nowcoder.yierloom.sdk;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Map;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdviceTransformersTest {

    @Test
    void appliesBoundAdviceWithoutMakingAdviceTypesVisibleToTheTargetLoader() throws Exception {
        AdviceBinding binding = new AdviceBinding(BoundFlag.class, true);
        AdviceTransformer transformer = AdviceTransformers.forAdvice(
                SampleAdvice.class,
                named("value"),
                binding);

        DynamicType.Builder<?> transformedBuilder = transformer.transform(
                new ByteBuddy().redefine(SampleTarget.class),
                TypeDescription.ForLoadedType.of(SampleTarget.class),
                SampleTarget.class.getClassLoader(),
                JavaModule.ofType(SampleTarget.class),
                SampleTarget.class.getProtectionDomain());
        byte[] transformedBytes = transformedBuilder.make().getBytes();
        ClassLoader targetLoader = new ByteArrayClassLoader.ChildFirst(
                ClassLoader.getPlatformClassLoader(),
                Map.of(SampleTarget.class.getName(), transformedBytes));

        Class<?> targetType = Class.forName(SampleTarget.class.getName(), true, targetLoader);
        Object target = targetType.getDeclaredConstructor().newInstance();
        Method value = targetType.getDeclaredMethod("value");

        assertThat(value.invoke(target)).isEqualTo(42);
        assertThat(transformer.adviceClass()).isSameAs(SampleAdvice.class);
        assertThat(transformer.bindings()).containsExactly(binding);
        assertThatThrownBy(() -> Class.forName(SampleAdvice.class.getName(), false, targetLoader))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(BoundFlag.class.getName(), false, targetLoader))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void rejectsBindingValuesThatCannotBeEmbeddedAsConstants() {
        assertThatThrownBy(() -> new AdviceBinding(BoundFlag.class, new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported Advice binding constant");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface BoundFlag {
    }

    static class SampleAdvice {
        @Advice.OnMethodExit
        static void onExit(
                @Advice.Return(readOnly = false) int returned,
                @BoundFlag boolean enabled
        ) {
            if (enabled) {
                returned = 42;
            }
        }
    }

    public static class SampleTarget {
        public int value() {
            return 7;
        }
    }
}
