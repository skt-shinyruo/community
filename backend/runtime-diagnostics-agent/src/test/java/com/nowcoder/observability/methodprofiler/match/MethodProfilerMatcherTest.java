package com.nowcoder.observability.methodprofiler.match;

import com.nowcoder.observability.methodprofiler.config.ProfilerConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MethodProfilerMatcherTest {

    @Test
    void includeStarMeansEligibleAfterHardExcludes() {
        MethodProfilerMatcher matcher = new MethodProfilerMatcher(config(List.of("*"), List.of()));

        assertThat(matcher.shouldInstrumentClass("com.example.Service")).isTrue();
        assertThat(matcher.shouldInstrumentClass("java.lang.String")).isFalse();
        assertThat(matcher.shouldInstrumentClass("org.slf4j.Logger")).isFalse();
        assertThat(matcher.shouldInstrumentClass("ch.qos.logback.classic.Logger")).isFalse();
        assertThat(matcher.shouldInstrumentClass("net.bytebuddy.agent.builder.AgentBuilder")).isFalse();
        assertThat(matcher.shouldInstrumentClass("com.nowcoder.observability.methodprofiler.MethodProfilerAgent")).isFalse();
    }

    @Test
    void userExcludesCannotRemoveHardExcludes() {
        MethodProfilerMatcher matcher = new MethodProfilerMatcher(config(List.of("java.*", "com.example.*"), List.of()));

        assertThat(matcher.shouldInstrumentClass("java.util.ArrayList")).isFalse();
        assertThat(matcher.shouldInstrumentClass("com.example.Service")).isTrue();
    }

    @Test
    void userExcludesBlockIncludedClasses() {
        MethodProfilerMatcher matcher = new MethodProfilerMatcher(config(
                List.of("com.example.*"),
                List.of("com.example.internal.*")
        ));

        assertThat(matcher.shouldInstrumentClass("com.example.Service")).isTrue();
        assertThat(matcher.shouldInstrumentClass("com.example.internal.SecretService")).isFalse();
    }

    @Test
    void skipsUnsafeMethods() throws NoSuchMethodException {
        MethodProfilerMatcher matcher = new MethodProfilerMatcher(config(List.of("com.example.*"), List.of()));

        assertThat(matcher.shouldInstrumentMethod(Sample.class.getDeclaredMethod("normal"))).isTrue();
        assertThat(matcher.shouldInstrumentMethod(Sample.class.getDeclaredMethod("nativeMethod"))).isFalse();
        assertThat(matcher.shouldInstrumentMethod(AbstractSample.class.getDeclaredMethod("abstractMethod"))).isFalse();
    }

    private static ProfilerConfig config(List<String> includes, List<String> excludes) {
        return new ProfilerConfig(false, includes, excludes, 100, Duration.ofSeconds(60), 50, 1.0, 20, 10_000);
    }

    static class Sample {
        void normal() {
        }

        native void nativeMethod();
    }

    abstract static class AbstractSample {
        abstract void abstractMethod();
    }
}
