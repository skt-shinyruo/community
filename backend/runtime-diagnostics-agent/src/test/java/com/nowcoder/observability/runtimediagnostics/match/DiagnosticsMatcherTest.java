package com.nowcoder.observability.runtimediagnostics.match;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticsMatcherTest {

    @Test
    void excludesAgentPackageAndJdkPackages() {
        DiagnosticsMatcher matcher = new DiagnosticsMatcher(config(List.of("*"), List.of()));

        assertThat(matcher.shouldInstrumentClass("com.nowcoder.observability.runtimediagnostics.core.DiagnosticRuntime"))
                .isFalse();
        assertThat(matcher.shouldInstrumentClass("java.lang.String")).isFalse();
        assertThat(matcher.shouldInstrumentClass("com.example.Service")).isTrue();
    }

    @Test
    void userExcludesOverrideIncludes() {
        DiagnosticsMatcher matcher = new DiagnosticsMatcher(config(List.of("com.example.*"), List.of("com.example.internal.*")));

        assertThat(matcher.shouldInstrumentClass("com.example.Service")).isTrue();
        assertThat(matcher.shouldInstrumentClass("com.example.internal.SecretService")).isFalse();
    }

    private static DiagnosticsConfig config(List<String> includes, List<String> excludes) {
        return new DiagnosticsConfig(true, List.of("method"), includes, excludes, 1.0, 20,
                Duration.ofSeconds(60), 50, 10_000, 100, Duration.ofSeconds(60), Duration.ofSeconds(60),
                500, 200, 100, 500,
                1.0, 1.0, 1.0, 1.0,
                20, 20, 20, 20,
                false);
    }
}
