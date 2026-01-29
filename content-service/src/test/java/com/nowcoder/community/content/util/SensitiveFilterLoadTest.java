package com.nowcoder.community.content.util;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveFilterLoadTest {

    @Test
    void sensitiveWordsResourceShouldExistAndBeReadable() throws Exception {
        ClassPathResource res = new ClassPathResource("sensitive-words.txt");
        assertThat(res.exists()).isTrue();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            String first = reader.readLine();
            assertThat(first).isNotNull();
        }
    }
}

