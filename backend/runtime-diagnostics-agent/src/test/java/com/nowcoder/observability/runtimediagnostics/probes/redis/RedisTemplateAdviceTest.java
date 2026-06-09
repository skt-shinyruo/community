package com.nowcoder.observability.runtimediagnostics.probes.redis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTemplateAdviceTest {

    @Test
    void commandNameFallsBackToExecute() {
        assertThat(RedisTemplateAdvice.commandName("execute")).isEqualTo("EXECUTE");
    }

    @Test
    void keyHashNeverReturnsRawKey() {
        String hash = RedisTemplateAdvice.hashKeyspace("user:token:secret");

        assertThat(hash).hasSize(16);
        assertThat(hash).doesNotContain("secret");
    }
}
