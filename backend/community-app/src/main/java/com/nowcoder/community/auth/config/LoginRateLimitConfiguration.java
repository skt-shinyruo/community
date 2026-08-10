package com.nowcoder.community.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class LoginRateLimitConfiguration {

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    @Bean(name = "loginRateLimitLeaseRenewer", destroyMethod = "shutdownNow")
    ScheduledExecutorService loginRateLimitLeaseRenewer() {
        ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(
                2,
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "auth-login-lease-renewer-" + THREAD_SEQUENCE.incrementAndGet()
                    );
                    thread.setDaemon(true);
                    return thread;
                }
        );
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
}
