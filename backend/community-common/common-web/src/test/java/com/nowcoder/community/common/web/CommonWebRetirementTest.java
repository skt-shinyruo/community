package com.nowcoder.community.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonWebRetirementTest {

    @Test
    void retiredTraceIdClientInterceptorShouldStayAbsent() {
        assertThatThrownBy(() -> Class.forName(cn(
                        "com.nowcoder.community.common.web.",
                        "TraceIdClientHttpRequest",
                        "Interceptor"
                )))
                .isInstanceOf(ClassNotFoundException.class);
    }

    private String cn(String... parts) {
        return String.join("", parts);
    }
}
