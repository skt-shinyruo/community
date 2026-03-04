package com.nowcoder.community.infra.idempotency.autoconfig;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyAutoConfigurationBytecodeDependencyTest {

    @Test
    void baseAutoConfigShouldNotReferenceOptionalJdbcRedisOrJacksonTypes() throws Exception {
        byte[] bytes = readClassBytes(IdempotencyAutoConfiguration.class);

        // Use ISO_8859_1 so every byte maps to a char 1:1 (avoids decoding failures).
        String classFile = new String(bytes, StandardCharsets.ISO_8859_1);

        assertThat(classFile).doesNotContain("org/springframework/jdbc/core/JdbcTemplate");
        assertThat(classFile).doesNotContain("org/springframework/data/redis/core/StringRedisTemplate");
        assertThat(classFile).doesNotContain("com/fasterxml/jackson/databind/ObjectMapper");
    }

    private static byte[] readClassBytes(Class<?> type) throws Exception {
        String resourceName = type.getName().replace('.', '/') + ".class";
        ClassLoader classLoader = type.getClassLoader();
        try (InputStream in = classLoader.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("class bytes not found: " + resourceName);
            }
            return in.readAllBytes();
        }
    }
}

