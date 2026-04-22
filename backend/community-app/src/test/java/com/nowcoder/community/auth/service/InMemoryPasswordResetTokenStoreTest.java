package com.nowcoder.community.auth.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPasswordResetTokenStoreTest {

    @Test
    void consumeShouldOnlySucceedOnceUnderConcurrentAccess() throws Exception {
        UUID userId = uuid(11);
        InMemoryPasswordResetTokenStore store = new InMemoryPasswordResetTokenStore();
        store.store("reset-token", userId, Duration.ofMinutes(5));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> first = executor.submit(() -> store.consume("reset-token"));
            Future<UUID> second = executor.submit(() -> store.consume("reset-token"));

            List<UUID> results = Arrays.asList(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

            assertThat(results).contains(userId);
            assertThat(results.stream().filter(userId::equals).count()).isEqualTo(1);
            assertThat(results.stream().filter(v -> v == null).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
