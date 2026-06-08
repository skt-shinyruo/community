package com.nowcoder.observability.runtimediagnostics;

import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDiagnosticsAgentSmokeTest {

    @Test
    void exposesPremainEntryPoint() throws Exception {
        assertThat(RuntimeDiagnosticsAgent.class.getMethod("premain", String.class, Instrumentation.class))
                .isNotNull();
    }
}
