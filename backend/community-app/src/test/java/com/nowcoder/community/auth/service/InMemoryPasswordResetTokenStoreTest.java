package com.nowcoder.community.auth.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPasswordResetTokenStoreTest {

    @Test
    void consumeShouldOnlySucceedOnceUnderConcurrentAccess() throws Exception {
        InMemoryPasswordResetTokenStore store = new InMemoryPasswordResetTokenStore();
        store.store("reset-token", 11, Duration.ofMinutes(5));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> store.consume("reset-token"));
            Future<Integer> second = executor.submit(() -> store.consume("reset-token"));

            List<Integer> results = Arrays.asList(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

            assertThat(results).contains(11);
            assertThat(results.stream().filter(v -> v != null && v == 11).count()).isEqualTo(1);
            assertThat(results.stream().filter(v -> v == null).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
