package com.nowcoder.community.common.observability.app;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

import java.time.Duration;
import java.util.function.LongSupplier;

public class RuntimeApplicationLifecycleListener implements ApplicationListener<ApplicationEvent>, DisposableBean {

    private final ApplicationLifecycleRuntimeLogger logger;
    private final Duration gracefulShutdownTimeout;
    private final LongSupplier nanoTime;
    private long contextClosedAtNanos = -1;
    private boolean shutdownLogged;

    public RuntimeApplicationLifecycleListener(
            ApplicationLifecycleRuntimeLogger logger,
            Duration gracefulShutdownTimeout
    ) {
        this(logger, gracefulShutdownTimeout, System::nanoTime);
    }

    RuntimeApplicationLifecycleListener(
            ApplicationLifecycleRuntimeLogger logger,
            Duration gracefulShutdownTimeout,
            LongSupplier nanoTime
    ) {
        this.logger = logger;
        this.gracefulShutdownTimeout = gracefulShutdownTimeout == null ? Duration.ZERO : gracefulShutdownTimeout;
        this.nanoTime = nanoTime;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationStartedEvent startedEvent) {
            logger.logStartup(startedEvent.getTimeTaken());
        } else if (event instanceof ApplicationReadyEvent readyEvent) {
            logger.logReady(readyEvent.getTimeTaken());
        } else if (event instanceof ContextClosedEvent) {
            contextClosedAtNanos = nanoTime.getAsLong();
        }
    }

    @Override
    public void destroy() {
        if (shutdownLogged) {
            return;
        }
        shutdownLogged = true;
        Duration shutdownDuration = Duration.ZERO;
        if (contextClosedAtNanos >= 0) {
            shutdownDuration = Duration.ofNanos(Math.max(0, nanoTime.getAsLong() - contextClosedAtNanos));
        }
        logger.logShutdown(shutdownDuration);
        if (!gracefulShutdownTimeout.isZero()
                && !gracefulShutdownTimeout.isNegative()
                && shutdownDuration.compareTo(gracefulShutdownTimeout) >= 0) {
            logger.logGracefulShutdownTimeout(gracefulShutdownTimeout);
        }
    }
}
