package com.nowcoder.observability.runtimediagnostics.probes.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExchangeAdviceTest {

    @Test
    void routeSanitizationDropsQueryString() {
        assertThat(HttpExchangeAdvice.sanitizeRoute("https://example.com/api/users?id=123&token=secret"))
                .isEqualTo("/api/users");
    }

    @Test
    void hostIsHashedAndNotReturnedRaw() {
        String hash = HttpExchangeAdvice.hashHost("internal-service.local");

        assertThat(hash).hasSize(16);
        assertThat(hash).doesNotContain("internal-service");
    }
}
