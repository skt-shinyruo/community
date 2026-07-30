package com.nowcoder.yierloom.core.instrumentation;

import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.nowcoder.yierloom.core.instrumentation.fixture.InjectedHelper;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassInjector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelperInjectorTest {
    private static final String BASE_NAME =
            "com.nowcoder.yierloom.core.instrumentation.fixture.InjectedHelperBase";

    @Test
    void readsExactHelperBytesInDependencyOrderAndInjectsOncePerLoader() {
        RecordingInjectionStrategy strategy = new RecordingInjectionStrategy();
        HelperInjector injector = new HelperInjector(strategy);
        Map<String, byte[]> helpers = HelperClassBytes.read(
                InjectedHelper.class.getClassLoader(),
                Set.of(InjectedHelper.class.getName(), BASE_NAME));
        ClassLoader targetLoader = new ClassLoader(null) { };

        injector.inject(targetLoader, protectionDomain(), helpers);
        injector.inject(targetLoader, protectionDomain(), helpers);

        assertThat(strategy.resolveCalls()).hasValue(1);
        assertThat(strategy.injectedBatches()).singleElement()
                .satisfies(batch -> assertThat(batch)
                        .containsExactly(BASE_NAME, InjectedHelper.class.getName()));
    }

    @Test
    void usesWeakIdentityRatherThanClassLoaderEquality() {
        RecordingInjectionStrategy strategy = new RecordingInjectionStrategy();
        HelperInjector injector = new HelperInjector(strategy);
        Map<String, byte[]> helper = Map.of("fixture.Helper", new byte[]{1});
        ClassLoader first = new EqualClassLoader();
        ClassLoader second = new EqualClassLoader();

        injector.inject(first, protectionDomain(), helper);
        injector.inject(second, protectionDomain(), helper);

        assertThat(first).isEqualTo(second);
        assertThat(strategy.resolveCalls()).hasValue(2);
    }

    @Test
    void injectsBootstrapHelpersOnlyOnce() {
        RecordingInjectionStrategy strategy = new RecordingInjectionStrategy();
        HelperInjector injector = new HelperInjector(strategy);
        Map<String, byte[]> helper = Map.of("fixture.BootstrapHelper", new byte[]{1});

        injector.inject(null, protectionDomain(), helper);
        injector.inject(null, protectionDomain(), helper);

        assertThat(strategy.resolveCalls()).hasValue(1);
    }

    @Test
    void rejectsAHelperNameAlreadyVisibleToTheTargetLoader() {
        RecordingInjectionStrategy strategy = new RecordingInjectionStrategy();
        HelperInjector injector = new HelperInjector(strategy);
        Map<String, byte[]> helpers = HelperClassBytes.read(
                InjectedHelper.class.getClassLoader(),
                Set.of(InjectedHelper.class.getName()));

        assertThatThrownBy(() -> injector.inject(
                InjectedHelper.class.getClassLoader(), protectionDomain(), helpers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(InjectedHelper.class.getName());
        assertThat(strategy.resolveCalls()).hasValue(0);
    }

    @Test
    void reusesAnExactHelperPreviouslyInjectedIntoAParentLoader() throws Exception {
        String helperName = "fixture.ParentInjectedHelper";
        byte[] helperBytes = new ByteBuddy()
                .subclass(Object.class)
                .name(helperName)
                .make()
                .getBytes();
        DefiningClassLoader parent = new DefiningClassLoader(null);
        ClassLoader child = new ClassLoader(parent) { };
        AtomicInteger resolveCalls = new AtomicInteger();
        HelperInjector injector = new HelperInjector((loader, domain) -> new ClassInjector() {
            @Override
            public boolean isAlive() {
                return true;
            }

            @Override
            public Map<TypeDescription, Class<?>> inject(
                    Map<? extends TypeDescription, byte[]> types
            ) {
                return Map.of();
            }

            @Override
            public Map<String, Class<?>> injectRaw(Map<? extends String, byte[]> types) {
                resolveCalls.incrementAndGet();
                Map<String, Class<?>> injected = new LinkedHashMap<>();
                types.forEach((name, bytes) -> injected.put(
                        name, ((DefiningClassLoader) loader).define(name, bytes)));
                return injected;
            }
        });

        injector.inject(parent, protectionDomain(), Map.of(helperName, helperBytes));
        injector.inject(child, protectionDomain(), Map.of(helperName, helperBytes));

        assertThat(resolveCalls).hasValue(1);
        assertThat(Class.forName(helperName, false, child).getClassLoader()).isSameAs(parent);
    }

    private static ProtectionDomain protectionDomain() {
        return HelperInjectorTest.class.getProtectionDomain();
    }

    private static final class EqualClassLoader extends ClassLoader {
        private EqualClassLoader() {
            super(null);
        }

        @Override
        public boolean equals(Object ignored) {
            return true;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static final class DefiningClassLoader extends ClassLoader {
        private DefiningClassLoader(ClassLoader parent) {
            super(parent);
        }

        private Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static final class RecordingInjectionStrategy implements AgentBuilder.InjectionStrategy {
        private final AtomicInteger resolveCalls = new AtomicInteger();
        private final List<List<String>> injectedBatches = new ArrayList<>();

        @Override
        public ClassInjector resolve(ClassLoader loader, ProtectionDomain protectionDomain) {
            resolveCalls.incrementAndGet();
            return new ClassInjector() {
                @Override
                public boolean isAlive() {
                    return true;
                }

                @Override
                public Map<TypeDescription, Class<?>> inject(
                        Map<? extends TypeDescription, byte[]> types
                ) {
                    return Map.of();
                }

                @Override
                public Map<String, Class<?>> injectRaw(Map<? extends String, byte[]> types) {
                    injectedBatches.add(new ArrayList<>(new LinkedHashMap<>(types).keySet()));
                    return Map.of();
                }
            };
        }

        private AtomicInteger resolveCalls() {
            return resolveCalls;
        }

        private List<List<String>> injectedBatches() {
            return injectedBatches;
        }
    }
}
