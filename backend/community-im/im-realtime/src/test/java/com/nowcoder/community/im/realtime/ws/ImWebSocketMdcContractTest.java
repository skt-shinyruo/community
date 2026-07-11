package com.nowcoder.community.im.realtime.ws;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class ImWebSocketMdcContractTest {

    @Test
    void eventLogsShouldUseCanonicalTraceMdcKey() throws ReflectiveOperationException {
        Field field = ImWebSocketHandler.class.getDeclaredField("MDC_TRACE_ID");
        field.setAccessible(true);

        assertThat(field.get(null))
                .isEqualTo("trace.id")
                .isNotEqualTo("traceId");
    }
}
