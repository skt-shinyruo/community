package com.nowcoder.community.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyTraceMdcRetirementTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        MDC.clear();
    }

    @Test
    void traceContextShouldNotExposeLegacyMdcConstant() {
        assertThatThrownBy(() -> TraceContext.class.getDeclaredField("MDC_KEY_" + "LEGACY_TRACE_ID"))
                .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    void traceContextShouldNotMutateForeignLegacyNamedMdcEntry() {
        MDC.put("trace" + "Id", "foreign-value");

        TraceContext.set("11111111111111111111111111111111", "00f067aa0ba902b7");
        TraceContext.clear();

        assertThat(MDC.get("trace" + "Id")).isEqualTo("foreign-value");
    }
}
