package com.nowcoder.observability.methodprofiler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MethodProfilerAgentSmokeTest {

    @Test
    void exposesPremainEntryPoint() throws Exception {
        assertThat(MethodProfilerAgent.class.getMethod("premain", String.class, java.lang.instrument.Instrumentation.class))
                .isNotNull();
    }
}
